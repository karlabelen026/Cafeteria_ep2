import { useEffect, useRef } from 'react';

// Intervalo por defecto con el que las paginas del dashboard vuelven a pedir
// sus datos al backend. Asi lo que un rol crea o modifica (un insumo, el
// estado de un pedido, una alerta) lo ven los demas roles sin recargar.
export const REFRESCO_MS = 8000;

// Ejecuta "tarea" al montar y luego cada "intervaloMs", solo mientras la
// pestaña esta visible. Al volver a la pestaña refresca de inmediato.
export function usePolling(tarea, intervaloMs = REFRESCO_MS, deps = []) {
  const tareaRef = useRef(tarea);
  tareaRef.current = tarea;

  useEffect(() => {
    const ejecutar = () => {
      if (document.visibilityState === 'visible') tareaRef.current();
    };
    tareaRef.current();
    const intervalo = setInterval(ejecutar, intervaloMs);
    document.addEventListener('visibilitychange', ejecutar);
    return () => {
      clearInterval(intervalo);
      document.removeEventListener('visibilitychange', ejecutar);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervaloMs, ...deps]);
}
