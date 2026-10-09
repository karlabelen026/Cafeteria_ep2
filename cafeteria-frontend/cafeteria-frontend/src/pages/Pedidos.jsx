import { useState } from 'react';
import { usePolling } from '../hooks/usePolling';
import { ESTADOS_DE_PAGO, etiquetaEstado } from '../utils/estadosPedido';
import { useApiClient } from '../services/apiClient';
import { useToasts } from '../context/ToastContext.jsx';
import { useUserRole } from '../hooks/useUserRole';
import { badgeClassForEstado } from '../utils/badges';

// Espejo de MaquinaEstadosPedido (ver ms-pedidos/service/MaquinaEstadosPedido):
// que transiciones son validas desde cada estado. El backend es quien manda
// (vuelve a validarlo y responde 409 si no corresponde); esto solo evita
// ofrecer opciones que de entrada van a fallar.
const TRANSICIONES = {
  PAGADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['LISTO', 'CANCELADO'],
  LISTO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
};

export default function Pedidos() {
  const { callApi } = useApiClient();
  const toasts = useToasts();
  const role = useUserRole();
  // Matriz de roles (ver docs/EP2_PLAN.md seccion 4): ADMIN y BARISTA pueden
  // cambiar el estado; BARISTA no puede cancelar. CAJERO/GERENTE solo ven.
  const puedeCambiarEstado = role === 'ADMIN' || role === 'BARISTA';
  const puedeCancelar = role === 'ADMIN';

  const [pedidos, setPedidos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [actualizando, setActualizando] = useState(null);

  // La confirmacion del pago se resuelve sola por mensajeria (RabbitMQ) y se
  // revisa en "Pagos": aqui solo se listan los pedidos que ya entraron a la
  // barra (Pendiente -> En preparación -> Listo -> Entregado).
  usePolling(() => {
    callApi('/pedidos')
      .then((data) => {
        setPedidos((data || []).filter((p) => !ESTADOS_DE_PAGO.includes(p.estado)));
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar los pedidos (revisa el token o el backend).');
      })
      .finally(() => setCargando(false));
  });

  const cambiarEstado = async (pedido, nuevoEstado) => {
    setActualizando(pedido.id);
    try {
      const actualizado = await callApi(`/pedidos/${pedido.id}/estado`, {
        method: 'PATCH',
        body: JSON.stringify({ nuevoEstado }),
      });
      setPedidos((prev) => prev.map((p) => (p.id === pedido.id ? actualizado : p)));
      toasts.success(`Pedido ${pedido.codigoSeguimiento?.slice(0, 8) || pedido.id} pasó a ${etiquetaEstado(nuevoEstado)}.`);
    } catch (err) {
      console.error(err);
      toasts.error(err.message || `No se pudo actualizar el pedido.`);
    } finally {
      setActualizando(null);
    }
  };

  const cancelar = async (pedido) => {
    setActualizando(pedido.id);
    try {
      const actualizado = await callApi(`/pedidos/${pedido.id}`, { method: 'DELETE' });
      setPedidos((prev) => prev.map((p) => (p.id === pedido.id ? actualizado : p)));
      toasts.success('Pedido cancelado.');
    } catch (err) {
      console.error(err);
      toasts.error(err.message || 'No se pudo cancelar el pedido.');
    } finally {
      setActualizando(null);
    }
  };

  return (
    <div>
      <div className="dash-page__header">
        <div>
          <h2>Pedidos</h2>
          <p>Seguimiento de lo que se está preparando y entregando.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {cargando && (
        <div className="state-block">
          <div className="spinner" />
          <p>Cargando pedidos...</p>
        </div>
      )}

      {!cargando && !error && pedidos.length === 0 && (
        <div className="state-block">
          <h3>No hay pedidos registrados todavía</h3>
          <p>Los nuevos pedidos aparecerán aquí apenas se registren.</p>
        </div>
      )}

      {!cargando && pedidos.length > 0 && (
        <div className="dash-table-wrap">
          <table className="dash-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Cliente</th>
                <th>Canal</th>
                <th>Total</th>
                <th>Estado</th>
                {(puedeCambiarEstado || puedeCancelar) && <th></th>}
              </tr>
            </thead>
            <tbody>
              {pedidos.map((p) => {
                const siguientes = TRANSICIONES[p.estado] || [];
                const puedeCancelarEste = puedeCancelar && siguientes.includes('CANCELADO');
                return (
                  <tr key={p.id}>
                    <td>{p.codigoSeguimiento ? p.codigoSeguimiento.slice(0, 8) : `#${p.id}`}</td>
                    <td>{p.clienteNombre || '—'}</td>
                    <td>{p.canal}</td>
                    <td>${p.total}</td>
                    <td>
                      <span className={badgeClassForEstado(p.estado)}>{etiquetaEstado(p.estado)}</span>
                    </td>
                    {(puedeCambiarEstado || puedeCancelar) && (
                      <td>
                        <div className="dash-table__actions">
                          {puedeCambiarEstado && siguientes.length > 0 && (
                            <select
                              className="dash-select"
                              value=""
                              disabled={actualizando === p.id}
                              onChange={(e) => e.target.value && cambiarEstado(p, e.target.value)}
                            >
                              <option value="" disabled>
                                Cambiar a...
                              </option>
                              {siguientes
                                .filter((s) => s !== 'CANCELADO')
                                .map((estado) => (
                                  <option key={estado} value={estado}>
                                    {etiquetaEstado(estado)}
                                  </option>
                                ))}
                            </select>
                          )}
                          {puedeCancelarEste && (
                            <button
                              className="dash-icon-btn dash-icon-btn--danger"
                              disabled={actualizando === p.id}
                              onClick={() => cancelar(p)}
                            >
                              Cancelar
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
