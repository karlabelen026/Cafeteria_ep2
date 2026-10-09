import { useMemo, useState } from 'react';
import { usePolling } from '../hooks/usePolling';
import { useApiClient } from '../services/apiClient';
import { useToasts } from '../context/ToastContext.jsx';
import Modal from './Modal.jsx';
import ConfirmDialog from './ConfirmDialog.jsx';
import CrudForm from './CrudForm.jsx';

const TAMANO_PAGINA = 10;

// Pagina CRUD generica (ver docs/EP2_PLAN.md seccion 6.2): tabla con
// busqueda y paginacion (en el cliente: el backend no pagina, devuelve la
// lista completa), modal de crear/editar con validacion (fieldErrors del
// backend bajo cada campo), confirmacion al eliminar, toasts, y estados de
// carga/vacío/error. Los botones de crear/editar/eliminar se ocultan segun
// el rol (lo decide la pagina que usa CrudPage, via puedeCrear/Editar/Eliminar).
export default function CrudPage({
  title,
  subtitle,
  endpoint,
  columns,
  fields,
  idField = 'id',
  searchKeys = [],
  puedeCrear = false,
  puedeEditar = false,
  puedeEliminar = false,
  valoresPorDefecto = {},
  aValoresFormulario = (item) => item,
  aCuerpoPeticion = (values) => values,
  accionesExtra,
  nombreSingular = 'elemento',
}) {
  const { callApi } = useApiClient();
  const toasts = useToasts();

  const [items, setItems] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [pagina, setPagina] = useState(1);

  const [modal, setModal] = useState(null); // null | 'crear' | item a editar
  const [valoresForm, setValoresForm] = useState(valoresPorDefecto);
  const [erroresForm, setErroresForm] = useState([]);
  const [guardando, setGuardando] = useState(false);

  const [aEliminar, setAEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

  // Se vuelve a pedir la lista cada pocos segundos (sin spinner): lo que
  // otro rol agrega, edita o elimina aparece aqui sin recargar la pagina.
  function cargar() {
    callApi(endpoint)
      .then((data) => {
        setItems(data || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudo cargar la información (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  }

  usePolling(cargar, undefined, [endpoint]);

  const filtrados = useMemo(() => {
    if (!busqueda.trim() || searchKeys.length === 0) return items;
    const q = busqueda.trim().toLowerCase();
    return items.filter((item) => searchKeys.some((k) => String(item[k] ?? '').toLowerCase().includes(q)));
  }, [items, busqueda, searchKeys]);

  const totalPaginas = Math.max(1, Math.ceil(filtrados.length / TAMANO_PAGINA));
  const paginaSegura = Math.min(pagina, totalPaginas);
  const paginaItems = filtrados.slice((paginaSegura - 1) * TAMANO_PAGINA, paginaSegura * TAMANO_PAGINA);

  function abrirCrear() {
    setValoresForm(valoresPorDefecto);
    setErroresForm([]);
    setModal('crear');
  }

  function abrirEditar(item) {
    setValoresForm(aValoresFormulario(item));
    setErroresForm([]);
    setModal(item);
  }

  function cerrarModal() {
    setModal(null);
    setErroresForm([]);
  }

  async function guardar() {
    setGuardando(true);
    setErroresForm([]);
    const esEdicion = modal && modal !== 'crear';
    try {
      const cuerpo = aCuerpoPeticion(valoresForm);
      if (esEdicion) {
        const actualizado = await callApi(`${endpoint}/${modal[idField]}`, {
          method: 'PUT',
          body: JSON.stringify(cuerpo),
        });
        setItems((prev) => prev.map((i) => (i[idField] === modal[idField] ? actualizado : i)));
        toasts.success(`${nombreSingular} actualizado.`);
        cargar();
      } else {
        const creado = await callApi(endpoint, { method: 'POST', body: JSON.stringify(cuerpo) });
        setItems((prev) => [...prev, creado]);
        toasts.success(`${nombreSingular} creado.`);
        cargar();
      }
      cerrarModal();
    } catch (err) {
      console.error(err);
      if (err.status === 400 && err.fieldErrors?.length) {
        setErroresForm(err.fieldErrors);
      } else {
        toasts.error(err.message || 'No se pudo guardar.');
      }
    } finally {
      setGuardando(false);
    }
  }

  async function confirmarEliminar() {
    setEliminando(true);
    try {
      await callApi(`${endpoint}/${aEliminar[idField]}`, { method: 'DELETE' });
      setItems((prev) => prev.filter((i) => i[idField] !== aEliminar[idField]));
      toasts.success(`${nombreSingular} eliminado.`);
      setAEliminar(null);
    } catch (err) {
      console.error(err);
      toasts.error(err.message || 'No se pudo eliminar.');
    } finally {
      setEliminando(false);
    }
  }

  const columnasConAcciones =
    puedeEditar || puedeEliminar || accionesExtra
      ? [...columns, { key: '__acciones', label: '' }]
      : columns;

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>{title}</h2>
          <p>{subtitle}</p>
        </div>
        {puedeCrear && (
          <button className="dash-btn" onClick={abrirCrear}>
            + Agregar
          </button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {searchKeys.length > 0 && !cargando && items.length > 0 && (
        <div className="dash-toolbar">
          <input
            className="dash-input"
            placeholder="Buscar..."
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value);
              setPagina(1);
            }}
          />
        </div>
      )}

      {cargando && (
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando...</p>
        </div>
      )}

      {!cargando && !error && items.length === 0 && (
        <div className="state-block">
          <h3>Todavía no hay {nombreSingular}s registrados</h3>
          <p>{puedeCrear ? 'Agrega el primero desde el botón "+ Agregar".' : 'Vuelve más tarde.'}</p>
        </div>
      )}

      {!cargando && items.length > 0 && filtrados.length === 0 && (
        <div className="state-block">
          <h3>Sin resultados</h3>
          <p>Prueba con otro término de búsqueda.</p>
        </div>
      )}

      {!cargando && paginaItems.length > 0 && (
        <>
          <div className="dash-table-wrap">
            <table className="dash-table">
              <thead>
                <tr>
                  {columnasConAcciones.map((col) => (
                    <th key={col.key}>{col.label}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {paginaItems.map((item) => (
                  <tr key={item[idField]}>
                    {columns.map((col) => (
                      <td key={col.key}>{col.render ? col.render(item) : item[col.key]}</td>
                    ))}
                    {(puedeEditar || puedeEliminar || accionesExtra) && (
                      <td>
                        <div className="dash-table__actions">
                          {accionesExtra?.(item)}
                          {puedeEditar && (
                            <button className="dash-icon-btn" onClick={() => abrirEditar(item)}>
                              Editar
                            </button>
                          )}
                          {puedeEliminar && (
                            <button className="dash-icon-btn dash-icon-btn--danger" onClick={() => setAEliminar(item)}>
                              Eliminar
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
            {totalPaginas > 1 && (
              <div className="dash-pagination">
                <button
                  className="dash-icon-btn"
                  disabled={paginaSegura === 1}
                  onClick={() => setPagina((p) => p - 1)}
                >
                  ← Anterior
                </button>
                <span>
                  Página {paginaSegura} de {totalPaginas}
                </span>
                <button
                  className="dash-icon-btn"
                  disabled={paginaSegura === totalPaginas}
                  onClick={() => setPagina((p) => p + 1)}
                >
                  Siguiente →
                </button>
              </div>
            )}
          </div>
        </>
      )}

      {modal && (
        <Modal title={modal === 'crear' ? `Nuevo ${nombreSingular}` : `Editar ${nombreSingular}`} onClose={cerrarModal}>
          <CrudForm
            fields={fields}
            values={valoresForm}
            onChange={setValoresForm}
            errors={erroresForm}
            onSubmit={guardar}
            onCancel={cerrarModal}
            submitting={guardando}
            submitLabel={modal === 'crear' ? 'Crear' : 'Guardar cambios'}
          />
        </Modal>
      )}

      {aEliminar && (
        <ConfirmDialog
          title={`Eliminar ${nombreSingular}`}
          message={`¿Seguro que quieres eliminar este ${nombreSingular}? Esta acción no se puede deshacer.`}
          onConfirm={confirmarEliminar}
          onCancel={() => setAEliminar(null)}
          loading={eliminando}
        />
      )}
    </div>
  );
}
