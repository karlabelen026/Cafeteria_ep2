#!/usr/bin/env bash
# Administracion de Kafka por linea de comandos, con las herramientas del
# broker (lo mismo que expone ms-kafka-admin por REST).
#
# Uso (parado en la raiz del repo, con el stack arriba):
#   ./scripts/kafka-topics.sh listar
#   ./scripts/kafka-topics.sh describir <topic>
#   ./scripts/kafka-topics.sh crear <topic> [particiones=3] [replicas=3]
#   ./scripts/kafka-topics.sh config <topic> <clave=valor>      # p.ej. retention.ms=86400000
#   ./scripts/kafka-topics.sh particiones <topic> <total>
#   ./scripts/kafka-topics.sh eliminar <topic>
#   ./scripts/kafka-topics.sh grupos
#   ./scripts/kafka-topics.sh grupo <group-id>                  # miembros, offsets y lag
set -euo pipefail

BOOTSTRAP="kafka-1:29092,kafka-2:29092,kafka-3:29092"

# Heap propio: estas herramientas corren dentro del contenedor del broker.
kafka() {
    docker exec -e KAFKA_HEAP_OPTS=-Xmx128m kafka-1 "$@" --bootstrap-server "$BOOTSTRAP"
}

accion="${1:-}"
[ $# -gt 0 ] && shift

case "$accion" in
    listar)      kafka kafka-topics --list ;;
    describir)   kafka kafka-topics --describe --topic "$1" ;;
    crear)       kafka kafka-topics --create --topic "$1" --partitions "${2:-3}" --replication-factor "${3:-3}" \
                     --config min.insync.replicas=2 --config cleanup.policy=delete ;;
    config)      kafka kafka-configs --alter --entity-type topics --entity-name "$1" --add-config "$2" ;;
    particiones) kafka kafka-topics --alter --topic "$1" --partitions "$2" ;;
    eliminar)    kafka kafka-topics --delete --topic "$1" ;;
    grupos)      kafka kafka-consumer-groups --list ;;
    grupo)       kafka kafka-consumer-groups --describe --group "$1" ;;
    *)           sed -n '2,13p' "$0"; exit 1 ;;
esac
