import { useCallback, useEffect, useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { loginRequest, apiBaseUrl } from '../auth/authConfig';

const authDisabled = import.meta.env.VITE_AUTH_DISABLED === 'true';
export const DEMO_ROLE_KEY = 'cg360_demo_role';
const ACTIVE_ROLE_KEY = 'cg360_active_role';

// Orden de preferencia cuando no hay un perfil activo guardado todavia.
const PRIORIDAD_ROLES = ['ADMIN', 'GERENTE', 'BARISTA', 'CAJERO', 'BODEGUERO'];

function elegirRolPorDefecto(roles) {
  for (const candidato of PRIORIDAD_ROLES) {
    if (roles.includes(candidato)) return candidato;
  }
  return roles[0] || null;
}

// Fuente de verdad del rol = el backend. El BFF ya valido el JWT (firma,
// issuer, audience) y expone en GET /api/me exactamente los roles que leyo
// del claim "roles" del token — el frontend NUNCA decodifica el JWT por su
// cuenta (ver docs/EP2_PLAN.md seccion 4).
//
// En el perfil local sin Azure (VITE_AUTH_DISABLED=true, backend en noauth)
// no hay token del que leer roles, asi que el perfil se elige a mano con el
// selector "Perfil" de la barra superior y se recuerda en localStorage.
//
// Si el usuario tiene mas de un rol asignado en Azure, puede elegir cual usar
// como "perfil activo"; esa eleccion se recuerda en localStorage mientras
// siga siendo uno de los roles que trae el token.
export function useAuthProfile() {
  const { instance, accounts } = useMsal();
  const [roles, setRoles] = useState(() => (authDisabled ? [localStorage.getItem(DEMO_ROLE_KEY) || 'ADMIN'] : []));
  const [activeRole, setActiveRoleState] = useState(() =>
    authDisabled ? localStorage.getItem(DEMO_ROLE_KEY) || 'ADMIN' : localStorage.getItem(ACTIVE_ROLE_KEY)
  );
  const [perfil, setPerfil] = useState(() =>
    authDisabled ? { nombre: 'Equipo', email: '' } : { nombre: '', email: '' }
  );

  useEffect(() => {
    if (authDisabled) return;
    const account = accounts[0];
    if (!account) return;

    let cancelled = false;
    instance
      .acquireTokenSilent({ ...loginRequest, account })
      .then((response) =>
        fetch(`${apiBaseUrl}/me`, {
          headers: { Authorization: `Bearer ${response.accessToken}` },
        })
      )
      .then((response) => (response.ok ? response.json() : { roles: [] }))
      .then((data) => {
        if (cancelled) return;
        const rolesRecibidos = data.roles || [];
        setRoles(rolesRecibidos);
        setPerfil({ nombre: data.nombre || account.name || '', email: data.email || account.username || '' });
        setActiveRoleState((actual) =>
          actual && rolesRecibidos.includes(actual) ? actual : elegirRolPorDefecto(rolesRecibidos)
        );
      })
      .catch(() => {
        if (!cancelled) {
          setRoles([]);
          setActiveRoleState(null);
        }
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instance, accounts]);

  const setActiveRole = useCallback((rol) => {
    if (authDisabled) {
      setDemoRole(rol);
      window.location.reload();
      return;
    }
    localStorage.setItem(ACTIVE_ROLE_KEY, rol);
    setActiveRoleState(rol);
  }, []);

  return { roles, role: activeRole, setActiveRole, nombre: perfil.nombre, email: perfil.email };
}

// Atajo para los componentes que solo necesitan el rol activo (p.ej. para
// filtrar que puede ver/hacer, no para dibujar el selector de perfil).
export function useUserRole() {
  return useAuthProfile().role;
}

export function setDemoRole(role) {
  localStorage.setItem(DEMO_ROLE_KEY, role);
}
