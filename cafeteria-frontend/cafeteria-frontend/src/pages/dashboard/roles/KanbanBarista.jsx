import { useState } from 'react';
import { usePolling } from '../../../hooks/usePolling';
import { useApiClient } from '../../../services/apiClient';
import { useToasts } from '../../../context/ToastContext.jsx';
import { etiquetaEstado } from '../../../utils/estadosPedido';

// Kanban "Cola de preparación" (ver docs/EP2_PLAN.md seccion 6.2):
// PAGADO -> EN_PREPARACION -> LISTO -> ENTREGADO, con un boton para avanzar
// cada pedido a la siguiente columna. BARISTA no puede cancelar (eso es
// exclusivo de ADMIN, ver PedidoController).
const COLUMNAS = [
  { estado: 'PAGADO', titulo: 'Pendiente', siguiente: 'EN_PREPARACION' },
  { estado: 'EN_PREPARACION', titulo: 'En preparación', siguiente: 'LISTO' },
  { estado: 'LISTO', titulo: 'Listo', siguiente: 'ENTREGADO' },
  { estado: 'ENTREGADO', titulo: 'Entregado', siguiente: null },
];

export default function KanbanBarista() {
  const { callApi } = useApiClient();
  const toasts = useToasts();
  const [pedidos, setPedidos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [avanzando, setAvanzando] = useState(null);

  function cargar() {
    callApi('/pedidos')
      .then((data) => {
        setPedidos((data || []).filter((p) => COLUMNAS.some((c) => c.estado === p.estado)));
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar los pedidos (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  }

  usePolling(cargar);

  async function avanzar(pedido, siguiente) {
    setAvanzando(pedido.id);
    try {
      const actualizado = await callApi(`/pedidos/${pedido.id}/estado`, {
        method: 'PATCH',
        body: JSON.stringify({ nuevoEstado: siguiente }),
      });
      setPedidos((prev) => prev.map((p) => (p.id === pedido.id ? actualizado : p)));
      toasts.success(`Pedido ${pedido.codigoSeguimiento?.slice(0, 8)} → ${etiquetaEstado(siguiente)}.`);
    } catch (err) {
      console.error(err);
      toasts.error(err.message || 'No se pudo actualizar el pedido.');
    } finally {
      setAvanzando(null);
    }
  }

  if (cargando) {
    return (
      <div className="dash-card">
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando pedidos...</p>
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
          <h2>Cola de preparación</h2>
          <p>Pedidos pendientes, en curso y entregados. Se actualiza solo.</p>
        </div>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 16,
        }}
      >
        {COLUMNAS.map((col) => {
          const items = pedidos.filter((p) => p.estado === col.estado);
          return (
            <div className="dash-card" key={col.estado}>
              <div className="dash-card__header">
                <h3>{col.titulo}</h3>
                <span className="dash-card__sub">{items.length}</span>
              </div>
              {items.length === 0 ? (
                <p className="dash-empty">Sin pedidos.</p>
              ) : (
                <ul className="dash-list">
                  {items.map((p) => (
                    <li className="dash-list__item" key={p.id} style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                      <div className="dash-list__title">
                        <strong>{p.codigoSeguimiento?.slice(0, 8)}</strong>
                        <span>
                          {p.clienteNombre || 'Mostrador'} · ${p.total}
                        </span>
                      </div>
                      {col.siguiente && (
                        <button
                          className="dash-btn dash-btn--ghost"
                          style={{ marginTop: 8 }}
                          disabled={avanzando === p.id}
                          onClick={() => avanzar(p, col.siguiente)}
                        >
                          {avanzando === p.id ? 'Actualizando...' : `Pasar a ${etiquetaEstado(col.siguiente)}`}
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
