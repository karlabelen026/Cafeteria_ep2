import { Routes, Route } from 'react-router-dom';
import Store from './pages/Store.jsx';
import Checkout from './pages/Checkout.jsx';
import Seguimiento from './pages/Seguimiento.jsx';
import StaffLogin from './pages/StaffLogin.jsx';
import DashboardLayout from './layouts/DashboardLayout.jsx';
import DashboardHome from './pages/dashboard/DashboardHome.jsx';
import Alertas from './pages/dashboard/Alertas.jsx';
import Recetas from './pages/dashboard/Recetas.jsx';
import Mensajeria from './pages/dashboard/Mensajeria.jsx';
import Chat from './pages/dashboard/Chat.jsx';
import Pedidos from './pages/Pedidos.jsx';
import Productos from './pages/Productos.jsx';
import Inventario from './pages/Inventario.jsx';
import Clientes from './pages/Clientes.jsx';
import Empleados from './pages/Empleados.jsx';
import Proveedores from './pages/Proveedores.jsx';
import Pagos from './pages/Pagos.jsx';
import Reportes from './pages/Reportes.jsx';
import ProtectedRoute from './auth/ProtectedRoute.jsx';
import RequireRole from './components/RequireRole.jsx';

function guarded(element, roles) {
  return roles ? <RequireRole allowed={roles}>{element}</RequireRole> : element;
}

export default function App() {
  return (
    <Routes>
      {/* Tienda publica: sin login, para clientes (ver docs/EP2_PLAN.md seccion 6.1) */}
      <Route path="/" element={<Store />} />
      <Route path="/checkout" element={<Checkout />} />
      <Route path="/seguimiento/:codigo" element={<Seguimiento />} />
      <Route path="/login" element={<StaffLogin />} />

      {/* Dashboard de staff: requiere sesion (Azure Entra ID / MSAL), layout
          con sidebar/topbar compartido y permisos por rol dentro de cada
          seccion (el backend vuelve a validar esto siempre). */}
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardHome />} />
        <Route path="pedidos" element={guarded(<Pedidos />, ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO'])} />
        <Route path="menu" element={<Productos />} />
        <Route
          path="inventario"
          element={guarded(<Inventario />, ['ADMIN', 'GERENTE', 'BARISTA', 'BODEGUERO'])}
        />
        <Route path="recetas" element={guarded(<Recetas />, ['ADMIN', 'GERENTE', 'BODEGUERO'])} />
        <Route path="clientes" element={guarded(<Clientes />, ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO'])} />
        <Route path="pagos" element={guarded(<Pagos />, ['ADMIN', 'GERENTE', 'CAJERO'])} />
        <Route path="empleados" element={guarded(<Empleados />, ['ADMIN', 'GERENTE'])} />
        <Route path="proveedores" element={guarded(<Proveedores />, ['ADMIN', 'GERENTE', 'BODEGUERO'])} />
        <Route path="reportes" element={guarded(<Reportes />, ['ADMIN', 'GERENTE', 'CAJERO'])} />
        <Route path="alertas" element={<Alertas />} />
        <Route path="chat" element={<Chat />} />
        <Route path="mensajeria" element={guarded(<Mensajeria />, ['ADMIN'])} />
      </Route>
    </Routes>
  );
}
