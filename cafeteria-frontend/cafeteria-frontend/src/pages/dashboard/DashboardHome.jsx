import { useState } from 'react';
import { NavLink, useOutletContext } from 'react-router-dom';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
} from 'recharts';
import { useApiClient } from '../../services/apiClient';
import { useAuthProfile } from '../../hooks/useUserRole';
import { usePolling } from '../../hooks/usePolling';
import { rabbitDashboardUrl } from '../../utils/rabbitmq';
import KanbanBarista from './roles/KanbanBarista.jsx';
import CajaDelDia from './roles/CajaDelDia.jsx';
import InsumosCriticos from './roles/InsumosCriticos.jsx';

const COLOR_ACTUAL = '#5c3420';
const COLOR_ANTERIOR = '#e6cfb5';
const COLORES_FRANJA = ['#a8744a', '#e6cfb5', '#5c3420'];

const formatoCLP = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });

function nombreDia(fecha) {
  const dias = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
  return dias[new Date(fecha + 'T00:00:00').getDay()];
}

function Delta({ valor }) {
  if (valor === null || valor === undefined || Number.isNaN(valor)) return null;
  const subio = valor >= 0;
  return (
    <span className={`dash-kpi__delta ${subio ? 'dash-kpi__delta--up' : 'dash-kpi__delta--down'}`}>
      {subio ? '↑' : '↓'} {Math.abs(valor).toFixed(1)}% vs semana anterior
    </span>
  );
}

function KpiCard({ icono, etiqueta, valor, delta }) {
  return (
    <div className="dash-card">
      <div className="dash-kpi__icon">{icono}</div>
      <p className="dash-kpi__label">{etiqueta}</p>
      <p className="dash-kpi__value">{valor}</p>
      {delta !== undefined && <Delta valor={delta} />}
    </div>
  );
}

function Skeleton({ height = 220 }) {
  return <div className="dash-skeleton" style={{ height }} />;
}

export default function DashboardHome() {
  const { role, nombre } = useAuthProfile();
  const { desde, hasta } = useOutletContext();
  const { callApi } = useApiClient();

  const [datos, setDatos] = useState(null);
  const [alertas, setAlertas] = useState([]);
  const [dlqResumen, setDlqResumen] = useState(null);
  const [cluster, setCluster] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const esVistaKpi = role === 'ADMIN' || role === 'GERENTE';

  // Se refresca solo: una venta, un pedido o una alerta nuevos (generados por
  // cualquier rol o por un evento de RabbitMQ) aparecen sin recargar.
  usePolling(
    () => {
      if (!esVistaKpi) {
        setCargando(false);
        return;
      }
      const query = `?desde=${desde}&hasta=${hasta}`;
      const pedidos = [
        callApi(`/reportes/dashboard${query}`).then(setDatos),
        callApi('/notificaciones/alertas').then((a) => setAlertas(a || [])),
      ];
      if (role === 'ADMIN') {
        // El estado de RabbitMQ es informativo: si falla no debe tapar los KPI.
        callApi('/rabbitmq/dlq').then((d) => setDlqResumen(d || [])).catch(() => setDlqResumen([]));
        callApi('/rabbitmq/cluster').then(setCluster).catch(() => setCluster(null));
      }
      Promise.all(pedidos)
        .then(() => setError(''))
        .catch((err) => {
          console.error(err);
          setError('No se pudo cargar el dashboard (revisa el token o el backend).');
        })
        .finally(() => setCargando(false));
    },
    undefined,
    [desde, hasta, role],
  );

  if (!esVistaKpi) {
    return (
      <div>
        <div className="dash__greeting">
          <h1>Bienvenido de vuelta, {nombre || 'equipo'}</h1>
          <p>Así va la cafetería hoy</p>
        </div>
        {role === 'BARISTA' && <KanbanBarista />}
        {role === 'CAJERO' && <CajaDelDia />}
        {role === 'BODEGUERO' && <InsumosCriticos />}
        {!['BARISTA', 'CAJERO', 'BODEGUERO'].includes(role) && (
          <div className="dash-card">
            <p className="dash-empty">Tu rol no tiene una vista asignada todavía.</p>
          </div>
        )}
      </div>
    );
  }

  const totalDlq = (dlqResumen || []).reduce((acc, d) => acc + d.mensajes, 0);
  const nodosActivos = cluster?.nodos?.filter((n) => n.running).length;
  const ventasChart = (datos?.ventasUltimos7Dias || []).map((v, i) => ({
    fecha: nombreDia(v.fecha),
    actual: v.totalVentas,
    anterior: datos?.ventasSemanaAnterior?.[i]?.totalVentas ?? 0,
  }));

  return (
    <div>
      <div className="dash__greeting">
        <h1>Bienvenido de vuelta, {nombre || 'equipo'}</h1>
        <p>Así va la cafetería hoy</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {cargando && !datos ? (
        <div className="dash__kpi-row">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} height={110} />
          ))}
        </div>
      ) : (
        datos && (
          <div className="dash__kpi-row">
            <KpiCard icono="💰" etiqueta="Ventas hoy" valor={formatoCLP.format(datos.ventasHoy)}
              delta={datos.variacionVentasSemanaPorcentaje} />
            <KpiCard icono="🧾" etiqueta="Pedidos hoy" valor={datos.pedidosHoy} />
            <KpiCard icono="🎯" etiqueta="Ticket promedio" valor={formatoCLP.format(datos.ticketPromedio)} />
            <KpiCard icono="🙋" etiqueta="Clientes nuevos" valor={datos.clientesNuevosHoy} />
            {role === 'ADMIN' && (
              <KpiCard icono="☠️" etiqueta="Mensajes en DLQ" valor={totalDlq} />
            )}
          </div>
        )
      )}

      <div className="dash__row">
        <div className="dash-card">
          <div className="dash-card__header">
            <div>
              <h3>Ventas últimos 7 días</h3>
              <span className="dash-card__sub">Esta semana vs. semana anterior</span>
            </div>
            <div className="dash-legend">
              <span><i className="dash-legend__dot" style={{ background: COLOR_ACTUAL }} />Actual</span>
              <span><i className="dash-legend__dot" style={{ background: COLOR_ANTERIOR }} />Anterior</span>
            </div>
          </div>
          {cargando && !datos ? (
            <Skeleton />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={ventasChart} barGap={2}>
                <CartesianGrid vertical={false} stroke="#efe4d8" />
                <XAxis dataKey="fecha" tick={{ fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis hide />
                <Tooltip formatter={(v) => formatoCLP.format(v)} />
                <Bar dataKey="anterior" fill={COLOR_ANTERIOR} radius={[4, 4, 0, 0]} />
                <Bar dataKey="actual" fill={COLOR_ACTUAL} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Pedidos por franja</h3>
          </div>
          {cargando && !datos ? (
            <Skeleton />
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={datos?.pedidosPorFranja || []}
                  dataKey="cantidad"
                  nameKey="franja"
                  innerRadius={50}
                  outerRadius={80}
                >
                  {(datos?.pedidosPorFranja || []).map((_, i) => (
                    <Cell key={i} fill={COLORES_FRANJA[i % COLORES_FRANJA.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="dash__row--tres">
        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Productos más pedidos</h3>
          </div>
          {datos?.topProductos?.length ? (
            <ul className="dash-list">
              {datos.topProductos.map((p) => (
                <li className="dash-list__item" key={p.productoId}>
                  <span className="dash-list__thumb">☕</span>
                  <div className="dash-list__title">
                    <strong>{p.nombreProducto}</strong>
                    <span>{p.cantidad} vendidos</span>
                  </div>
                  <strong>{formatoCLP.format(p.monto)}</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p className="dash-empty">Sin ventas en el rango seleccionado.</p>
          )}
        </div>

        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Pedidos por hora hoy</h3>
          </div>
          {cargando && !datos ? (
            <Skeleton height={160} />
          ) : (
            <ResponsiveContainer width="100%" height={160}>
              <LineChart data={datos?.pedidosPorHoraHoy || []}>
                <XAxis dataKey="hora" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis hide />
                <Tooltip />
                <Line type="monotone" dataKey="cantidad" stroke={COLOR_ACTUAL} strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Alertas</h3>
          </div>
          {alertas.length ? (
            <ul className="dash-list">
              {alertas.slice(0, 6).map((a) => (
                <li
                  key={a.id}
                  className={`dash-alert dash-alert--${a.tipo === 'STOCK_BAJO' ? 'stock' : a.tipo === 'DLQ' ? 'dlq' : 'pedido'}`}
                >
                  {a.mensaje}
                </li>
              ))}
            </ul>
          ) : (
            <p className="dash-empty">Sin alertas pendientes.</p>
          )}
        </div>
      </div>

      {role === 'ADMIN' && (
        <div className="dash-card">
          <div className="dash-card__header">
            <div>
              <h3>Mensajería (RabbitMQ)</h3>
              <span className="dash-card__sub">
                Cluster: {nodosActivos ?? '—'}/{cluster?.nodos?.length ?? '—'} nodos activos · {totalDlq} mensajes en
                DLQ
              </span>
            </div>
            <a className="dash-btn" href={rabbitDashboardUrl} target="_blank" rel="noopener noreferrer">
              Abrir dashboard de RabbitMQ ↗
            </a>
          </div>
          <p className="dash-empty">
            La actividad de colas, exchanges, conexiones y consumidores se ve en el dashboard propio de RabbitMQ
            (usuario y clave del broker). Para crear o eliminar colas, exchanges y bindings usa{' '}
            <NavLink to="/dashboard/mensajeria">Mensajería</NavLink>.
          </p>
        </div>
      )}
    </div>
  );
}
