#!/usr/bin/env bash
# Smoke test de CafeGestion360 (ver docs/EP2_PLAN.md Fase 7 / sección 11).
# Recorre con curl: productos públicos, checkout, seguimiento hasta PAGADO,
# tarjeta 9999 hasta DLQ, los endpoints del admin de RabbitMQ y el pipeline
# antifraude de Kafka (Fase 8).
#
# Uso:
#   ./scripts/smoke-test.sh [BASE_URL] [ADMIN_TOKEN]
#
# BASE_URL   por defecto http://localhost:8080
# ADMIN_TOKEN opcional: access token de un usuario ADMIN (Azure Entra ID).
#             Sin token, se omiten las pruebas de /api/rabbitmq/** (requieren
#             rol ADMIN) y el script sigue probando lo público. Con
#             SPRING_PROFILES_ACTIVE=noauth no hace falta token.
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
ADMIN_TOKEN="${2:-}"

OK=0
FAIL=0

verde() { printf '\033[32m%s\033[0m\n' "$1"; }
rojo()  { printf '\033[31m%s\033[0m\n' "$1"; }

# $1 = descripcion, $2 = status esperado, $3.. = argumentos curl
chequear() {
    local descripcion="$1"; shift
    local esperado="$1"; shift
    local status
    status=$(curl -s -o /tmp/smoke-test-body.json -w '%{http_code}' "$@")
    if [ "$status" = "$esperado" ]; then
        verde "OK   $descripcion (status $status)"
        OK=$((OK + 1))
    else
        rojo "FAIL $descripcion (esperado $esperado, llego $status)"
        cat /tmp/smoke-test-body.json
        FAIL=$((FAIL + 1))
    fi
}

cuerpo() { cat /tmp/smoke-test-body.json; }

echo "== CafeGestion360 — smoke test contra $BASE_URL =="

echo
echo "-- 1) Productos públicos --"
chequear "GET /api/productos sin token -> 200" 200 "$BASE_URL/api/productos"

echo
echo "-- 2) Checkout público: tarjeta OK --"
CHECKOUT_OK=$(curl -s -X POST "$BASE_URL/api/public/checkout" \
    -H "Content-Type: application/json" \
    -d '{"cliente":{"nombre":"Smoke Test","email":"smoke@cafegestion360.cl"},"items":[{"productoId":1,"cantidad":1}],"pago":{"metodo":"CREDITO","numeroTarjeta":"4111111111111234"}}')
CODIGO_OK=$(echo "$CHECKOUT_OK" | grep -o '"codigoSeguimiento"[^,}]*' | sed 's/.*:"\(.*\)"/\1/')
if [ -n "$CODIGO_OK" ]; then
    verde "OK   Checkout creado (codigoSeguimiento=$CODIGO_OK)"
    OK=$((OK + 1))
else
    rojo "FAIL Checkout no devolvio codigoSeguimiento"
    echo "$CHECKOUT_OK"
    FAIL=$((FAIL + 1))
fi

echo
echo "-- 3) Seguimiento hasta PAGADO (polling cada 3s, máx. 10 intentos) --"
if [ -n "$CODIGO_OK" ]; then
    ESTADO=""
    for intento in $(seq 1 10); do
        ESTADO=$(curl -s "$BASE_URL/api/public/pedidos/$CODIGO_OK" | grep -o '"estado"[^,}]*' | sed 's/.*:"\(.*\)"/\1/')
        echo "   intento $intento: estado=$ESTADO"
        [ "$ESTADO" = "PAGADO" ] && break
        sleep 3
    done
    if [ "$ESTADO" = "PAGADO" ]; then
        verde "OK   Pedido $CODIGO_OK llego a PAGADO"
        OK=$((OK + 1))
    else
        rojo "FAIL Pedido $CODIGO_OK quedo en estado '$ESTADO' (se esperaba PAGADO)"
        FAIL=$((FAIL + 1))
    fi
else
    rojo "FAIL Se omite el polling: no hay codigoSeguimiento"
    FAIL=$((FAIL + 1))
fi

echo
echo "-- 4) Tarjeta 9999: 3 reintentos y luego DLQ --"
CHECKOUT_9999=$(curl -s -X POST "$BASE_URL/api/public/checkout" \
    -H "Content-Type: application/json" \
    -d '{"cliente":{"nombre":"Smoke Test DLQ","email":"smoke-dlq@cafegestion360.cl"},"items":[{"productoId":1,"cantidad":1}],"pago":{"metodo":"CREDITO","numeroTarjeta":"4111111111119999"}}')
CODIGO_9999=$(echo "$CHECKOUT_9999" | grep -o '"codigoSeguimiento"[^,}]*' | sed 's/.*:"\(.*\)"/\1/')
if [ -n "$CODIGO_9999" ]; then
    verde "OK   Checkout con tarjeta 9999 creado (codigoSeguimiento=$CODIGO_9999)"
    OK=$((OK + 1))
    echo "   Esperando ~35s a que se agoten los 3 reintentos (x-delivery-limit)..."
    sleep 35
    if [ -n "$ADMIN_TOKEN" ]; then
        chequear "GET /api/rabbitmq/dlq muestra mensajes en pagos.pedido-creado.queue.dlq" 200 \
            "$BASE_URL/api/rabbitmq/dlq" -H "Authorization: Bearer $ADMIN_TOKEN"
        if grep -q "pagos.pedido-creado.queue.dlq" /tmp/smoke-test-body.json 2>/dev/null; then
            verde "OK   pagos.pedido-creado.queue.dlq aparece en el resumen de DLQ"
            OK=$((OK + 1))
        else
            rojo "FAIL pagos.pedido-creado.queue.dlq no aparece en el resumen de DLQ"
            FAIL=$((FAIL + 1))
        fi
    else
        echo "   (sin ADMIN_TOKEN: revisa a mano en http://localhost:15672 la cola pagos.pedido-creado.queue.dlq)"
    fi
else
    rojo "FAIL Checkout con tarjeta 9999 no devolvio codigoSeguimiento"
    FAIL=$((FAIL + 1))
fi

echo
echo "-- 5) Endpoints de ms-rabbitmq-admin (requieren rol ADMIN) --"
if [ -n "$ADMIN_TOKEN" ]; then
    chequear "GET /api/rabbitmq/queues -> 200" 200 \
        "$BASE_URL/api/rabbitmq/queues" -H "Authorization: Bearer $ADMIN_TOKEN"
    chequear "GET /api/rabbitmq/exchanges -> 200" 200 \
        "$BASE_URL/api/rabbitmq/exchanges" -H "Authorization: Bearer $ADMIN_TOKEN"
    chequear "GET /api/rabbitmq/cluster -> 200" 200 \
        "$BASE_URL/api/rabbitmq/cluster" -H "Authorization: Bearer $ADMIN_TOKEN"
    chequear "POST /api/rabbitmq/queues con nombre vacio -> 400" 400 \
        -X POST "$BASE_URL/api/rabbitmq/queues" \
        -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
        -d '{"name":""}'
    chequear "DELETE pagos.pedido-creado.queue (protegida) -> 409" 409 \
        -X DELETE "$BASE_URL/api/rabbitmq/queues/pagos.pedido-creado.queue" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
    chequear "POST /api/rabbitmq/queues/smoke-test.queue -> 201" 201 \
        -X POST "$BASE_URL/api/rabbitmq/queues" \
        -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
        -d '{"name":"smoke-test.queue"}'
    chequear "DELETE /api/rabbitmq/queues/smoke-test.queue -> 204" 204 \
        -X DELETE "$BASE_URL/api/rabbitmq/queues/smoke-test.queue" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
else
    echo "   (sin ADMIN_TOKEN: se omiten. Pasa un access token ADMIN como segundo argumento para incluirlas)"
fi

echo
echo "-- 6) Kafka + Kafka Streams: monitoreo antifraude (requiere rol ADMIN) --"
if [ -n "$ADMIN_TOKEN" ]; then
    CLIENTE_KAFKA="smoke-$(date +%s)@mail.cl"
    simular() {
        chequear "$1" 202 -X POST "$BASE_URL/api/antifraude/simular" \
            -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "$2"
    }
    simular "Simular 500 CLP (sin alerta) -> 202" "{\"clienteEmail\":\"$CLIENTE_KAFKA\",\"monto\":500,\"metodoPago\":\"DEBITO\"}"
    simular "Simular 60000 CLP (alerta ALTA) -> 202" "{\"clienteEmail\":\"$CLIENTE_KAFKA\",\"monto\":60000,\"metodoPago\":\"CREDITO\"}"
    simular "Simular 5 CLP (filtrada) -> 202" "{\"clienteEmail\":\"$CLIENTE_KAFKA\",\"monto\":5,\"metodoPago\":\"EFECTIVO\"}"
    chequear "Simular con email invalido -> 400" 400 -X POST "$BASE_URL/api/antifraude/simular" \
        -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
        -d '{"clienteEmail":"no-es-email","monto":500,"metodoPago":"DEBITO"}'

    echo "   Esperando a que Kafka Streams procese (8s)..."
    sleep 8
    chequear "GET /api/antifraude/totales/$CLIENTE_KAFKA -> 200" 200 \
        "$BASE_URL/api/antifraude/totales/$CLIENTE_KAFKA" -H "Authorization: Bearer $ADMIN_TOKEN"
    if cuerpo | grep -q '"totalClp":60500'; then
        verde "OK   Total acumulado = 60500 CLP (los 5 CLP se filtraron)"
        OK=$((OK + 1))
    else
        rojo "FAIL Total acumulado inesperado: $(cuerpo)"
        FAIL=$((FAIL + 1))
    fi
    chequear "GET /api/antifraude/alertas -> 200" 200 \
        "$BASE_URL/api/antifraude/alertas" -H "Authorization: Bearer $ADMIN_TOKEN"
    if cuerpo | grep -q "\"cliente\":\"$CLIENTE_KAFKA\",\"severidad\":\"ALTA\""; then
        verde "OK   Alerta ALTA de $CLIENTE_KAFKA recibida desde el topic alertas-fraude"
        OK=$((OK + 1))
    else
        rojo "FAIL No llego la alerta ALTA de $CLIENTE_KAFKA"
        FAIL=$((FAIL + 1))
    fi
    chequear "POST /api/antifraude/carga?cantidad=1000 -> 200" 200 -X POST \
        "$BASE_URL/api/antifraude/carga?cantidad=1000" -H "Authorization: Bearer $ADMIN_TOKEN"
    echo "   $(cuerpo)"
else
    echo "   (sin ADMIN_TOKEN: se omiten. Revisa a mano en Kafka UI: http://localhost:8095)"
fi

echo
echo "== Resultado: $OK OK, $FAIL FAIL =="
rm -f /tmp/smoke-test-body.json
[ "$FAIL" -eq 0 ]
