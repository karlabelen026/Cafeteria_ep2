import { useState } from 'react';
import { usePolling } from '../../../hooks/usePolling';
import { useApiClient } from '../../../services/apiClient';
import { useToasts } from '../../../context/ToastContext.jsx';
import Modal from '../../../components/Modal.jsx';
import CrudForm from '../../../components/CrudForm.jsx';

const CAMPOS = [
  { name: 'cantidad', label: 'Cantidad a ingresar', type: 'number', required: true, min: 0, step: 0.1 },
  { name: 'motivo', label: 'Motivo', type: 'text', required: true, placeholder: 'Compra a proveedor, ajuste...' },
];

// "Insumos críticos" (ver docs/EP2_PLAN.md seccion 6.2): lista de stock bajo
// (GET /api/inventario/alertas) + registrar una entrada de stock por insumo.
export default function InsumosCriticos() {
  const { callApi } = useApiClient();
  const toasts = useToasts();
  const [insumos, setInsumos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [modal, setModal] = useState(null);
  const [valores, setValores] = useState({ cantidad: '', motivo: '' });
  const [errores, setErrores] = useState([]);
  const [guardando, setGuardando] = useState(false);

  function cargar() {
    callApi('/inventario/alertas')
      .then((data) => {
        setInsumos(data || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar los insumos (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  }

  usePolling(cargar);

  function abrirModal(insumo) {
    setModal(insumo);
    setValores({ cantidad: '', motivo: '' });
    setErrores([]);
  }

  async function registrarEntrada() {
    setGuardando(true);
    setErrores([]);
    try {
      await callApi(`/inventario/${modal.id}/movimientos`, {
        method: 'POST',
        body: JSON.stringify({ tipo: 'ENTRADA', cantidad: valores.cantidad, motivo: valores.motivo }),
      });
      toasts.success(`Entrada registrada para ${modal.nombre}.`);
      setModal(null);
      cargar();
    } catch (err) {
      console.error(err);
      if (err.status === 400 && err.fieldErrors?.length) {
        setErrores(err.fieldErrors);
      } else {
        toasts.error(err.message || 'No se pudo registrar la entrada.');
      }
    } finally {
      setGuardando(false);
    }
  }

  if (cargando) {
    return (
      <div className="dash-card">
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando insumos...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="alert alert-error">{error}</div>;
  }

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Insumos críticos</h2>
          <p>Insumos con stock igual o por debajo del mínimo.</p>
        </div>
      </div>

      {insumos.length === 0 ? (
        <div className="state-block">
          <h3>Sin alertas de stock</h3>
          <p>Todos los insumos están sobre su mínimo.</p>
        </div>
      ) : (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Insumo</th>
                <th>Stock actual</th>
                <th>Stock mínimo</th>
                <th>Unidad</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {insumos.map((i) => (
                <tr key={i.id}>
                  <td>
                    <strong>{i.nombre}</strong>
                  </td>
                  <td>
                    <span className="badge badge--error">{i.stockActual}</span>
                  </td>
                  <td>{i.stockMinimo}</td>
                  <td>{i.unidadMedida}</td>
                  <td>
                    <button className="dash-icon-btn" onClick={() => abrirModal(i)}>
                      Registrar entrada
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal && (
        <Modal title={`Registrar entrada — ${modal.nombre}`} onClose={() => setModal(null)}>
          <CrudForm
            fields={CAMPOS}
            values={valores}
            onChange={setValores}
            errors={errores}
            onSubmit={registrarEntrada}
            onCancel={() => setModal(null)}
            submitting={guardando}
            submitLabel="Registrar"
          />
        </Modal>
      )}
    </div>
  );
}
