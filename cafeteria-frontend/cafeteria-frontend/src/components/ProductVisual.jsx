import { useState } from 'react';

function CupHotIcon() {
  return (
    <svg width="46" height="46" viewBox="0 0 24 24" fill="none">
      <path d="M4 8h13v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8Z" stroke="#fff" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M17 9h1.5a2.5 2.5 0 0 1 0 5H17" stroke="#fff" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M7 5c0-.7.5-1 .8-1.6M11 5c0-.7.5-1 .8-1.6" stroke="#fff" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

function CupColdIcon() {
  return (
    <svg width="46" height="46" viewBox="0 0 24 24" fill="none">
      <path d="M6 4h12l-1.3 15.2a2 2 0 0 1-2 1.8H9.3a2 2 0 0 1-2-1.8L6 4Z" stroke="#fff" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M6.6 8.5h10.8" stroke="#fff" strokeWidth="1.4" />
      <path d="M14 4 12 22" stroke="#fff" strokeWidth="1.3" strokeLinecap="round" />
    </svg>
  );
}

function PastryIcon() {
  return (
    <svg width="46" height="46" viewBox="0 0 24 24" fill="none">
      <path
        d="M3 13c1.5-4 4.5-7 9-7s7.5 3 9 7c-2 .6-3.2 2-3.2 2s-1.4-1.2-2.8-1.2S12.6 15 12 15s-1.6-1.2-3-1.2S6.8 15 6.8 15 5.2 13.6 3 13Z"
        stroke="#fff"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const PALETTES = {
  'Bebidas calientes': ['#5a3420', '#c1622d'],
  'Bebidas frías': ['#2b6b72', '#69b7ba'],
  Pastelería: ['#96591f', '#e2a75c'],
  default: ['#4a2e1f', '#8a6142'],
};

function pickIcon(categoria) {
  if ((categoria || '').toLowerCase().includes('frí')) return CupColdIcon;
  if ((categoria || '').toLowerCase().includes('pasteler')) return PastryIcon;
  return CupHotIcon;
}

const DIACRITICS_RANGE = new RegExp('[' + String.fromCharCode(0x0300) + '-' + String.fromCharCode(0x036f) + ']', 'g');

function slugify(nombre) {
  return (nombre || '')
    .normalize('NFD')
    .replace(DIACRITICS_RANGE, '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');
}

export default function ProductVisual({ nombre, categoria, imagenUrl }) {
  const [c1, c2] = PALETTES[categoria] || PALETTES.default;
  const Icon = pickIcon(categoria);
  const [intento, setIntento] = useState(0);

  // Foto real del producto, probando en orden: la imagenUrl guardada en el
  // producto, y luego public/productos/<nombre-slug>.jpg / .png. Si ninguna
  // carga, cae al icono de siempre.
  const slug = nombre ? slugify(nombre) : null;
  const candidatas = [imagenUrl, slug && `/productos/${slug}.jpg`, slug && `/productos/${slug}.png`].filter(
    (url, i, lista) => url && lista.indexOf(url) === i,
  );
  const src = candidatas[intento];

  if (src) {
    return (
      <div className="product-visual product-visual--photo">
        <img src={src} alt={nombre || ''} loading="lazy" onError={() => setIntento((n) => n + 1)} />
      </div>
    );
  }

  return (
    <div className="product-visual" style={{ background: `linear-gradient(135deg, ${c1}, ${c2})` }}>
      <Icon />
    </div>
  );
}
