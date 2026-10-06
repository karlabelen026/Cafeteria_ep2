import { useEffect, useRef, useState } from 'react';

const SLIDES = [
  { src: '/banner/hero.jpg', alt: 'Café con leche, jarabe de vainilla y cold brew sobre la barra' },
  { src: '/banner/interior.jpg', alt: 'Interior acogedor de la cafetería, con luces cálidas' },
  { src: '/banner/espresso.jpg', alt: 'Espresso recién extraído, con vapor' },
  { src: '/banner/granos.jpg', alt: 'Café molido, espresso y granos tostados' },
];

const INTERVALO_MS = 5000;

// Fondo del hero con cross-fade automático entre fotos + puntos de
// navegación manual. Pausa el avance automático mientras el mouse esta
// encima (hover) para no pelear con quien esta leyendo/clickeando.
export default function HeroCarousel() {
  const [indice, setIndice] = useState(0);
  const [pausado, setPausado] = useState(false);
  const timerRef = useRef(null);

  useEffect(() => {
    if (pausado) return undefined;
    timerRef.current = setInterval(() => {
      setIndice((i) => (i + 1) % SLIDES.length);
    }, INTERVALO_MS);
    return () => clearInterval(timerRef.current);
  }, [pausado]);

  return (
    <div
      className="hero-carousel"
      onMouseEnter={() => setPausado(true)}
      onMouseLeave={() => setPausado(false)}
    >
      {SLIDES.map((slide, i) => (
        <div
          key={slide.src}
          className={`hero-carousel__slide${i === indice ? ' hero-carousel__slide--activo' : ''}`}
          style={{ backgroundImage: `url('${slide.src}')` }}
          role="img"
          aria-label={slide.alt}
          aria-hidden={i !== indice}
        />
      ))}
      <div className="hero-carousel__overlay" />
      <div className="hero-carousel__dots">
        {SLIDES.map((slide, i) => (
          <button
            key={slide.src}
            type="button"
            className={`hero-carousel__dot${i === indice ? ' hero-carousel__dot--activo' : ''}`}
            aria-label={`Ver foto ${i + 1} de ${SLIDES.length}`}
            aria-current={i === indice}
            onClick={() => setIndice(i)}
          />
        ))}
      </div>
    </div>
  );
}
