import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useApiClient } from '../services/apiClient';
import { useCart } from '../context/CartContext';
import CoffeeIcon from '../components/CoffeeIcon';
import CartIcon from '../components/CartIcon';
import ProductVisual from '../components/ProductVisual';
import CartDrawer from '../components/CartDrawer';
import AboutSection from '../components/AboutSection';
import HeroCarousel from '../components/HeroCarousel';
import ReviewsSection from '../components/ReviewsSection';
import LocationsSection from '../components/LocationsSection';
import NewsSection from '../components/NewsSection';
import Footer from '../components/Footer';

// Etiquetas puramente decorativas (no vienen del backend, no hay campo de
// oferta en el modelo Producto): solo para que la grilla se vea variada,
// como los ribbons "New"/"Popular" de una carta real.
function tagFor(id) {
  if (id % 5 === 0) return 'Popular';
  if (id % 7 === 0) return 'Nuevo';
  return null;
}

export default function Store() {
  const { callApi } = useApiClient();
  const { items: cartItems, addItem, totalItems } = useCart();
  const [productos, setProductos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [busqueda, setBusqueda] = useState('');
  const [categoria, setCategoria] = useState('Todos');
  const [carritoAbierto, setCarritoAbierto] = useState(false);
  const [agregado, setAgregado] = useState(null);
  const [ultimoPedido, setUltimoPedido] = useState(null);

  useEffect(() => {
    callApi('/productos')
      .then(setProductos)
      .catch((err) => {
        console.error(err);
        setError('No se pudo cargar el menú. Verifica que el backend esté corriendo.');
      })
      .finally(() => setCargando(false));

    try {
      setUltimoPedido(localStorage.getItem('cg360_ultimo_pedido'));
    } catch {
      setUltimoPedido(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const categorias = useMemo(
    () => ['Todos', ...Array.from(new Set(productos.map((p) => p.categoria || 'Otros')))],
    [productos],
  );

  const destacado = productos[0];
  const productoNuevo = productos.find((p) => p.nombre?.toLowerCase().includes('crumbl'));

  const filtrados = productos.filter((p) => {
    const coincideCategoria = categoria === 'Todos' || (p.categoria || 'Otros') === categoria;
    const q = busqueda.trim().toLowerCase();
    const coincideBusqueda =
      !q || p.nombre?.toLowerCase().includes(q) || p.descripcion?.toLowerCase().includes(q);
    return coincideCategoria && coincideBusqueda;
  });

  const handleAdd = (producto) => {
    addItem(producto);
    setAgregado(producto.id);
    setTimeout(() => setAgregado(null), 1200);
  };

  return (
    <div className="app-shell">
      <header className="store-header">
        <Link to="/" className="navbar__brand">
          <CoffeeIcon />
          CafeGestión360
        </Link>
        <div className="store-header__search">
          <input
            type="search"
            placeholder="Buscar café, pastelería..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </div>
        <div className="store-header__actions">
          {ultimoPedido && (
            <Link to={`/seguimiento/${ultimoPedido}`} className="btn btn-ghost">
              Mi pedido
            </Link>
          )}
          <Link to="/login" className="btn btn-ghost">
            Portal del equipo
          </Link>
          <button className="cart-button" onClick={() => setCarritoAbierto(true)} aria-label="Ver carrito">
            <CartIcon />
            {totalItems > 0 && <span className="cart-button__badge">{totalItems}</span>}
          </button>
        </div>
      </header>

      <section className="promo-banner">
        <HeroCarousel />
        <div className="promo-banner__text">
          <span className="hero__eyebrow">Oferta del día</span>
          <h1>{destacado ? destacado.nombre : 'Bienvenido a CafeGestión360'}</h1>
          <p>{destacado?.descripcion || 'Café recién hecho, pedidos listos en minutos.'}</p>
          {destacado && (
            <button className="btn btn-primary" onClick={() => handleAdd(destacado)}>
              Pedir ahora — ${destacado.precio}
            </button>
          )}
        </div>
      </section>

      {productoNuevo && (
        <section className="new-product-banner">
          <div className="new-product-banner__photo">
            <ProductVisual
              nombre={productoNuevo.nombre}
              categoria={productoNuevo.categoria}
              imagenUrl={productoNuevo.imagenUrl}
            />
          </div>
          <div className="new-product-banner__body">
            <span className="ribbon ribbon--nuevo" style={{ position: 'static' }}>
              Nuevo
            </span>
            <h2>{productoNuevo.nombre}</h2>
            <p>{productoNuevo.descripcion}</p>
            <button className="btn btn-primary" onClick={() => handleAdd(productoNuevo)}>
              Pedir ahora — ${productoNuevo.precio}
            </button>
          </div>
        </section>
      )}

      <div className="page">
        {error && <div className="alert alert-error">{error}</div>}

        {categorias.length > 1 && (
          <div className="category-tabs">
            {categorias.map((c) => (
              <button
                key={c}
                type="button"
                className={`category-tabs__pill${categoria === c ? ' category-tabs__pill--active' : ''}`}
                onClick={() => setCategoria(c)}
              >
                {c}
              </button>
            ))}
          </div>
        )}

        {cargando && (
          <div className="state-block">
            <div className="spinner" />
            <p>Cargando el menú...</p>
          </div>
        )}

        {!cargando && !error && filtrados.length === 0 && (
          <div className="state-block">
            <h3>No encontramos productos</h3>
            <p>Prueba con otra categoría o término de búsqueda.</p>
          </div>
        )}

        <div className="store-grid">
          {filtrados.map((p) => {
            const tag = tagFor(p.id);
            const enCarrito = cartItems.find((i) => i.productoId === p.id);
            return (
              <article key={p.id} className="store-card">
                <div className="store-card__visual-wrap">
                  <ProductVisual nombre={p.nombre} categoria={p.categoria} imagenUrl={p.imagenUrl} />
                  {tag && <span className={`ribbon ribbon--${tag.toLowerCase()}`}>{tag}</span>}
                </div>
                <div className="store-card__body">
                  <span className="store-card__category">{p.categoria || 'Otros'}</span>
                  <h3>{p.nombre}</h3>
                  {p.descripcion && <p className="store-card__desc">{p.descripcion}</p>}
                  <div className="store-card__footer">
                    <span className="store-card__price">${p.precio}</span>
                    <button
                      type="button"
                      className={`btn btn-sm ${agregado === p.id ? 'btn-outline' : 'btn-primary'}`}
                      onClick={() => handleAdd(p)}
                    >
                      {agregado === p.id
                        ? 'Agregado ✓'
                        : enCarrito
                          ? `En carrito (${enCarrito.cantidad})`
                          : 'Agregar'}
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </div>

      <div id="about">
        <AboutSection />
      </div>
      <NewsSection />
      <ReviewsSection />
      <LocationsSection />
      <Footer />

      <CartDrawer open={carritoAbierto} onClose={() => setCarritoAbierto(false)} />
    </div>
  );
}
