function PinIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
      <path
        d="M12 21s7-6.3 7-11.5A7 7 0 0 0 5 9.5C5 14.7 12 21 12 21Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="9.5" r="2.4" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 7v5l3.5 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

// Sucursales de ejemplo (contenido de marketing): el proyecto no tiene un
// modulo de sucursales en el backend, es una sola cafeteria logicamente.
const SUCURSALES = [
  {
    nombre: 'Sucursal Centro',
    direccion: 'Av. Libertador Bernardo O\'Higgins 1234, Santiago',
    horario: 'Lun a sáb, 8:00 – 20:00',
  },
  {
    nombre: 'Sucursal Providencia',
    direccion: 'Av. Providencia 2580, Providencia',
    horario: 'Lun a dom, 9:00 – 21:00',
  },
];

export default function LocationsSection() {
  return (
    <section className="locations-section" id="ubicaciones">
      <div className="reviews-section__inner">
        <div className="about-section__heading">
          <span>Ubicaciones</span>
          <h2>Visítanos</h2>
          <p>Dos locales pensados para que siempre tengas uno cerca.</p>
        </div>
        <div className="locations-grid">
          {SUCURSALES.map((s) => (
            <article className="location-card" key={s.nombre}>
              <div className="location-card__icon">
                <PinIcon />
              </div>
              <div>
                <h3>{s.nombre}</h3>
                <p>{s.direccion}</p>
                <p className="location-card__horario">
                  <ClockIcon />
                  {s.horario}
                </p>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
