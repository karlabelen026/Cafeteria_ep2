#!/usr/bin/env bash
# Levanta RabbitMQ por etapas, como se pide en la EP2: primero se comprueba
# que la mensajeria funciona con UN solo nodo y recien despues se agregan los
# demas nodos al cluster (guia 2.3.1 "RabbitMQ Cluster con Docker Compose").
#
# Uso (parado en la raiz del repo):
#   ./scripts/rabbitmq-cluster.sh un-nodo     # 1) solo rabbitmq-1 + la aplicacion
#   ./scripts/rabbitmq-cluster.sh agregar     # 2) suma rabbitmq-2 y rabbitmq-3 al cluster
#   ./scripts/rabbitmq-cluster.sh estado      # nodos del cluster y miembros de cada cola
#
# El dashboard de RabbitMQ queda en http://localhost:15672 (nodo 1),
# :15673 (nodo 2) y :15674 (nodo 3), con el usuario y la clave definidos en
# docker-compose.yml (RABBITMQ_USER / RABBITMQ_PASSWORD de tu .env).
set -euo pipefail
cd "$(dirname "$0")/.."

APLICACION="ms-productos ms-inventario ms-pedidos ms-clientes ms-pagos ms-empleados ms-proveedores ms-reportes ms-notificaciones ms-rabbitmq-admin bff-gateway frontend"
NODOS_EXTRA="rabbitmq-2 rabbitmq-3"

# Los comandos se ejecutan como el usuario "rabbitmq" del contenedor (no como
# root) para no tocar los permisos de la cookie de Erlang del nodo.
rabbit() {
    local nodo="$1"
    shift
    docker exec -u rabbitmq "$nodo" "$@"
}

estado() {
    echo "== Nodos activos del cluster =="
    rabbit rabbitmq-1 rabbitmqctl -q cluster_status | sed -n '/Running Nodes/,/Versions/p' | grep 'rabbit@'
    echo
    echo "== Colas (tipo, mensajes y nodos donde vive cada una) =="
    rabbit rabbitmq-1 rabbitmqctl -q list_queues name type messages members
}

case "${1:-}" in
    un-nodo)
        # depends_on de los microservicios solo exige rabbitmq-1: los nodos 2 y
        # 3 no se crean aqui. RABBITMQ_ADDRESSES lista los 3, pero el cliente
        # AMQP simplemente usa el que responde.
        # --wait: espera a que cada contenedor quede "healthy" antes de seguir.
        docker compose up -d --build --wait mysql rabbitmq-1
        docker compose up -d --build $APLICACION
        estado
        echo
        echo "Listo: la aplicacion esta funcionando con UN solo nodo de RabbitMQ."
        echo "Prueba un pedido en http://localhost:4200 y mira la actividad en http://localhost:15672"
        ;;
    agregar)
        # Cada nodo nuevo arranca con su volumen vacio, lee rabbitmq/rabbitmq.conf
        # (peer discovery classic_config) y se une solo al cluster de rabbitmq-1.
        docker compose up -d --wait $NODOS_EXTRA
        for nodo in $NODOS_EXTRA; do
            rabbit "$nodo" rabbitmqctl await_startup >/dev/null
            # Las colas quorum creadas cuando solo existia el nodo 1 tienen una
            # unica replica: "grow" agrega una replica de cada cola en el nodo nuevo.
            rabbit rabbitmq-1 rabbitmq-queues grow "rabbit@$nodo" all
        done
        estado
        echo
        echo "Listo: cluster de 3 nodos, con cada cola replicada en los 3."
        ;;
    estado)
        estado
        ;;
    *)
        sed -n '2,13p' "$0"
        exit 1
        ;;
esac
