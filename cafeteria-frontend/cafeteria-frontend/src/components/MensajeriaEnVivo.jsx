import { useEffect, useRef, useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { usePolling } from '../hooks/usePolling';
import { useApiClient } from '../services/apiClient';

export const REFRESCO_EN_VIVO_MS = 4000;

const COLOR_PUBLICADOS = '#b85c1e';
const COLOR_ENTREGADOS = '#1f78b4';
const PUNTOS_GRAFICO = 30;
const MAX_MOVIMIENTOS = 8;

const formatoNumero = new Intl.NumberFormat('es-CL');

function horaActual() {
  return new Date().toLocaleTimeString('es-CL');
}

function formatoMemoria(bytes) {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
  return `${Math.round(bytes / 1024 ** 2)} MB`;
}

function formatoDuracion(ms) {
  const minutos = Math.floor(ms / 60000);
  if (minutos < 60) return `${minutos} min`;
  return `${Math.floor(minutos / 60)} h ${minutos % 60} min`;
}

// El "key" con el valor reinicia la animacion cada vez que el numero cambia.
function Contador({ etiqueta, valor, detalle }) {
  return (
    <div className="dash-card">
      <p className="dash-kpi__label">{etiqueta}</p>
      <p key={valor} className="dash-kpi__value dash-pulso">
        {valor}
      </p>
      <span className="dash-card__sub">{detalle}</span>
    </div>
  );
}

function TarjetaNodo({ nombre, activo, detalles }) {
  return (
    <div className={`dash-nodo ${activo ? '' : 'dash-nodo--caido'}`}>
      <div className="dash-nodo__cabecera">
        <strong>{nombre}</strong>
        <span className={`dash-nodo__estado ${activo ? 'dash-nodo__estado--ok' : 'dash-nodo__estado--error'}`}>
          {activo ? '● Activo' : '✕ Caído'}
        </span>
      </div>
      {detalles.map((d) => (
        <p key={d} className="dash-nodo__detalle">
          {d}
        </p>
      ))}
    </div>
  );
}

function detallesRabbit(nodo) {
  if (!nodo.running) return ['Sin conexión con el cluster'];
  const detalles = [
    `Líder de ${nodo.colasLider} colas`,
    `Memoria: ${formatoMemoria(nodo.memoriaUsada)} de ${formatoMemoria(nodo.memoriaLimite)}`,
    `Encendido hace ${formatoDuracion(nodo.uptimeMs)}`,
  ];
  if (nodo.alarmaMemoria) detalles.push('⚠ Alarma de memoria');
  if (nodo.alarmaDisco) detalles.push('⚠ Alarma de disco');
  return detalles;
}

// Estado en vivo de la mensajeria: nodos de RabbitMQ y Kafka, y el movimiento
// real de mensajes. Las colas casi siempre estan en 0 porque los consumidores
// procesan al instante, asi que lo que se muestra son contadores acumulados y
// tasas por segundo, los mismos que usa el panel propio de RabbitMQ.
export default function MensajeriaEnVivo({ colas }) {
  const { callApi } = useApiClient();

  const [resumen, setResumen] = useState(null);
  const [nodosRabbit, setNodosRabbit] = useState([]);
  const [brokers, setBrokers] = useState([]);
  const [errorKafka, setErrorKafka] = useState(false);
  const [serie, setSerie] = useState([]);
  const [movimientos, setMovimientos] = useState([]);

  const colasAnteriores = useRef(null);
  // Kafka solo informa los brokers vivos: recordar los ya vistos permite
  // mostrar como caido al que deja de aparecer.
  const brokersVistos = useRef(new Map());

  usePolling(() => {
    callApi('/rabbitmq/actividad')
      .then((r) => {
        setResumen(r);
        setSerie((actual) =>
          [
            ...actual,
            {
              hora: horaActual(),
              publicados: r.actividad.publicadosPorSeg,
              entregados: r.actividad.entregadosPorSeg,
            },
          ].slice(-PUNTOS_GRAFICO),
        );
      })
      .catch((err) => console.error(err));

    callApi('/rabbitmq/cluster')
      .then((c) => setNodosRabbit(c?.nodos || []))
      .catch((err) => console.error(err));

    callApi('/kafka/cluster')
      .then((c) => {
        const vivos = new Set((c?.brokers || []).map((b) => b.id));
        (c?.brokers || []).forEach((b) => brokersVistos.current.set(b.id, b));
        setBrokers(
          [...brokersVistos.current.values()]
            .sort((a, b) => a.id - b.id)
            .map((b) => ({ ...b, activo: vivos.has(b.id) })),
        );
        setErrorKafka(false);
      })
      .catch((err) => {
        console.error(err);
        setErrorKafka(true);
      });
  }, REFRESCO_EN_VIVO_MS);

  // Compara cada cola con la lectura anterior para registrar lo que se movio.
  useEffect(() => {
    if (colas.length === 0) return;
    const anteriores = colasAnteriores.current;
    colasAnteriores.current = new Map(colas.map((c) => [c.name, c.actividad]));
    if (!anteriores) return;

    const nuevos = colas.flatMap((c) => {
      const previo = anteriores.get(c.name);
      if (!previo) return [];
      const publicados = c.actividad.publicados - previo.publicados;
      const entregados = c.actividad.entregados - previo.entregados;
      if (publicados <= 0 && entregados <= 0) return [];
      return [{ id: `${c.name}-${c.actividad.publicados}-${c.actividad.entregados}`, hora: horaActual(), cola: c.name, publicados, entregados }];
    });
    if (nuevos.length > 0) {
      setMovimientos((actual) => [...nuevos, ...actual].slice(0, MAX_MOVIMIENTOS));
    }
  }, [colas]);

  const actividad = resumen?.actividad;
  const nodosRabbitActivos = nodosRabbit.filter((n) => n.running).length;
  const brokersActivos = brokers.filter((b) => b.activo).length;

  return (
    <>
      <div className="dash-card dash-en-vivo__seccion">
        <div className="dash-card__header">
          <div>
            <h3>Nodos de RabbitMQ</h3>
            <span className="dash-card__sub">
              {nodosRabbitActivos} de {nodosRabbit.length} activos · colas quorum replicadas entre los nodos
            </span>
          </div>
        </div>
        <div className="dash-nodos">
          {nodosRabbit.map((n) => (
            <TarjetaNodo key={n.name} nombre={n.name.replace('rabbit@', '')} activo={n.running} detalles={detallesRabbit(n)} />
          ))}
        </div>
      </div>

      <div className="dash-card dash-en-vivo__seccion">
        <div className="dash-card__header">
          <div>
            <h3>Brokers de Kafka</h3>
            <span className="dash-card__sub">
              {brokersActivos} de {brokers.length} activos · topics con 3 réplicas
            </span>
          </div>
        </div>
        {errorKafka && <div className="alert alert-error">No se pudo consultar el estado de Kafka.</div>}
        <div className="dash-nodos">
          {brokers.map((b) => (
            <TarjetaNodo
              key={b.id}
              nombre={`kafka-${b.id}`}
              activo={b.activo}
              detalles={b.activo ? [b.controller ? 'Controlador del cluster' : 'Broker'] : ['Sin conexión con el cluster']}
            />
          ))}
        </div>
      </div>

      <div className="dash__kpi-row">
        <Contador
          etiqueta="Mensajes publicados"
          valor={formatoNumero.format(actividad?.publicados ?? 0)}
          detalle={`${(actividad?.publicadosPorSeg ?? 0).toFixed(1)} por segundo`}
        />
        <Contador
          etiqueta="Mensajes entregados"
          valor={formatoNumero.format(actividad?.entregados ?? 0)}
          detalle={`${(actividad?.entregadosPorSeg ?? 0).toFixed(1)} por segundo`}
        />
        <Contador
          etiqueta="Mensajes confirmados (ACK)"
          valor={formatoNumero.format(actividad?.confirmados ?? 0)}
          detalle={`${(actividad?.confirmadosPorSeg ?? 0).toFixed(1)} por segundo`}
        />
        <Contador
          etiqueta="Consumidores conectados"
          valor={formatoNumero.format(resumen?.consumidores ?? 0)}
          detalle={`${resumen?.colas ?? 0} colas · ${resumen?.conexiones ?? 0} conexiones`}
        />
      </div>

      <div className="dash-en-vivo__fila">
        <div className="dash-card">
          <div className="dash-card__header">
            <div>
              <h3>Mensajes por segundo</h3>
              <span className="dash-card__sub">Últimos 2 minutos · se actualiza cada 4 segundos</span>
            </div>
            <div className="dash-legend">
              <span><i className="dash-legend__dot" style={{ background: COLOR_PUBLICADOS }} />Publicados</span>
              <span><i className="dash-legend__dot" style={{ background: COLOR_ENTREGADOS }} />Entregados</span>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={serie}>
              <CartesianGrid vertical={false} stroke="#efe4d8" />
              <XAxis dataKey="hora" tick={{ fontSize: 11 }} minTickGap={50} />
              <YAxis allowDecimals tick={{ fontSize: 11 }} width={32} domain={[0, (max) => Math.max(1, Math.ceil(max))]} />
              <Tooltip formatter={(v) => v.toFixed(2)} />
              <Line name="Publicados" type="monotone" dataKey="publicados" stroke={COLOR_PUBLICADOS} strokeWidth={2} dot={false} isAnimationActive={false} />
              <Line name="Entregados" type="monotone" dataKey="entregados" stroke={COLOR_ENTREGADOS} strokeWidth={2} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="dash-card">
          <div className="dash-card__header">
            <div>
              <h3>Últimos movimientos</h3>
              <span className="dash-card__sub">Desde que abriste esta página</span>
            </div>
          </div>
          {movimientos.length === 0 ? (
            <p className="dash-empty">Sin movimientos todavía. Aparecerán solos cuando se cree un pedido o se pague.</p>
          ) : (
            <ul className="dash-movimientos">
              {movimientos.map((m) => (
                <li key={m.id}>
                  <span className="dash-movimientos__hora">{m.hora}</span>
                  <span className="dash-movimientos__cola">{m.cola}</span>
                  <span className="dash-movimientos__cambio">
                    {m.publicados > 0 && `+${m.publicados} publ.`} {m.entregados > 0 && `+${m.entregados} entr.`}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  );
}
