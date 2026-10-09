import { useState } from 'react';
import { usePolling } from '../hooks/usePolling';
import { useApiClient } from '../services/apiClient';
import { useToasts } from '../context/ToastContext.jsx';
import { useUserRole } from '../hooks/useUserRole';
import { badgeClassForEstado } from '../utils/badges';

// El unico cambio de estado posible desde la UI de staff es "anular" (ver
// ms-pagos/controller/PagoController: PATCH /{id}/anular, solo ADMIN). No
// existe un PUT libre de estado: APROBADO/RECHAZADO los decide la pasarela
// (ver docs/EP2_PLAN.md seccion 3.5), no el staff.
export default function Pagos() {
  const { callApi } = useApiClient();
  const toasts = useToasts();
  const role = useUserRole();
  const puedeAnular = role === 'ADMIN';

  const [pagos, setPagos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [anulando, setAnulando] = useState(null);

  usePolling(() => {
    callApi('/pagos')
      .then((data) => {
        setPagos(data || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar los pagos (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  });

  const anular = async (pago) => {
    setAnulando(pago.id);
    try {
      const actualizado = await callApi(`/pagos/${pago.id}/anular`, { method: 'PATCH' });
      setPagos((prev) => prev.map((p) => (p.id === pago.id ? actualizado : p)));
      toasts.success(`Pago #${pago.id} anulado.`);
    } catch (err) {
      console.error(err);
      toasts.error(err.message || `No se pudo anular el pago #${pago.id}.`);
    } finally {
      setAnulando(null);
    }
  };

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Pagos</h2>
          <p>Registro de pagos por pedido, método y estado.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {cargando && (
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando pagos...</p>
        </div>
      )}

      {!cargando && !error && pagos.length === 0 && (
        <div className="state-block">
          <h3>Todavía no hay pagos registrados</h3>
          <p>Los pagos hechos desde la tienda aparecerán aquí.</p>
        </div>
      )}

      {!cargando && pagos.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Pago</th>
                <th>Pedido</th>
                <th>Monto</th>
                <th>Método</th>
                <th>Últimos 4</th>
                <th>Estado</th>
                {puedeAnular && <th></th>}
              </tr>
            </thead>
            <tbody>
              {pagos.map((p) => (
                <tr key={p.id}>
                  <td>
                    <strong>#{p.id}</strong>
                  </td>
                  <td>#{p.pedidoId}</td>
                  <td>${p.monto}</td>
                  <td>{p.metodoPago}</td>
                  <td>{p.ultimos4 ? `•••• ${p.ultimos4}` : '—'}</td>
                  <td>
                    <span className={badgeClassForEstado(p.estado)}>{p.estado || 'Sin estado'}</span>
                  </td>
                  {puedeAnular && (
                    <td>
                      {p.estado === 'APROBADO' && (
                        <button
                          className="dash-icon-btn dash-icon-btn--danger"
                          disabled={anulando === p.id}
                          onClick={() => anular(p)}
                        >
                          {anulando === p.id ? 'Anulando...' : 'Anular'}
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
