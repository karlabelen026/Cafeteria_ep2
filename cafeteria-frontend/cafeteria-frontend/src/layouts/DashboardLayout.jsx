import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { useAuthProfile } from '../hooks/useUserRole';
import { useApiClient } from '../services/apiClient';
import { usePolling } from '../hooks/usePolling';
import { rabbitDashboardUrl } from '../utils/rabbitmq';
import '../styles/dashboard.css';

const authDisabled = import.meta.env.VITE_AUTH_DISABLED === 'true';
// Solo para el perfil local sin Azure (noauth): ahi el backend no entrega
// roles, asi que el perfil se elige a mano. Con Azure el rol viene del token.
const ROLES_LOCALES = ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO', 'BODEGUERO'];

// Grupos "Menu" y "Otros" de la sidebar (ver docs/EP2_PLAN.md seccion 6.2).
// roles=null significa "todos los roles lo ven"; si no, solo los listados
// (igual que la matriz de la seccion 4 — el backend vuelve a validar esto,
// aqui solo se oculta del menu lo que el rol ni siquiera deberia intentar).
const NAV_GROUPS = [
  {
    label: 'Menú',
    items: [
      { to: '/dashboard', label: 'Resumen', icon: '📊', roles: null, end: true },
      { to: '/dashboard/pedidos', label: 'Pedidos', icon: '🧾', roles: ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO'] },
      { to: '/dashboard/menu', label: 'Menú', icon: '☕', roles: null },
      {
        to: '/dashboard/inventario',
        label: 'Inventario',
        icon: '📦',
        roles: ['ADMIN', 'GERENTE', 'BARISTA', 'BODEGUERO'],
      },
      { to: '/dashboard/recetas', label: 'Recetas', icon: '📖', roles: ['ADMIN', 'GERENTE', 'BODEGUERO'] },
      { to: '/dashboard/clientes', label: 'Clientes', icon: '👥', roles: ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO'] },
      { to: '/dashboard/pagos', label: 'Pagos', icon: '💳', roles: ['ADMIN', 'GERENTE', 'CAJERO'] },
      { to: '/dashboard/chat', label: 'Chat del equipo', icon: '💬', roles: null },
    ],
  },
  {
    label: 'Otros',
    items: [
      { to: '/dashboard/empleados', label: 'Empleados', icon: '🧑‍🍳', roles: ['ADMIN', 'GERENTE'] },
      { to: '/dashboard/proveedores', label: 'Proveedores', icon: '🚚', roles: ['ADMIN', 'GERENTE', 'BODEGUERO'] },
      { to: '/dashboard/reportes', label: 'Reportes', icon: '📈', roles: ['ADMIN', 'GERENTE', 'CAJERO'] },
      { to: '/dashboard/alertas', label: 'Alertas', icon: '🔔', roles: null },
    ],
  },
];

function hoy() {
  return new Date().toISOString().slice(0, 10);
}

function hace7Dias() {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() - 6);
  return fecha.toISOString().slice(0, 10);
}

export default function DashboardLayout() {
  const { instance } = useMsal();
  const { role, nombre, setActiveRole, roles } = useAuthProfile();
  const { callApi } = useApiClient();
  const [sidebarAbierta, setSidebarAbierta] = useState(false);
  const [alertasNoLeidas, setAlertasNoLeidas] = useState(0);
  const [desde, setDesde] = useState(hace7Dias());
  const [hasta, setHasta] = useState(hoy());

  // La campana se refresca sola: una alerta generada por un evento (stock
  // bajo, pedido listo, mensaje en DLQ) aparece para todos los roles.
  usePolling(() => {
    callApi('/notificaciones/alertas')
      .then((alertas) => setAlertasNoLeidas((alertas || []).filter((a) => !a.leida).length))
      .catch(() => {
        /* la campana solo es informativa: si falla, no se rompe la pagina */
      });
  });

  const iniciales = (nombre || 'Equipo')
    .split(' ')
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <div className="dash">
      <div className="dash__body">
      {sidebarAbierta && <div className="dash__scrim" onClick={() => setSidebarAbierta(false)} />}

      <aside className={`dash__sidebar${sidebarAbierta ? ' dash__sidebar--open' : ''}`}>
        <div className="dash__logo">
          <span className="dash__logo-mark">CG</span>
          CafeGestión360
        </div>

        {NAV_GROUPS.map((grupo) => {
          const visibles = grupo.items.filter((item) => !item.roles || item.roles.includes(role));
          if (visibles.length === 0) return null;
          return (
            <div className="dash__nav-group" key={grupo.label}>
              <div className="dash__nav-label">{grupo.label}</div>
              {visibles.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={() => setSidebarAbierta(false)}
                  className={({ isActive }) => `dash__nav-item${isActive ? ' dash__nav-item--active' : ''}`}
                >
                  <span className="dash__nav-icon">{item.icon}</span>
                  {item.label}
                </NavLink>
              ))}
            </div>
          );
        })}

        <div className="dash__sidebar-footer">
          {role === 'ADMIN' && (
            <>
              <NavLink to="/dashboard/mensajeria" className="dash__mensajeria-card">
                <strong>🐇 Mensajería</strong>
                Administrar colas, exchanges y bindings
              </NavLink>
              <a
                href={rabbitDashboardUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="dash__mensajeria-card dash__mensajeria-card--externo"
              >
                <strong>📡 Dashboard RabbitMQ ↗</strong>
                Actividad en vivo del cluster
              </a>
            </>
          )}
        </div>
      </aside>

      <div className="dash__main">
        <header className="dash__topbar">
          <button
            className="dash__menu-toggle"
            onClick={() => setSidebarAbierta((v) => !v)}
            aria-label="Abrir menú"
          >
            ☰
          </button>
          <input className="dash__search" placeholder="Buscar..." />
          <div className="dash__topbar-spacer" />

          <div className="dash__date-range">
            <input type="date" value={desde} max={hasta} onChange={(e) => setDesde(e.target.value)} />
            <span>–</span>
            <input type="date" value={hasta} min={desde} max={hoy()} onChange={(e) => setHasta(e.target.value)} />
          </div>

          {authDisabled && (
            <select
              className="dash-select"
              value={role || ''}
              onChange={(e) => setActiveRole(e.target.value)}
              title="Perfil local (solo sin Azure)"
            >
              {ROLES_LOCALES.map((r) => (
                <option key={r} value={r}>
                  Perfil: {r}
                </option>
              ))}
            </select>
          )}
          {!authDisabled && roles && roles.length > 1 && (
            <select className="dash-select" value={role || ''} onChange={(e) => setActiveRole(e.target.value)}>
              {roles.map((r) => (
                <option key={r} value={r}>
                  Perfil: {r}
                </option>
              ))}
            </select>
          )}
          <span className="dash__role-chip">{role || 'sin rol'}</span>

          <NavLink to="/dashboard/alertas" className="dash__bell" aria-label="Alertas">
            🔔
            {alertasNoLeidas > 0 && <span className="dash__bell-badge">{alertasNoLeidas}</span>}
          </NavLink>

          <div className="dash__avatar" title={nombre}>
            <span className="dash__avatar-mark">{iniciales}</span>
          </div>

          {!authDisabled && (
            <button className="dash-btn dash-btn--ghost" onClick={() => instance.logoutRedirect()}>
              Salir
            </button>
          )}
        </header>

        <main className="dash__content">
          <Outlet context={{ desde, hasta }} />
        </main>
      </div>
      </div>
    </div>
  );
}
