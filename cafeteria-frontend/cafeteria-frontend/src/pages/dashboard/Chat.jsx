import { useEffect, useRef, useState } from 'react';
import { useApiClient } from '../../services/apiClient';
import { useAuthProfile } from '../../hooks/useUserRole';

const INTERVALO_POLLING_MS = 3000;

// Chat interno del equipo: un solo canal, visible para todos los roles. No
// confundir con "Mensajería" (panel de administración de RabbitMQ, solo
// ADMIN) — esto es comunicación entre personas, no entre servicios. Cada
// mensaje enviado viaja por RabbitMQ de verdad (POST publica el evento,
// GET lee lo que ya quedo persistido al consumirlo de vuelta, ver
// ChatController/ChatListener en ms-notificaciones).
export default function Chat() {
  const { callApi } = useApiClient();
  const { nombre, role } = useAuthProfile();
  const [mensajes, setMensajes] = useState([]);
  const [pendientes, setPendientes] = useState([]);
  const [texto, setTexto] = useState('');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const ultimoTimestampRef = useRef(null);
  const listaRef = useRef(null);

  useEffect(() => {
    let cancelado = false;

    function cargar() {
      const desde = ultimoTimestampRef.current;
      const query = desde ? `?desde=${encodeURIComponent(desde)}` : '';
      callApi(`/notificaciones/mensajes${query}`)
        .then((nuevos) => {
          if (cancelado || !nuevos || nuevos.length === 0) return;
          ultimoTimestampRef.current = nuevos[nuevos.length - 1].creadoEn;
          setMensajes((prev) => [...prev, ...nuevos]);
          // Los que ya llegaron de verdad ya no hace falta mostrarlos como
          // "enviando..." (ver handleEnviar): se identifican por autor+texto.
          setPendientes((prev) =>
            prev.filter((p) => !nuevos.some((n) => n.autor === p.autor && n.texto === p.texto)),
          );
        })
        .catch((err) => {
          if (!cancelado) {
            console.error(err);
            setError('No se pudo cargar el chat.');
          }
        })
        .finally(() => !cancelado && setCargando(false));
    }

    cargar();
    const intervalo = setInterval(cargar, INTERVALO_POLLING_MS);
    return () => {
      cancelado = true;
      clearInterval(intervalo);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    listaRef.current?.scrollTo({ top: listaRef.current.scrollHeight, behavior: 'smooth' });
  }, [mensajes, pendientes]);

  const handleEnviar = async (e) => {
    e.preventDefault();
    const limpio = texto.trim();
    if (!limpio) return;

    const autor = nombre || 'Equipo';
    const rolActual = role || 'STAFF';
    setPendientes((prev) => [...prev, { autor, rol: rolActual, texto: limpio, pendiente: true }]);
    setTexto('');

    try {
      await callApi('/notificaciones/mensajes', {
        method: 'POST',
        body: JSON.stringify({ autor, rol: rolActual, texto: limpio }),
      });
      // No hace falta hacer nada mas aqui: el proximo polling (cada 3s) va a
      // traer el mensaje ya persistido y reemplazar el "pendiente" de arriba.
    } catch (err) {
      console.error(err);
      setError('No se pudo enviar el mensaje.');
      setPendientes((prev) => prev.filter((p) => !(p.autor === autor && p.texto === limpio)));
    }
  };

  const iniciales = (nombreCompleto) =>
    (nombreCompleto || 'E')
      .split(' ')
      .map((p) => p[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();

  return (
    <div className="chat-page">
      <div className="dash__greeting">
        <h1>Chat del equipo</h1>
        <p>Un solo canal para todo el staff — baristas, cajeros, bodega y administración.</p>
      </div>

      <div className="chat-card">
        <div className="chat-card__lista" ref={listaRef}>
          {cargando && <div className="chat-card__vacio">Cargando el chat...</div>}
          {!cargando && mensajes.length === 0 && pendientes.length === 0 && (
            <div className="chat-card__vacio">Todavía no hay mensajes. ¡Escribe el primero!</div>
          )}
          {[...mensajes, ...pendientes].map((m, i) => (
            <div
              key={m.id ?? `pendiente-${i}`}
              className={`chat-msg${m.autor === nombre ? ' chat-msg--propio' : ''}`}
            >
              <span className="chat-msg__avatar">{iniciales(m.autor)}</span>
              <div className="chat-msg__cuerpo">
                <div className="chat-msg__meta">
                  <strong>{m.autor}</strong>
                  <span className="chat-msg__rol">{m.rol}</span>
                </div>
                <p>{m.texto}</p>
                {m.pendiente && <span className="chat-msg__pendiente">Enviando...</span>}
              </div>
            </div>
          ))}
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        <form className="chat-card__form" onSubmit={handleEnviar}>
          <input
            type="text"
            placeholder="Escribe un mensaje para el equipo..."
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            maxLength={500}
          />
          <button type="submit" className="btn btn-primary" disabled={!texto.trim()}>
            Enviar
          </button>
        </form>
      </div>
    </div>
  );
}
