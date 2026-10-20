import { useState } from 'react';
import { useApiClient } from '../../services/apiClient';
import { useToasts } from '../../context/ToastContext.jsx';
import { usePolling } from '../../hooks/usePolling';
import { kafkaUiUrl } from '../../utils/kafka';
import CrudForm from '../../components/CrudForm.jsx';

const REFRESCO_ALERTAS_MS = 4000;
const METODOS = ['DEBITO', 'CREDITO', 'EFECTIVO', 'TRANSFERENCIA'];

const CAMPOS_SIMULACION = [
  { name: 'clienteEmail', label: 'Email del cliente', type: 'email', required: true, placeholder: 'juan@mail.cl' },
  { name: 'monto', label: 'Monto (CLP)', type: 'number', required: true, min: 1, step: 1 },
  {
    name: 'metodoPago',
    label: 'Método de pago',
    type: 'select',
    required: true,
    options: METODOS.map((m) => ({ value: m, label: m })),
  },
  { name: 'aprobado', label: 'Pago aprobado (desmarca para simular un rechazo)', type: 'checkbox' },
];

const VALORES_INICIALES = { clienteEmail: 'juan@mail.cl', monto: 60000, metodoPago: 'CREDITO', aprobado: true };

const CLP = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

// Monitoreo antifraude en tiempo real (Fase 8, solo ADMIN): ms-pagos publica
// cada transaccion en Kafka, ms-antifraude la analiza con Kafka Streams y
// emite alertas al topic alertas-fraude. Aqui se ven esas alertas, el total
// gastado por cliente (Interactive Queries sobre el state store) y se pueden
// inyectar transacciones de prueba sin pasar por la tienda.
export default function Antifraude() {
  const { callApi } = useApiClient();
  const toasts = useToasts();

  const [alertas, setAlertas] = useState([]);
  const [totales, setTotales] = useState([]);
  const [errorAlertas, setErrorAlertas] = useState('');
  const [avisoTotales, setAvisoTotales] = useState('');
  const [cargando, setCargando] = useState(true);

  const [valores, setValores] = useState(VALORES_INICIALES);
  const [errores, setErrores] = useState([]);
  const [simulando, setSimulando] = useState(false);

  const [cantidadCarga, setCantidadCarga] = useState(10000);
  const [enCarga, setEnCarga] = useState(false);
  const [resultadoCarga, setResultadoCarga] = useState(null);

  usePolling(() => {
    callApi('/antifraude/alertas?cantidad=50')
      .then((data) => {
        setAlertas(data || []);
        setErrorAlertas('');
      })
      .catch((err) => {
        console.error(err);
        setErrorAlertas('No se pudieron cargar las alertas (¿está corriendo ms-antifraude?).');
      })
      .finally(() => setCargando(false));

    // 503 mientras Kafka Streams arranca o rebalancea: es un aviso, no un error.
    callApi('/antifraude/totales')
      .then((data) => {
        setTotales(data || []);
        setAvisoTotales('');
      })
      .catch((err) => setAvisoTotales(err.message || 'Kafka Streams todavía no está listo.'));
  }, REFRESCO_ALERTAS_MS);

  async function simular() {
    setSimulando(true);
    setErrores([]);
    try {
      await callApi('/antifraude/simular', {
        method: 'POST',
        body: JSON.stringify({ ...valores, monto: Number(valores.monto) }),
      });
      toasts.success('Transacción publicada en Kafka.');
    } catch (err) {
      if (err.status === 400 && err.fieldErrors?.length) setErrores(err.fieldErrors);
      else toasts.error(err.message || 'No se pudo publicar la transacción.');
    } finally {
      setSimulando(false);
    }
  }

  async function generarCarga() {
    setEnCarga(true);
    setResultadoCarga(null);
    try {
      const resultado = await callApi(`/antifraude/carga?cantidad=${cantidadCarga}`, { method: 'POST' });
      setResultadoCarga(resultado);
      toasts.success(`${resultado.cantidad} transacciones publicadas.`);
    } catch (err) {
      toasts.error(err.message || 'No se pudo generar la carga.');
    } finally {
      setEnCarga(false);
    }
  }

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Antifraude (Kafka)</h2>
          <p>Cada pago se publica en Kafka y Kafka Streams lo analiza en tiempo real.</p>
        </div>
        <a className="dash-btn" href={kafkaUiUrl} target="_blank" rel="noopener noreferrer">
          Abrir Kafka UI ↗
        </a>
      </div>

      <div className="dash__row">
        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Simular una transacción</h3>
          </div>
          <CrudForm
            fields={CAMPOS_SIMULACION}
            values={valores}
            onChange={setValores}
            errors={errores}
            onSubmit={simular}
            onCancel={() => setValores(VALORES_INICIALES)}
            submitting={simulando}
            submitLabel="Publicar en Kafka"
          />
          <p className="dash-empty">
            Sobre 50.000 CLP genera alerta ALTA; un rechazo genera MEDIA; menos de 10 CLP se descarta; más de 5
            pagos del mismo cliente en 5 minutos genera "Ráfaga de pagos".
          </p>
        </div>

        <div className="dash-card">
          <div className="dash-card__header">
            <h3>Prueba de carga</h3>
          </div>
          <div className="dash-field">
            <label htmlFor="cantidad-carga">Cantidad de transacciones</label>
            <input
              id="cantidad-carga"
              type="number"
              min={1}
              max={100000}
              value={cantidadCarga}
              onChange={(e) => setCantidadCarga(e.target.valueAsNumber || 1)}
            />
          </div>
          <button className="dash-btn" disabled={enCarga} onClick={generarCarga}>
            {enCarga ? 'Publicando...' : 'Generar carga'}
          </button>
          {resultadoCarga && (
            <p className="dash-empty">
              {resultadoCarga.cantidad.toLocaleString('es-CL')} transacciones en{' '}
              {resultadoCarga.milisegundos.toLocaleString('es-CL')} ms (
              {resultadoCarga.mensajesPorSegundo.toLocaleString('es-CL')} mensajes/s).
            </p>
          )}
        </div>
      </div>

      <div className="dash-card" style={{ marginBottom: 16 }}>
        <div className="dash-card__header">
          <h3>Alertas de fraude en tiempo real</h3>
          <span className="dash-card__sub">Topic alertas-fraude · se actualiza cada 4 segundos</span>
        </div>
        {errorAlertas && <div className="alert alert-error">{errorAlertas}</div>}
        {cargando && (
          <div className="state-block">
            <div className="spinner" />
          </div>
        )}
        {!cargando && !errorAlertas && alertas.length === 0 && (
          <p className="dash-empty">Sin alertas todavía. Simula una transacción de 60.000 CLP.</p>
        )}
        {alertas.length > 0 && (
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>Severidad</th>
                  <th>Cliente</th>
                  <th>Motivo</th>
                  <th>Monto</th>
                  <th>Detectada</th>
                </tr>
              </thead>
              <tbody>
                {alertas.map((a) => (
                  <tr key={a.eventId}>
                    <td>
                      <span className={`badge ${a.severidad === 'ALTA' ? 'badge--error' : 'badge--neutral'}`}>
                        {a.severidad}
                      </span>
                    </td>
                    <td>{a.cliente}</td>
                    <td>{a.motivo}</td>
                    <td>{a.monto > 0 ? `${CLP.format(a.monto)} (${USD.format(a.montoUsd)})` : '—'}</td>
                    <td>{new Date(a.detectadaEn).toLocaleTimeString('es-CL')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="dash-card">
        <div className="dash-card__header">
          <h3>Total gastado por cliente</h3>
          <span className="dash-card__sub">State store total-por-cliente (Interactive Queries)</span>
        </div>
        {avisoTotales && <div className="alert alert-warning">{avisoTotales}</div>}
        {!avisoTotales && totales.length === 0 && <p className="dash-empty">Sin transacciones aprobadas todavía.</p>}
        {totales.length > 0 && (
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Cliente</th>
                  <th>Total CLP</th>
                  <th>Total USD</th>
                  <th>Transacciones</th>
                </tr>
              </thead>
              <tbody>
                {totales.map((t, i) => (
                  <tr key={t.cliente}>
                    <td>{i + 1}</td>
                    <td>
                      <strong>{t.cliente}</strong>
                    </td>
                    <td>{CLP.format(t.totalClp)}</td>
                    <td>{USD.format(t.totalUsd)}</td>
                    <td>{t.cantidadTransacciones}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
