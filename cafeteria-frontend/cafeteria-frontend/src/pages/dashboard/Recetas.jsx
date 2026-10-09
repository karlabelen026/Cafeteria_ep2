import { useMemo, useState } from 'react';
import CrudPage from '../../components/CrudPage.jsx';
import { useApiClient } from '../../services/apiClient';
import { useUserRole } from '../../hooks/useUserRole';
import { usePolling } from '../../hooks/usePolling';

// Recetas: que insumos (y cuanto) consume cada producto del menu (ver
// ms-inventario/RecetaItem). Con esto, cada pago aprobado descuenta stock y,
// si un insumo baja de su minimo, se genera la alerta de stock bajo.
// Permisos (misma matriz que Inventario): ADMIN y BODEGUERO crean/editan,
// solo ADMIN elimina, GERENTE solo ve.
export default function Recetas() {
  const { callApi } = useApiClient();
  const role = useUserRole();
  const puedeEditar = role === 'ADMIN' || role === 'BODEGUERO';

  const [productos, setProductos] = useState([]);
  const [insumos, setInsumos] = useState([]);
  const [error, setError] = useState('');

  // Productos e insumos se refrescan igual que la tabla, para que un insumo
  // o producto recien creado por otro rol aparezca en los selectores.
  usePolling(() => {
    Promise.all([callApi('/productos'), callApi('/inventario')])
      .then(([p, i]) => {
        setProductos(p || []);
        setInsumos(i || []);
        setError('');
      })
      .catch((err) => {
        console.error(err);
        setError('No se pudieron cargar los productos o insumos para armar las recetas.');
      });
  });

  const productoPorId = useMemo(() => new Map(productos.map((p) => [p.id, p])), [productos]);
  const insumoPorId = useMemo(() => new Map(insumos.map((i) => [i.id, i])), [insumos]);

  const campos = [
    {
      name: 'productoId',
      label: 'Producto',
      type: 'select',
      required: true,
      options: productos.map((p) => ({ value: p.id, label: p.nombre })),
    },
    {
      name: 'insumoId',
      label: 'Insumo',
      type: 'select',
      required: true,
      options: insumos.map((i) => ({ value: i.id, label: `${i.nombre} (${i.unidadMedida})` })),
    },
    { name: 'cantidad', label: 'Cantidad por unidad vendida', type: 'number', required: true, min: 0.01, step: 0.01 },
  ];

  return (
    <>
      {error && <div className="alert alert-error">{error}</div>}
      <CrudPage
        title="Recetas"
        subtitle="Insumos y cantidades que usa cada producto del menú."
        endpoint="/recetas"
        nombreSingular="ingrediente"
        puedeCrear={puedeEditar}
        puedeEditar={puedeEditar}
        puedeEliminar={role === 'ADMIN'}
        valoresPorDefecto={{ productoId: '', insumoId: '', cantidad: '' }}
        aCuerpoPeticion={(v) => ({
          productoId: Number(v.productoId),
          insumoId: Number(v.insumoId),
          cantidad: v.cantidad,
        })}
        fields={campos}
        columns={[
          {
            key: 'productoId',
            label: 'Producto',
            render: (r) => <strong>{productoPorId.get(r.productoId)?.nombre || `Producto #${r.productoId}`}</strong>,
          },
          {
            key: 'insumoId',
            label: 'Insumo',
            render: (r) => insumoPorId.get(r.insumoId)?.nombre || `Insumo #${r.insumoId}`,
          },
          {
            key: 'cantidad',
            label: 'Cantidad',
            render: (r) => `${r.cantidad} ${insumoPorId.get(r.insumoId)?.unidadMedida || ''}`,
          },
        ]}
      />
    </>
  );
}
