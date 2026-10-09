import { useState } from 'react';
import { usePolling } from '../../hooks/usePolling';
import { rabbitDashboardUrl } from '../../utils/rabbitmq';
import { useApiClient } from '../../services/apiClient';
import { useToasts } from '../../context/ToastContext.jsx';
import Modal from '../../components/Modal.jsx';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import CrudForm from '../../components/CrudForm.jsx';

const CAMPOS_COLA = [
  { name: 'name', label: 'Nombre', type: 'text', required: true, placeholder: 'mi-cola-demo' },
  { name: 'ttlMs', label: 'TTL (ms, opcional)', type: 'number', min: 1000 },
  { name: 'maxLength', label: 'Largo máximo (opcional)', type: 'number', min: 1 },
  { name: 'deliveryLimit', label: 'Límite de reintentos (opcional)', type: 'number', min: 1, max: 20 },
];

const CAMPOS_EXCHANGE = [
  { name: 'name', label: 'Nombre', type: 'text', required: true, placeholder: 'mi-exchange-demo' },
  {
    name: 'type',
    label: 'Tipo',
    type: 'select',
    required: true,
    options: ['direct', 'topic', 'fanout', 'headers'].map((t) => ({ value: t, label: t })),
  },
];

// Administracion de RabbitMQ (ver docs/EP2_PLAN.md seccion 3.8 y 6.2): crear
// y eliminar colas, exchanges y bindings usando ms-rabbitmq-admin (solo
// ADMIN). Los 4 exchanges y las 16 colas del sistema estan protegidos: el
// backend responde 409 si se intenta eliminarlos/purgarlos.
export default function Mensajeria() {
  const { callApi } = useApiClient();
  const toasts = useToasts();

  const [colas, setColas] = useState([]);
  const [exchanges, setExchanges] = useState([]);
  const [bindings, setBindings] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const [modalCola, setModalCola] = useState(false);
  const [modalExchange, setModalExchange] = useState(false);
  const [modalBinding, setModalBinding] = useState(false);
  const [valoresCola, setValoresCola] = useState({ name: '' });
  const [valoresExchange, setValoresExchange] = useState({ name: '', type: 'direct' });
  const [valoresBinding, setValoresBinding] = useState({ queue: '', exchange: '', routingKey: '' });
  const [erroresCola, setErroresCola] = useState([]);
  const [erroresExchange, setErroresExchange] = useState([]);
  const [erroresBinding, setErroresBinding] = useState([]);
  const [guardando, setGuardando] = useState(false);

  const [aEliminar, setAEliminar] = useState(null); // { tipo, nombre } o { tipo:'binding', binding }
  const [eliminando, setEliminando] = useState(false);

  // Se refresca solo: los contadores de mensajes y consumidores de cada cola
  // se mueven mientras el sistema trabaja.
  function cargar() {
    Promise.all([callApi('/rabbitmq/queues'), callApi('/rabbitmq/exchanges'), callApi('/rabbitmq/bindings')])
      .then(([q, e, b]) => {
        setColas(q || []);
        setExchanges(e || []);
        setBindings(b || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudo cargar el estado de RabbitMQ (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  }

  usePolling(cargar);

  async function crearCola() {
    setGuardando(true);
    setErroresCola([]);
    try {
      await callApi('/rabbitmq/queues', {
        method: 'POST',
        body: JSON.stringify({
          name: valoresCola.name,
          ttlMs: valoresCola.ttlMs || null,
          maxLength: valoresCola.maxLength || null,
          deliveryLimit: valoresCola.deliveryLimit || null,
        }),
      });
      toasts.success('Cola creada.');
      setModalCola(false);
      cargar();
    } catch (err) {
      if (err.status === 400 && err.fieldErrors?.length) setErroresCola(err.fieldErrors);
      else toasts.error(err.message || 'No se pudo crear la cola.');
    } finally {
      setGuardando(false);
    }
  }

  async function crearExchange() {
    setGuardando(true);
    setErroresExchange([]);
    try {
      await callApi('/rabbitmq/exchanges', { method: 'POST', body: JSON.stringify(valoresExchange) });
      toasts.success('Exchange creado.');
      setModalExchange(false);
      cargar();
    } catch (err) {
      if (err.status === 400 && err.fieldErrors?.length) setErroresExchange(err.fieldErrors);
      else toasts.error(err.message || 'No se pudo crear el exchange.');
    } finally {
      setGuardando(false);
    }
  }

  async function crearBinding() {
    setGuardando(true);
    setErroresBinding([]);
    try {
      await callApi('/rabbitmq/bindings', { method: 'POST', body: JSON.stringify(valoresBinding) });
      toasts.success('Binding creado.');
      setModalBinding(false);
      cargar();
    } catch (err) {
      if (err.status === 400 && err.fieldErrors?.length) setErroresBinding(err.fieldErrors);
      else toasts.error(err.message || 'No se pudo crear el binding.');
    } finally {
      setGuardando(false);
    }
  }

  async function confirmarEliminar() {
    setEliminando(true);
    try {
      if (aEliminar.tipo === 'cola') {
        await callApi(`/rabbitmq/queues/${encodeURIComponent(aEliminar.nombre)}`, { method: 'DELETE' });
      } else if (aEliminar.tipo === 'exchange') {
        await callApi(`/rabbitmq/exchanges/${encodeURIComponent(aEliminar.nombre)}`, { method: 'DELETE' });
      } else {
        await callApi('/rabbitmq/bindings', { method: 'DELETE', body: JSON.stringify(aEliminar.binding) });
      }
      toasts.success('Eliminado.');
      setAEliminar(null);
      cargar();
    } catch (err) {
      console.error(err);
      toasts.error(err.message || 'No se pudo eliminar (¿es un recurso protegido del sistema?).');
    } finally {
      setEliminando(false);
    }
  }

  if (cargando) {
    return (
      <div className="dash-card">
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando RabbitMQ...</p>
        </div>
      </div>
    );
  }

  if (error && colas.length === 0 && exchanges.length === 0) {
    return (
      <div>
        <div className="alert alert-error">{error}</div>
        <button className="dash-btn" onClick={cargar}>
          Reintentar
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Mensajería</h2>
          <p>Crear y eliminar colas, exchanges y bindings de RabbitMQ (ms-rabbitmq-admin).</p>
        </div>
        <a className="dash-btn" href={rabbitDashboardUrl} target="_blank" rel="noopener noreferrer">
          Abrir dashboard de RabbitMQ ↗
        </a>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="dash-card" style={{ marginBottom: 16 }}>
        <div className="dash-card__header">
          <h3>Colas ({colas.length})</h3>
          <button className="dash-btn" onClick={() => { setValoresCola({ name: '' }); setErroresCola([]); setModalCola(true); }}>
            + Crear cola
          </button>
        </div>
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Listos</th>
                <th>No confirmados</th>
                <th>Consumidores</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {colas.map((c) => (
                <tr key={c.name}>
                  <td>{c.name}</td>
                  <td>{c.messagesReady}</td>
                  <td>{c.messagesUnacknowledged}</td>
                  <td>{c.consumers}</td>
                  <td>
                    <button
                      className="dash-icon-btn dash-icon-btn--danger"
                      onClick={() => setAEliminar({ tipo: 'cola', nombre: c.name })}
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="dash-card" style={{ marginBottom: 16 }}>
        <div className="dash-card__header">
          <h3>Exchanges ({exchanges.length})</h3>
          <button
            className="dash-btn"
            onClick={() => { setValoresExchange({ name: '', type: 'direct' }); setErroresExchange([]); setModalExchange(true); }}
          >
            + Crear exchange
          </button>
        </div>
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Tipo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {exchanges.map((e) => (
                <tr key={e.name}>
                  <td>{e.name}</td>
                  <td>{e.type}</td>
                  <td>
                    <button
                      className="dash-icon-btn dash-icon-btn--danger"
                      onClick={() => setAEliminar({ tipo: 'exchange', nombre: e.name })}
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="dash-card">
        <div className="dash-card__header">
          <h3>Bindings ({bindings.length})</h3>
          <button
            className="dash-btn"
            onClick={() => {
              setValoresBinding({ queue: '', exchange: '', routingKey: '' });
              setErroresBinding([]);
              setModalBinding(true);
            }}
          >
            + Crear binding
          </button>
        </div>
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Exchange</th>
                <th>Destino</th>
                <th>Routing key</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {bindings
                .filter((b) => b.destination)
                .map((b, idx) => (
                  <tr key={`${b.source}-${b.destination}-${b.routingKey}-${idx}`}>
                    <td>{b.source || '(default)'}</td>
                    <td>{b.destination}</td>
                    <td>{b.routingKey || '—'}</td>
                    <td>
                      {b.source && (
                        <button
                          className="dash-icon-btn dash-icon-btn--danger"
                          onClick={() =>
                            setAEliminar({
                              tipo: 'binding',
                              nombre: `${b.source} → ${b.destination}`,
                              binding: { queue: b.destination, exchange: b.source, routingKey: b.routingKey },
                            })
                          }
                        >
                          Eliminar
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </div>

      {modalCola && (
        <Modal title="Nueva cola" onClose={() => setModalCola(false)}>
          <CrudForm
            fields={CAMPOS_COLA}
            values={valoresCola}
            onChange={setValoresCola}
            errors={erroresCola}
            onSubmit={crearCola}
            onCancel={() => setModalCola(false)}
            submitting={guardando}
            submitLabel="Crear"
          />
        </Modal>
      )}

      {modalExchange && (
        <Modal title="Nuevo exchange" onClose={() => setModalExchange(false)}>
          <CrudForm
            fields={CAMPOS_EXCHANGE}
            values={valoresExchange}
            onChange={setValoresExchange}
            errors={erroresExchange}
            onSubmit={crearExchange}
            onCancel={() => setModalExchange(false)}
            submitting={guardando}
            submitLabel="Crear"
          />
        </Modal>
      )}

      {modalBinding && (
        <Modal title="Nuevo binding" onClose={() => setModalBinding(false)}>
          <CrudForm
            fields={[
              {
                name: 'exchange',
                label: 'Exchange',
                type: 'select',
                required: true,
                options: exchanges.map((e) => ({ value: e.name, label: e.name })),
              },
              {
                name: 'queue',
                label: 'Cola',
                type: 'select',
                required: true,
                options: colas.map((c) => ({ value: c.name, label: c.name })),
              },
              { name: 'routingKey', label: 'Routing key (opcional)', type: 'text', placeholder: 'pedido.#' },
            ]}
            values={valoresBinding}
            onChange={setValoresBinding}
            errors={erroresBinding}
            onSubmit={crearBinding}
            onCancel={() => setModalBinding(false)}
            submitting={guardando}
            submitLabel="Crear"
          />
        </Modal>
      )}

      {aEliminar && (
        <ConfirmDialog
          title="Eliminar recurso"
          message={`¿Seguro que quieres eliminar "${aEliminar.nombre}"? Si es un recurso del sistema, el backend lo va a rechazar.`}
          onConfirm={confirmarEliminar}
          onCancel={() => setAEliminar(null)}
          loading={eliminando}
        />
      )}
    </div>
  );
}
