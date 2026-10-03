import { useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useMsal, AuthenticatedTemplate, UnauthenticatedTemplate } from '@azure/msal-react';
import { loginRequest } from '../auth/authConfig';
import CoffeeIcon from '../components/CoffeeIcon';

const authDisabled = import.meta.env.VITE_AUTH_DISABLED === 'true';

// MSAL marca una interaccion (login/logout) "en curso" en sessionStorage
// (o localStorage, segun cacheLocation) antes de redirigir, y la limpia al
// volver. Si una pestaña se cierra o recarga a mitad de ese ciclo, la marca
// queda pegada y todo intento de loginRedirect posterior falla con
// "interaction_in_progress" aunque no haya ninguna interaccion real activa.
function limpiarInteractionStatusPegado() {
  [window.sessionStorage, window.localStorage].forEach((store) => {
    Object.keys(store)
      .filter((key) => key.includes('interaction.status'))
      .forEach((key) => store.removeItem(key));
  });
}

export default function StaffLogin() {
  const { instance, accounts } = useMsal();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // Vuelve a la pagina que el usuario queria ver antes de que ProtectedRoute
  // lo mandara aqui (ver auth/ProtectedRoute.jsx), o al dashboard por defecto.
  const destino = searchParams.get('redirect') || '/dashboard';

  // Si ya hay sesion (por ejemplo, justo volviendo del redirect de Azure),
  // no esperamos a que el usuario toque "Entrar al portal": lo mandamos
  // directo a donde iba.
  useEffect(() => {
    if (accounts.length > 0) {
      navigate(destino, { replace: true });
    }
  }, [accounts, destino, navigate]);

  const handleLogin = async () => {
    // Dispara el flujo Authorization Code + PKCE contra Azure Entra ID.
    // MSAL redirige al usuario y, al volver, procesa el codigo y obtiene
    // los tokens automaticamente (ver el manejo del redirect en main.jsx).
    try {
      await instance.loginRedirect(loginRequest);
    } catch (error) {
      if (error?.errorCode === 'interaction_in_progress') {
        limpiarInteractionStatusPegado();
        await instance.loginRedirect(loginRequest);
      } else {
        console.error('Error al iniciar sesion', error);
      }
    }
  };

  const handleLogout = () => {
    instance.logoutRedirect();
  };

  return (
    <div className="app-shell">
      <div className="hero">
        <div className="hero__content">
          <span className="hero__eyebrow">
            <CoffeeIcon size={16} /> Portal del equipo
          </span>
          <h1>Barra, bodega y caja — todo en un solo lugar.</h1>
          <p className="hero__subtitle">
            Acceso para baristas, cajeros y administración: pedidos en curso, inventario,
            pagos y reportes del negocio.
          </p>

          {authDisabled && (
            <div className="alert alert-warning">
              <span>
                Modo de prueba sin Azure configurado (<code>VITE_AUTH_DISABLED=true</code>).
                El backend debe estar corriendo con <code>SPRING_PROFILES_ACTIVE=noauth</code>.
              </span>
            </div>
          )}

          {window.__msalError && (
            <div className="alert alert-error">
              <span>
                Error de autenticación: <b>{window.__msalError.errorCode}</b>
                {window.__msalError.errorMessage ? ` — ${window.__msalError.errorMessage}` : ''}
              </span>
            </div>
          )}

          {authDisabled ? (
            <div className="hero__actions">
              <button className="btn btn-primary" onClick={() => navigate(destino)}>
                Entrar al portal
              </button>
              <Link to="/" className="btn btn-outline">
                Ir a la tienda
              </Link>
            </div>
          ) : (
            <>
              <UnauthenticatedTemplate>
                <div className="hero__actions">
                  <button className="btn btn-primary" onClick={handleLogin}>
                    Iniciar sesión
                  </button>
                  <Link to="/" className="btn btn-outline">
                    Ir a la tienda
                  </Link>
                </div>
              </UnauthenticatedTemplate>

              <AuthenticatedTemplate>
                <p className="hero__session">
                  Sesión iniciada como <b>{accounts[0]?.name ?? accounts[0]?.username}</b>
                </p>
                <div className="hero__actions">
                  <button className="btn btn-primary" onClick={() => navigate(destino)}>
                    Entrar al portal
                  </button>
                  <button className="btn btn-ghost" onClick={handleLogout}>
                    Cerrar sesión
                  </button>
                </div>
              </AuthenticatedTemplate>
            </>
          )}
        </div>

        <div className="hero__panel">
          <div className="hero__panel-card">
            <h3>Hoy en la barra</h3>
            <p>
              Cada pedido, cada insumo y cada boleta, trazables en tiempo real — para que el
              dueño ya no dependa de lo que ve parado en el mostrador.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
