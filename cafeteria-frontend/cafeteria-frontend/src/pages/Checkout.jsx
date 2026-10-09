import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useApiClient } from '../services/apiClient';
import { useCart } from '../context/CartContext';
import CoffeeIcon from '../components/CoffeeIcon';

const METODOS = [
  { value: 'DEBITO', label: 'Débito' },
  { value: 'CREDITO', label: 'Crédito' },
];

export default function Checkout() {
  const { callApi } = useApiClient();
  const { items, totalPrecio, clear } = useCart();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    nombre: '',
    email: '',
    metodo: 'DEBITO',
    numeroTarjeta: '',
  });
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState('');

  const update = (campo) => (e) => setForm((f) => ({ ...f, [campo]: e.target.value }));

  if (items.length === 0) {
    return (
      <div className="app-shell">
        <header className="navbar">
          <Link to="/" className="navbar__brand">
            <CoffeeIcon />
            CafeGestión360
          </Link>
        </header>
        <div className="page">
          <div className="state-block">
            <h3>No tienes productos en tu pedido</h3>
            <p>Vuelve a la tienda para agregar algo del menú.</p>
            <Link to="/" className="btn btn-primary">
              Ir a la tienda
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setProcesando(true);
    try {
      // Un solo endpoint atomico: ms-pedidos crea el pedido en PENDIENTE_PAGO,
      // resuelve precios contra ms-productos (nunca confia en lo que manda el
      // navegador) y publica pedido.creado para que ms-pagos simule la
      // pasarela de forma asincrona (ver docs/EP2_PLAN.md seccion 3 y 5).
      const { codigoSeguimiento } = await callApi('/public/checkout', {
        method: 'POST',
        body: JSON.stringify({
          cliente: { nombre: form.nombre, email: form.email },
          items: items.map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
          pago: {
            metodo: form.metodo,
            // Solo digitos: el backend nunca acepta espacios/guiones y nunca
            // guarda el numero completo (solo usa los ultimos 4 y descarta
            // el resto, ver docs/EP2_PLAN.md seccion 2 y 5).
            numeroTarjeta: form.numeroTarjeta.replace(/\D/g, ''),
          },
        }),
      });

      clear();
      try {
        localStorage.setItem('cg360_ultimo_pedido', codigoSeguimiento);
      } catch {
        // no bloquea el flujo si localStorage no esta disponible
      }
      navigate(`/seguimiento/${codigoSeguimiento}`);
    } catch (err) {
      console.error(err);
      setError(err.message || 'No se pudo procesar el pedido. Inténtalo de nuevo.');
    } finally {
      setProcesando(false);
    }
  };

  return (
    <div className="app-shell">
      <header className="navbar">
        <Link to="/" className="navbar__brand">
          <CoffeeIcon />
          CafeGestión360
        </Link>
      </header>
      <div className="page checkout-page">
        <h2>Confirma tu pedido</h2>

        <div className="checkout-layout">
          <form className="checkout-form" onSubmit={handleSubmit}>
            <h3>Tus datos</h3>
            <label>
              Nombre completo
              <input required value={form.nombre} onChange={update('nombre')} />
            </label>
            <label>
              Email
              <input required type="email" value={form.email} onChange={update('email')} />
            </label>

            <h3>Pago con tarjeta</h3>
            <p className="checkout-form__note">
              Simulación para fines académicos: no se procesa contra ninguna pasarela real, y nunca se
              guarda el número completo de la tarjeta.
            </p>
            <label>
              Método
              <select value={form.metodo} onChange={update('metodo')}>
                {METODOS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Número de tarjeta
              <input
                required
                inputMode="numeric"
                maxLength={19}
                placeholder="4111111111111111"
                value={form.numeroTarjeta}
                onChange={update('numeroTarjeta')}
              />
            </label>
            <p className="checkout-form__note">
              Tarjetas de prueba: termina en <code>0000</code> → pago rechazado · <code>9999</code> → error transitorio
              (reintenta y cae a la DLQ) · <code>8888</code> → error directo a la DLQ.
            </p>

            {error && <div className="alert alert-error">{error}</div>}

            <button className="btn btn-primary" type="submit" disabled={procesando}>
              {procesando ? 'Enviando pedido...' : `Pagar $${totalPrecio}`}
            </button>
          </form>

          <div className="checkout-summary">
            <h3>Resumen</h3>
            {items.map((i) => (
              <div className="checkout-summary__line" key={i.productoId}>
                <span>
                  {i.cantidad} × {i.nombre}
                </span>
                <span>${i.precio * i.cantidad}</span>
              </div>
            ))}
            <div className="checkout-summary__total">
              <span>Total</span>
              <strong>${totalPrecio}</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
