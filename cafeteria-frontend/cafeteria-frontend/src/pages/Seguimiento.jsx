import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useApiClient } from '../services/apiClient';
import CoffeeIcon from '../components/CoffeeIcon';
import { badgeClassForEstado } from '../utils/badges';
import { etiquetaEstado } from '../utils/estadosPedido';

const PASOS = ['PAGADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO'];
const ESTADOS_FALLIDOS = ['PAGO_RECHAZADO', 'CANCELADO'];
const ESTADOS_FINALES = ['ENTREGADO', ...ESTADOS_FALLIDOS];

// Seguimiento publico por codigo (nunca por id secuencial, ver
// docs/EP2_PLAN.md seccion 2, hallazgo #7). Hace polling cada 3s mientras el
// pedido sigue PENDIENTE_PAGO, para que se note la asincronia del pago
// simulado (seccion 6.1), y muestra la boleta de ms-notificaciones apenas el
// pago queda aprobado.
export default function Seguimiento() {
  const { codigo } = useParams();
  const { callApi } = useApiClient();
  const [pedido, setPedido] = useState(null);
  const [ticket, setTicket] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const intervalo = useRef(null);

  useEffect(() => {
    let cancelado = false;

    function consultar() {
      callApi(`/public/pedidos/${codigo}`)
        .then((data) => {
          if (cancelado) return;
          setPedido(data);
          setError('');
          // Se sigue consultando hasta un estado final: asi el cliente ve
          // avanzar su pedido cuando el barista lo cambia desde el dashboard.
          if (ESTADOS_FINALES.includes(data.estado) && intervalo.current) {
            clearInterval(intervalo.current);
            intervalo.current = null;
          }
        })
        .catch((err) => {
          console.error(err);
          if (!cancelado) setError('No encontramos ese pedido. Verifica el código o que el backend esté corriendo.');
        })
        .finally(() => !cancelado && setCargando(false));
    }

    consultar();
    intervalo.current = setInterval(consultar, 3000);

    return () => {
      cancelado = true;
      if (intervalo.current) clearInterval(intervalo.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codigo]);

  // La boleta la genera ms-notificaciones de forma asincrona al consumir
  // pago.aprobado: puede tardar un instante en existir, asi que un 404 aqui
  // no es un error real, solo "todavia no esta lista".
  useEffect(() => {
    if (!pedido || ticket || !PASOS.includes(pedido.estado)) return;
    callApi(`/public/tickets/${codigo}`)
      .then(setTicket)
      .catch(() => {
        /* boleta aun no generada, se reintenta en el proximo render si cambia el pedido */
      });
  }, [pedido, ticket, codigo, callApi]);

  const pasoActual = pedido ? PASOS.indexOf(pedido.estado) : -1;
  const fallido = pedido && ESTADOS_FALLIDOS.includes(pedido.estado);

  return (
    <div className="app-shell">
      <header className="navbar">
        <Link to="/" className="navbar__brand">
          <CoffeeIcon />
          CafeGestión360
        </Link>
      </header>
      <div className="page">
        {cargando && (
          <div className="state-block">
            <div className="spinner" />
            <p>Buscando tu pedido...</p>
          </div>
        )}

        {error && <div className="alert alert-error">{error}</div>}

        {pedido && (
          <>
            <div className="page__header">
              <div>
                <h2>Pedido {codigo.slice(0, 8)}</h2>
                <p>
                  {pedido.estado === 'PENDIENTE_PAGO'
                    ? 'Estamos confirmando tu pago...'
                    : 'Gracias por tu compra. Así va tu pedido:'}
                </p>
              </div>
              <span className={badgeClassForEstado(pedido.estado)}>{etiquetaEstado(pedido.estado)}</span>
            </div>

            {pedido.estado === 'PENDIENTE_PAGO' && (
              <div className="state-block">
                <div className="spinner" />
                <p>Confirmando el pago (esto puede tardar unos segundos)...</p>
              </div>
            )}

            {fallido && (
              <div className="alert alert-error">
                {pedido.estado === 'PAGO_RECHAZADO'
                  ? 'El pago fue rechazado. Puedes volver a intentarlo desde la tienda.'
                  : 'Este pedido fue cancelado.'}
              </div>
            )}

            {!fallido && pasoActual >= 0 && (
              <div className="order-timeline">
                {PASOS.map((paso, idx) => (
                  <div
                    key={paso}
                    className={`order-timeline__step${idx <= pasoActual ? ' order-timeline__step--done' : ''}`}
                  >
                    <span className="order-timeline__dot" />
                    <span>{etiquetaEstado(paso)}</span>
                  </div>
                ))}
              </div>
            )}

            <div className="table-wrap" style={{ marginTop: 24 }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Producto</th>
                    <th>Cantidad</th>
                    <th>Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  {pedido.items.map((i) => (
                    <tr key={i.id}>
                      <td>{i.nombreProducto}</td>
                      <td>{i.cantidad}</td>
                      <td>${i.precioUnitario * i.cantidad}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="checkout-summary__total" style={{ marginTop: 16 }}>
              <span>Total</span>
              <strong>${pedido.total}</strong>
            </div>

            {ticket && (
              <div className="checkout-summary" style={{ marginTop: 24 }}>
                <h3>Boleta #{ticket.numero}</h3>
                {ticket.items.map((i) => (
                  <div className="checkout-summary__line" key={i.productoId}>
                    <span>
                      {i.cantidad} × {i.nombreProducto}
                    </span>
                    <span>${i.precioUnitario * i.cantidad}</span>
                  </div>
                ))}
                <div className="checkout-summary__total">
                  <span>Total pagado ({ticket.metodoPago})</span>
                  <strong>${ticket.total}</strong>
                </div>
              </div>
            )}

            <Link to="/" className="btn btn-outline" style={{ marginTop: 24, display: 'inline-flex' }}>
              Volver a la tienda
            </Link>
          </>
        )}
      </div>
    </div>
  );
}
