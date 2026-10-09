import { useMemo, useState } from 'react';
import { usePolling } from '../../../hooks/usePolling';
import { useApiClient } from '../../../services/apiClient';

const METODOS_ICONO = { EFECTIVO: '💵', DEBITO: '💳', CREDITO: '💳', TRANSFERENCIA: '🏦' };

function esHoy(fechaIso) {
  if (!fechaIso) return false;
  const fecha = new Date(fechaIso);
  const hoy = new Date();
  return (
    fecha.getFullYear() === hoy.getFullYear() &&
    fecha.getMonth() === hoy.getMonth() &&
    fecha.getDate() === hoy.getDate()
  );
}

// "Caja del día" (ver docs/EP2_PLAN.md seccion 6.2): total por metodo de
// pago + ultimos pagos, solo los aprobados de hoy.
export default function CajaDelDia() {
  const { callApi } = useApiClient();
  const [pagos, setPagos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

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

  const pagosHoy = useMemo(
    () => pagos.filter((p) => p.estado === 'APROBADO' && esHoy(p.fecha)),
    [pagos]
  );

  const totalesPorMetodo = useMemo(() => {
    const mapa = new Map();
    for (const p of pagosHoy) {
      mapa.set(p.metodoPago, (mapa.get(p.metodoPago) || 0) + p.monto);
    }
    return Array.from(mapa.entries()).map(([metodo, total]) => ({ metodo, total }));
  }, [pagosHoy]);

  const totalDia = pagosHoy.reduce((acc, p) => acc + p.monto, 0);
  const ultimos = [...pagosHoy].sort((a, b) => new Date(b.fecha) - new Date(a.fecha)).slice(0, 10);

  if (cargando) {
    return (
      <div className="dash-card">
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando caja...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="alert alert-error">{error}</div>;
  }

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Caja del día</h2>
          <p>Pagos aprobados de hoy, por método.</p>
        </div>
      </div>

      <div className="dash__kpi-row">
        <div className="dash-card">
          <p className="dash-kpi__label">Total del día</p>
          <p className="dash-kpi__value">${totalDia}</p>
        </div>
        {totalesPorMetodo.map((t) => (
          <div className="dash-card" key={t.metodo}>
            <div className="dash-kpi__icon">{METODOS_ICONO[t.metodo] || '💰'}</div>
            <p className="dash-kpi__label">{t.metodo}</p>
            <p className="dash-kpi__value">${t.total}</p>
          </div>
        ))}
      </div>

      <div className="dash-card">
        <div className="dash-card__header">
          <h3>Últimos pagos</h3>
        </div>
        {ultimos.length === 0 ? (
          <p className="dash-empty">Todavía no hay pagos hoy.</p>
        ) : (
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Pago</th>
                  <th>Pedido</th>
                  <th>Monto</th>
                  <th>Método</th>
                  <th>Hora</th>
                </tr>
              </thead>
              <tbody>
                {ultimos.map((p) => (
                  <tr key={p.id}>
                    <td>#{p.id}</td>
                    <td>#{p.pedidoId}</td>
                    <td>${p.monto}</td>
                    <td>{p.metodoPago}</td>
                    <td>{new Date(p.fecha).toLocaleTimeString('es-CL')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
