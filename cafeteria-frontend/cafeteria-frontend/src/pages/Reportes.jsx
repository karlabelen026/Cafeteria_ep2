import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import ResourcePage from '../components/ResourcePage';
import TiempoRealKafka from '../components/TiempoRealKafka';
import { useAuthProfile } from '../hooks/useUserRole';

const CLP = (n) => `$${Math.round(n || 0).toLocaleString('es-CL')}`;

const ICONS = {
  ventas: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M3 17l6-6 4 4 7-8"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M15 7h5v5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  pedidos: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M4 7h16l-1.5 11a2 2 0 01-2 1.8H7.5a2 2 0 01-2-1.8L4 7z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <path d="M8 7V5a4 4 0 018 0v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  ),
  promedio: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
      <path d="M12 7v5l3 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  mejorDia: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M12 3l2.6 5.6 6.1.6-4.6 4.1 1.3 6-5.4-3.2-5.4 3.2 1.3-6-4.6-4.1 6.1-.6L12 3z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    </svg>
  ),
};

function tendencia(serie) {
  if (serie.length < 2) return null;
  const mitad = Math.floor(serie.length / 2) || 1;
  const previo = serie.slice(0, mitad);
  const reciente = serie.slice(mitad);
  const promedio = (arr) => arr.reduce((a, b) => a + b, 0) / (arr.length || 1);
  const antes = promedio(previo);
  const ahora = promedio(reciente);
  if (antes === 0) return null;
  return ((ahora - antes) / antes) * 100;
}

function KpiCard({ icon, label, value, delta, deltaLabel, caption }) {
  const subiendo = typeof delta === 'number' && delta >= 0;
  return (
    <div className="kpi-card">
      <div className="kpi-card__icon">{icon}</div>
      <div className="kpi-card__label">{label}</div>
      <div className="kpi-card__value">{value}</div>
      {typeof delta === 'number' && (
        <div className={`kpi-card__delta ${subiendo ? 'kpi-card__delta--up' : 'kpi-card__delta--down'}`}>
          {subiendo ? '↑' : '↓'} {Math.abs(delta).toFixed(1)}% {deltaLabel}
        </div>
      )}
      {caption && <div className="kpi-card__caption">{caption}</div>}
    </div>
  );
}

function resumen(ventas) {
  const ordenadas = [...ventas].sort((a, b) => (a.fecha > b.fecha ? 1 : -1));
  const totalVentas = ordenadas.reduce((acc, v) => acc + (v.totalVentas || 0), 0);
  const totalPedidos = ordenadas.reduce((acc, v) => acc + (v.cantidadPedidos || 0), 0);
  const promedioDiario = ordenadas.length ? totalVentas / ordenadas.length : 0;
  const mejorDia = ordenadas.reduce(
    (mejor, v) => ((v.totalVentas || 0) > (mejor?.totalVentas || 0) ? v : mejor),
    null
  );

  const deltaVentas = tendencia(ordenadas.map((v) => v.totalVentas || 0));
  const deltaPedidos = tendencia(ordenadas.map((v) => v.cantidadPedidos || 0));

  const chartData = ordenadas.map((v) => ({
    fecha: v.fecha,
    Ventas: v.totalVentas || 0,
    Pedidos: v.cantidadPedidos || 0,
  }));

  return (
    <>
      <div className="kpi-grid">
        <KpiCard
          icon={ICONS.ventas}
          label="Ventas totales"
          value={CLP(totalVentas)}
          delta={deltaVentas}
          deltaLabel="vs. período anterior"
        />
        <KpiCard
          icon={ICONS.pedidos}
          label="Pedidos totales"
          value={totalPedidos}
          delta={deltaPedidos}
          deltaLabel="vs. período anterior"
        />
        <KpiCard icon={ICONS.promedio} label="Promedio diario" value={CLP(promedioDiario)} />
        {mejorDia && (
          <KpiCard icon={ICONS.mejorDia} label="Mejor día" value={CLP(mejorDia.totalVentas)} caption={mejorDia.fecha} />
        )}
      </div>

      {chartData.length > 1 && (
        <div className="dashboard-charts">
          <div className="chart-panel chart-panel--wide">
            <h3 className="chart-panel__title">Ventas por día</h3>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={chartData} barCategoryGap="28%">
                <CartesianGrid stroke="#ece1d3" vertical={false} />
                <XAxis dataKey="fecha" tick={{ fontSize: 12, fill: '#8a7968' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#8a7968' }} axisLine={false} tickLine={false} width={70} />
                <Tooltip
                  cursor={{ fill: 'rgba(193, 98, 45, 0.08)' }}
                  formatter={(value) => CLP(value)}
                  contentStyle={{ borderRadius: 10, border: '1px solid #ece1d3' }}
                />
                <Bar dataKey="Ventas" fill="#c1622d" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="chart-panel">
            <h3 className="chart-panel__title">Pedidos por día</h3>
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={chartData}>
                <CartesianGrid stroke="#ece1d3" vertical={false} />
                <XAxis dataKey="fecha" tick={{ fontSize: 12, fill: '#8a7968' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#8a7968' }} axisLine={false} tickLine={false} width={40} />
                <Tooltip contentStyle={{ borderRadius: 10, border: '1px solid #ece1d3' }} />
                <Line
                  type="monotone"
                  dataKey="Pedidos"
                  stroke="#6f8a6a"
                  strokeWidth={2.5}
                  dot={{ r: 3, fill: '#6f8a6a' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </>
  );
}

export default function Reportes() {
  const { role } = useAuthProfile();
  // El tiempo real (Kafka) es solo para ADMIN y GERENTE; CAJERO ve las ventas diarias.
  const veTiempoReal = role === 'ADMIN' || role === 'GERENTE';
  return (
    <ResourcePage
      title="Reportes"
      subtitle="Ventas diarias agregadas — visibilidad real para el dueño."
      endpoint="/reportes"
      emptyTitle="Todavía no hay reportes generados"
      emptyText="Registra ventas diarias desde el backend para ver el resumen aquí."
      renderAbove={(ventas) => (
        <>
          {veTiempoReal && <TiempoRealKafka />}
          {ventas.length ? resumen(ventas) : null}
        </>
      )}
      columns={[
        { key: 'fecha', label: 'Fecha', render: (v) => <strong>{v.fecha}</strong> },
        { key: 'totalVentas', label: 'Ventas del día', render: (v) => `$${v.totalVentas}` },
        { key: 'cantidadPedidos', label: 'Pedidos del día' },
      ]}
    />
  );
}
