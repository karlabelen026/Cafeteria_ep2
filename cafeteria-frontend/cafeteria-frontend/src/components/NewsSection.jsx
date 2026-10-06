// Novedades de ejemplo (contenido de marketing): no hay modulo de blog/
// noticias en el backend, son tarjetas estaticas pensadas para mostrar que
// la carta y las promociones cambian seguido.
const NOVEDADES = [
  {
    categoria: 'Nuevo en la carta',
    titulo: 'Llegó el Cold Brew',
    texto: 'Café frío de extracción lenta, 16 horas en reposo. Ya disponible en ambas sucursales.',
    imagen: '/productos/cold-brew.jpg',
  },
  {
    categoria: 'Promoción',
    titulo: '2x1 en pastelería los miércoles',
    texto: 'Todos los miércoles de octubre, lleva dos productos de pastelería y paga uno.',
    imagen: '/productos/cheesecake.jpg',
  },
  {
    categoria: 'Novedad',
    titulo: 'Ahora puedes seguir tu pedido en vivo',
    texto: 'Desde que confirmas el pago hasta que está listo para retirar, todo en una sola página.',
    imagen: '/productos/latte-vainilla.jpg',
  },
];

export default function NewsSection() {
  return (
    <section className="news-section" id="novedades">
      <div className="reviews-section__inner">
        <div className="about-section__heading">
          <span>Novedades</span>
          <h2>Siempre hay algo nuevo</h2>
        </div>
        <div className="news-grid">
          {NOVEDADES.map((n) => (
            <article className="news-card" key={n.titulo}>
              <div
                className="news-card__imagen"
                style={{ backgroundImage: `url('${n.imagen}')` }}
              />
              <div className="news-card__body">
                <span className="news-card__categoria">{n.categoria}</span>
                <h3>{n.titulo}</h3>
                <p>{n.texto}</p>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
