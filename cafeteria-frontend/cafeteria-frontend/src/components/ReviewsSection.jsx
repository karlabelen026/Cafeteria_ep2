function StarIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 2.5l2.9 6.2 6.8.7-5.1 4.6 1.5 6.7L12 17l-6.1 3.7 1.5-6.7-5.1-4.6 6.8-.7L12 2.5Z" />
    </svg>
  );
}

// Testimonios de ejemplo (contenido de marketing, no vienen del backend: no
// hay modulo de resenas de clientes en el modelo de datos del proyecto).
const RESENAS = [
  {
    nombre: 'Javiera M.',
    texto:
      'Pido todos los días el café con leche antes de entrar a trabajar — nunca me han fallado un pedido desde que usan el sistema nuevo.',
    rol: 'Clienta habitual',
  },
  {
    nombre: 'Benjamín C.',
    texto:
      'El seguimiento del pedido en tiempo real es lo mejor: sé exactamente cuándo pasar a retirar sin hacer fila.',
    rol: 'Cliente habitual',
  },
  {
    nombre: 'Francisca L.',
    texto:
      'El brownie siempre está fresco y el local se siente súper acogedor para trabajar un par de horas con el notebook.',
    rol: 'Clienta habitual',
  },
];

export default function ReviewsSection() {
  return (
    <section className="reviews-section" id="resenas">
      <div className="reviews-section__inner">
        <div className="about-section__heading">
          <span>Reseñas</span>
          <h2>Lo que dicen quienes ya nos visitaron</h2>
        </div>
        <div className="reviews-grid">
          {RESENAS.map((r) => (
            <article className="review-card" key={r.nombre}>
              <div className="review-card__stars">
                {Array.from({ length: 5 }).map((_, i) => (
                  <StarIcon key={i} />
                ))}
              </div>
              <p className="review-card__texto">“{r.texto}”</p>
              <div className="review-card__autor">
                <span className="review-card__avatar">{r.nombre.charAt(0)}</span>
                <div>
                  <strong>{r.nombre}</strong>
                  <span>{r.rol}</span>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
