import { Link } from 'react-router-dom';
import CoffeeIcon from './CoffeeIcon';
import { InstagramIcon, FacebookIcon, TikTokIcon, AppleIcon, PlayStoreIcon } from './SocialIcons';

export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <div className="site-footer__top">
          <div className="site-footer__brand">
            <span className="site-footer__brand-name">
              <CoffeeIcon size={20} />
              CafeGestión360
            </span>
            <p>
              Sistema de gestión para cafeterías: pedidos, inventario, clientes y reportes en
              un solo lugar. Proyecto académico DSY1107 — Desarrollo Cloud Native I.
            </p>
            <div className="site-footer__social">
              <button type="button" aria-label="Instagram">
                <InstagramIcon />
              </button>
              <button type="button" aria-label="Facebook">
                <FacebookIcon />
              </button>
              <button type="button" aria-label="TikTok">
                <TikTokIcon />
              </button>
            </div>
          </div>

          <div className="site-footer__columns">
            <div className="site-footer__col">
              <h4>Tienda</h4>
              <ul>
                <li><Link to="/">Ver menú</Link></li>
                <li><a href="#novedades">Novedades</a></li>
                <li><a href="#about">Quiénes somos</a></li>
              </ul>
            </div>

            <div className="site-footer__col">
              <h4>Visítanos</h4>
              <ul>
                <li><a href="#ubicaciones">Ubicaciones</a></li>
                <li><a href="#resenas">Reseñas</a></li>
              </ul>
            </div>

            <div className="site-footer__col">
              <h4>Empresa</h4>
              <ul>
                <li><a href="#about">Nuestra historia</a></li>
                <li><a href="#about">Trabaja con nosotros</a></li>
              </ul>
            </div>

            <div className="site-footer__col">
              <h4>Cuenta</h4>
              <ul>
                <li><Link to="/login">Portal del equipo</Link></li>
                <li><Link to="/login">Iniciar sesión</Link></li>
              </ul>
            </div>
          </div>
        </div>

        <div className="site-footer__apps">
          <span className="app-badge">
            <AppleIcon />
            <span>
              <span className="app-badge__eyebrow">Próximamente en</span>
              <span className="app-badge__title">App Store</span>
            </span>
          </span>
          <span className="app-badge">
            <PlayStoreIcon />
            <span>
              <span className="app-badge__eyebrow">Próximamente en</span>
              <span className="app-badge__title">Google Play</span>
            </span>
          </span>
        </div>

        <div className="site-footer__bottom">
          <span>© {year} CafeGestión360. Todos los derechos reservados.</span>
          <span>Hecho con React + Spring Boot · Azure Entra ID</span>
        </div>
      </div>
    </footer>
  );
}
