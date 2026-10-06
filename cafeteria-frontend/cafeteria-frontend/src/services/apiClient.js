import { useMsal } from '@azure/msal-react';
import { InteractionRequiredAuthError } from '@azure/msal-browser';
import { loginRequest, apiBaseUrl } from '../auth/authConfig';

const authDisabled = import.meta.env.VITE_AUTH_DISABLED === 'true';

// Lee el cuerpo de una respuesta de error (formato {timestamp,status,error,
// message,fieldErrors} del GlobalExceptionHandler de cada microservicio, ver
// docs/EP2_PLAN.md seccion 3.8) y arma un Error con esos datos adjuntos, para
// que los formularios puedan mostrar fieldErrors bajo cada campo (seccion 6.1).
async function errorDesdeRespuesta(response) {
  let cuerpo = null;
  try {
    cuerpo = await response.json();
  } catch {
    // Respuesta sin cuerpo JSON (p.ej. 401 del propio BFF): se sigue con el mensaje generico.
  }

  let mensaje = cuerpo?.message || `Error ${response.status} llamando a la API`;
  if (response.status === 403) {
    mensaje = 'No tienes permiso para esta acción.';
  }

  const error = new Error(mensaje);
  error.status = response.status;
  error.fieldErrors = cuerpo?.fieldErrors || [];
  error.body = cuerpo;
  return error;
}

async function leerCuerpo(response) {
  if (response.status === 204) return null;
  try {
    return await response.json();
  } catch {
    return null;
  }
}

// ── Modo noauth: no usa MSAL, no hay token, el backend debe correr con noauth ──
function useApiClientNoAuth() {
  async function callApi(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers });
    if (!response.ok) {
      throw await errorDesdeRespuesta(response);
    }
    return leerCuerpo(response);
  }
  return { callApi };
}

// ── Modo Azure: adjunta el Bearer token de MSAL a cada petición ───────────────
// Este hook es el equivalente al MsalInterceptor de Angular: antes de cada
// llamada al BFF, obtiene (o renueva) el access token en silencio y lo
// adjunta como header "Authorization: Bearer <token>".
function useApiClientMsal() {
  const { instance, accounts } = useMsal();

  async function getToken() {
    const account = accounts[0];
    // Sin sesion: se llama sin token (rutas publicas como el menu de la
    // tienda). Las rutas que si exigen JWT devuelven 401 igual, y el UI
    // (ProtectedRoute) ya evita que un usuario sin sesion llegue ahi.
    if (!account) return null;
    try {
      const response = await instance.acquireTokenSilent({ ...loginRequest, account });
      return response.accessToken;
    } catch (error) {
      if (error instanceof InteractionRequiredAuthError) {
        await instance.acquireTokenRedirect(loginRequest);
      }
      throw error;
    }
  }

  async function callApi(path, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    // Rutas /public/** (tienda, checkout) nunca exigen JWT: no hay que
    // intentar adjuntar un token aqui. Si un staff logueado navega a la
    // tienda y la renovacion silenciosa de SU token falla, acquireTokenRedirect
    // saca de la pagina a mitad del checkout (redirect de ventana completa)
    // sin ningun error visible, perdiendo el carrito con el pedido a medio
    // enviar — por eso antes "no pasaba nada" al pagar estando logueada.
    const esPublica = path.startsWith('/public/');
    if (!esPublica) {
      const token = await getToken();
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }
    }
    const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers });

    if (response.status === 401 && !esPublica) {
      // El token expiro o ya no es valido: se fuerza un nuevo login en vez de
      // dejar que la pagina se quede mostrando datos vacios silenciosamente
      // (ver docs/EP2_PLAN.md seccion 6.1).
      await instance.acquireTokenRedirect(loginRequest);
      throw await errorDesdeRespuesta(response);
    }

    if (!response.ok) {
      throw await errorDesdeRespuesta(response);
    }
    return leerCuerpo(response);
  }

  return { callApi };
}

// authDisabled es una constante de build → Vite elimina el código muerto.
export const useApiClient = authDisabled ? useApiClientNoAuth : useApiClientMsal;
