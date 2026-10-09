import { useState } from 'react';
import { usePolling } from '../../hooks/usePolling';
import { useApiClient } from '../../services/apiClient';

const ETIQUETA = {
  STOCK_BAJO: 'Stock bajo',
  PEDIDO_LISTO: 'Pedido listo',
  DLQ: 'Mensaje en DLQ',
};

// Lista de alertas (ver docs/EP2_PLAN.md seccion 5): stock bajo, pedido listo
// y mensajes en DLQ, generadas por ms-notificaciones a partir de eventos.
export default function Alertas() {
  const { callApi } = useApiClient();
  const [alertas, setAlertas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const cargar = () => {
    callApi('/notificaciones/alertas')
      .then((data) => {
        setAlertas(data || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar las alertas (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  };

  usePolling(cargar);

  const marcarLeida = async (alerta) => {
    try {
      const actualizada = await callApi(`/notificaciones/alertas/${alerta.id}/leida`, { method: 'PATCH' });
      setAlertas((prev) => prev.map((a) => (a.id === alerta.id ? actualizada : a)));
    } catch (err) {
      console.error(err);
      setError('No se pudo marcar la alerta como leída.');
    }
  };

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Alertas</h2>
          <p>Stock bajo, pedidos listos y mensajes que terminaron en una DLQ.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {cargando && (
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando alertas...</p>
        </div>
      )}

      {!cargando && !error && alertas.length === 0 && (
        <div className="state-block">
          <h3>No hay alertas</h3>
          <p>Las alertas de stock, pedidos listos y DLQ aparecerán aquí.</p>
        </div>
      )}

      {!cargando && alertas.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Tipo</th>
                <th>Mensaje</th>
                <th>Fecha</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {alertas.map((a) => (
                <tr key={a.id}>
                  <td>{ETIQUETA[a.tipo] || a.tipo}</td>
                  <td>{a.mensaje}</td>
                  <td>{a.fecha ? new Date(a.fecha).toLocaleString('es-CL') : '—'}</td>
                  <td>
                    <span className={`badge ${a.leida ? 'badge--neutral' : 'badge--error'}`}>
                      {a.leida ? 'Leída' : 'Pendiente'}
                    </span>
                  </td>
                  <td>
                    {!a.leida && (
                      <button className="dash-icon-btn" onClick={() => marcarLeida(a)}>
                        Marcar leída
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
