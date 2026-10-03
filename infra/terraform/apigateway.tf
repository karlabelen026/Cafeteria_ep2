# HTTP API (mas barata y simple que una REST API clasica) que reenvia todo
# "/api/*" al bff-gateway de la EC2 vía integracion HTTP_PROXY, inyectando el
# header X-Origin-Verify para que el BFF sepa que la peticion pasó por aqui
# (ver OriginVerifyGlobalFilter y docs/EP2_PLAN.md sección 8.2 / prueba S8).

resource "aws_apigatewayv2_api" "app" {
  name          = "cafeteria360-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "ec2_proxy" {
  api_id             = aws_apigatewayv2_api.app.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://${aws_eip.app.public_ip}:8080/api/{proxy}"
  connection_type    = "INTERNET"

  # El "parameter mapping" para transformar/agregar headers de integracion
  # solo esta soportado con payload format version 1.0 en HTTP APIs.
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:header.x-origin-verify" = random_password.origin_verify_secret.result
  }
}

resource "aws_apigatewayv2_route" "ec2_proxy" {
  api_id    = aws_apigatewayv2_api.app.id
  route_key = "ANY /api/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.ec2_proxy.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.app.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }
}
