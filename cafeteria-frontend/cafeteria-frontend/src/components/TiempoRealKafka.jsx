import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { usePolling } from '../hooks/usePolling';
import { useApiClient } from '../services/apiClient';

const REFRESCO_MS = 5000;
const CLP = (n) => `$${Math.round(n || 0).toLocaleString('es-CL')}`;
const horaMinuto = (iso) => new Date(iso).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });

// Reporteria en tiempo real alimentada por Kafka: ms-reportes consume el topic
// cafeteria.pagos.transacciones con su propio consumer group
// (reportes-tiempo-real), en paralelo a ms-antifraude, y guarda cada
// transaccion en la auditoria con su particion y offset.
export default function TiempoRealKafka() {
  const { callApi } = useApiClient();
  const [metricas, setMetricas] = useState(null);
  const [ultimas, setUltimas] = useState([]);
  const [error, setError] = useState('');

  usePolling(() => {
    Promise.all([callApi('/reportes/tiempo-real?minutos=15'), callApi('/reportes/auditoria/transacciones?limite=5')])
      .then(([m, u]) => {
        setMetricas(m);
        setUltimas(u || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudo cargar la reportería en tiempo real.');
      });
  }, REFRESCO_MS);

  const serie = (metricas?.serie || []).map((p) => ({ minuto: horaMinuto(p.minuto), Transacciones: p.transacciones }));

  return (
    <div className="chart-panel" style={{ marginBottom: 24 }}>
      <h3 className="chart-panel__title">Tiempo real (Kafka) · últimos 15 minutos</h3>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-card__label">Transacciones</div>
          <div className="kpi-card__value">{metricas?.totalTransacciones ?? 0}</div>
          <div className="kpi-card__caption">
            {metricas?.aprobadas ?? 0} aprobadas · {metricas?.rechazadas ?? 0} rechazadas
          </div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__label">Tasa de aprobación</div>
          <div className="kpi-card__value">{(metricas?.tasaAprobacionPct ?? 0).toFixed(1)}%</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__label">Monto aprobado</div>
          <div className="kpi-card__value">{CLP(metricas?.montoAprobado)}</div>
          <div className="kpi-card__caption">Ticket promedio {CLP(metricas?.ticketPromedio)}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-card__label">Latencia Kafka</div>
          <div className="kpi-card__value">{Math.round(metricas?.latenciaPromedioMs ?? 0)} ms</div>
          <div className="kpi-card__caption">De la publicación al registro</div>
        </div>
      </div>

      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={serie} barCategoryGap="20%">
          <CartesianGrid stroke="#ece1d3" vertical={false} />
          <XAxis dataKey="minuto" tick={{ fontSize: 11, fill: '#8a7968' }} axisLine={false} tickLine={false} />
          <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#8a7968' }} axisLine={false} tickLine={false} width={30} />
          <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid #ece1d3' }} />
          <Bar dataKey="Transacciones" fill="#c1622d" radius={[4, 4, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>

      <h3 className="chart-panel__title">Auditoría: últimas transacciones</h3>
      {ultimas.length === 0 ? (
        <p className="kpi-card__caption">Todavía no llegan transacciones. Aparecen solas al pagar un pedido.</p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Hora</th>
                <th>Pedido</th>
                <th>Cliente</th>
                <th>Monto</th>
                <th>Estado</th>
                <th>Partición / offset</th>
              </tr>
            </thead>
            <tbody>
              {ultimas.map((t) => (
                <tr key={t.eventId}>
                  <td>{new Date(t.ocurridoEn).toLocaleTimeString('es-CL')}</td>
                  <td>#{t.pedidoId}</td>
                  <td>{t.clienteEmail}</td>
                  <td>{CLP(t.monto)}</td>
                  <td>{t.aprobado ? 'Aprobado' : 'Rechazado'}</td>
                  <td>
                    {t.particion} / {t.offset}
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
