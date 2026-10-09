// Como se le muestra al equipo (y al cliente) cada estado interno de un
// pedido. El backend sigue usando su maquina de estados completa (ver
// ms-pedidos/service/MaquinaEstadosPedido); la confirmacion del pago es un
// paso interno que se resuelve solo por mensajeria y se revisa en "Pagos",
// asi que en las vistas de pedidos el flujo visible es simplemente
// Pendiente -> En preparación -> Listo -> Entregado.
export const ETIQUETA_ESTADO = {
  PENDIENTE_PAGO: 'Recibido',
  PAGADO: 'Pendiente',
  EN_PREPARACION: 'En preparación',
  LISTO: 'Listo',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
  PAGO_RECHAZADO: 'No completado',
};

// Estados que pertenecen al cobro y no a la preparacion: no se listan en
// las vistas de pedidos del equipo.
export const ESTADOS_DE_PAGO = ['PENDIENTE_PAGO', 'PAGO_RECHAZADO'];

export function etiquetaEstado(estado) {
  return ETIQUETA_ESTADO[estado] || estado || 'Sin estado';
}
