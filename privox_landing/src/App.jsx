import React from 'react';
import {
  Shield,
  Key,
  PhoneCall,
  MessageSquare,
  Mic,
  Users,
  Download,
  ArrowRight,
  Lock,
  ShieldCheck
} from 'lucide-react';
import FeaturesCarousel from './components/FeaturesCarousel';
import ScreenshotCarousel from './components/ScreenshotCarousel';


function App() {
  return (
    <>
      {/* Navigation */}
      <nav className="navbar">
        <div className="container nav-container">
          <div className="logo-wrapper">
            <img src="/privox3.png" alt="Privox Logo" style={{ width: '40px', height: '40px' }} />
            <span>PRIVOX</span>
          </div>
          <ul className="nav-links">
            <li><a href="#inicio">Inicio</a></li>
            <li><a href="#caracteristicas">Características</a></li>
            <li><a href="#capturas">Capturas</a></li>
          </ul>
        </div>
      </nav>

      {/* Hero Section */}
      <section id="inicio" className="hero-section">
        <div className="container hero-grid">
          <div className="hero-content">
            <h1 className="hero-title">
              Comunicaciones <span className="gradient-text">100% Anónimas</span> y Cifradas
            </h1>
            <p className="hero-description">
              Realiza llamadas de voz con distorsión de timbre en tiempo real y chatea de forma cifrada de extremo a extremo sin vincular tu número de teléfono ni correo electrónico.
            </p>
            <div className="hero-buttons">
              <a href="#caracteristicas" className="btn btn-secondary">
                Explorar Más <ArrowRight size={18} />
              </a>
            </div>
          </div>

          <div className="hero-mockup">
            <div className="phone-container" style={{ borderColor: 'var(--primary)', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.8), var(--shadow-neon-emerald)' }}>
              <img src="/assets/perfil.png" alt="Llamada Cifrada en Privox" className="phone-screen" />
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="caracteristicas" className="features-section">
        <div className="container">
          <div className="section-header">
            <span className="section-tagline">Funcionalidades</span>
            <h2>Seguridad Absoluta Sin Compromisos</h2>
            <p style={{ maxWidth: '600px', margin: '0 auto' }}>
              Privox no es un chat convencional. Hemos desarrollado un sistema donde tu identidad está completamente separada del hardware y de cualquier servidor central.
            </p>
          </div>

          {/* Features Carousel (Link screenshots to features) */}
          <FeaturesCarousel />

        </div>
      </section>

      {/* Screenshots Section */}
      <section id="capturas" className="screenshots-section">
        <div className="container">
          <div className="section-header">
            <span className="section-tagline">Vistas de la Aplicación</span>
            <h2>Diseño Avanzado y Funcional</h2>
            <p style={{ maxWidth: '600px', margin: '0 auto' }}>
              Explora las diferentes pantallas de Privox desarrolladas con una interfaz moderna en modo oscuro, optimizada para un uso ágil y ultra-seguro.
            </p>
          </div>

          <ScreenshotCarousel />
        </div>
      </section>

      {/* CTA Download Section */}
      <section id="descargar" style={{ padding: '6rem 0', background: 'rgba(16, 185, 129, 0.02)', borderTop: '1px solid rgba(255,255,255,0.02)' }}>
        <div className="container" style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2rem' }}>
          <img src="/privox3.png" alt="Privox Logo" style={{ width: '100px', height: '100px' }} />
          <h2 style={{ maxWidth: '650px' }}>Protege tu Privacidad Hoy Mismo</h2>
        </div>
      </section>

      {/* Footer */}
      <footer className="footer">
        <div className="container footer-content">
          <ul className="footer-links">
            <li><a href="#inicio">Inicio</a></li>
            <li><a href="#caracteristicas">Características</a></li>
            <li><a href="#capturas">Capturas de Pantalla</a></li>
          </ul>
          <p className="copyright" style={{ maxWidth: '600px', lineHeight: '1.6' }}>
            © {new Date().getFullYear()} Privox. Desarrollado por <span style={{ color: 'var(--primary)' }}>Futura</span>.
          </p>
        </div>
      </footer>
    </>
  );
}

export default App;
