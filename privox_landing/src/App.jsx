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
          <br></br>

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

      {/* Botón flotante de Correo */}
      <a
        href="mailto:jpilay@futura.com.ec"
        style={{
          position: 'fixed',
          bottom: '6.5rem',
          right: '2rem',
          backgroundColor: '#0891b2',
          color: '#ffffff',
          borderRadius: '50px',
          padding: '0.875rem 1.5rem',
          boxShadow: '0 4px 15px rgba(8, 145, 178, 0.4), 0 8px 30px rgba(0, 0, 0, 0.25)',
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          zIndex: 9999,
          textDecoration: 'none',
          fontFamily: 'var(--font-heading)',
          fontWeight: '600',
          fontSize: '1.05rem',
          transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-3px)';
          e.currentTarget.style.boxShadow = '0 6px 20px rgba(8, 145, 178, 0.6), 0 12px 40px rgba(0, 0, 0, 0.3)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 4px 15px rgba(8, 145, 178, 0.4), 0 8px 30px rgba(0, 0, 0, 0.25)';
        }}
      >
        <svg
          style={{ width: '20px', height: '20px', fill: 'none', stroke: 'currentColor', strokeWidth: '2.5', strokeLinecap: 'round', strokeLinejoin: 'round' }}
          viewBox="0 0 24 24"
        >
          <rect width="20" height="16" x="2" y="4" rx="2" />
          <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
        </svg>
        <span>Enviar Correo</span>
      </a>

      {/* Botón flotante de WhatsApp */}
      <a
        href="https://wa.me/593987897194?text=Quiero%20mas%20informaci%C3%B3n"
        target="_blank"
        rel="noopener noreferrer"
        style={{
          position: 'fixed',
          bottom: '2rem',
          right: '2rem',
          backgroundColor: '#25D366',
          color: '#ffffff',
          borderRadius: '50px',
          padding: '0.875rem 1.5rem',
          boxShadow: '0 4px 15px rgba(37, 211, 102, 0.4), 0 8px 30px rgba(0, 0, 0, 0.25)',
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          zIndex: 9999,
          textDecoration: 'none',
          fontFamily: 'var(--font-heading)',
          fontWeight: '600',
          fontSize: '1.05rem',
          transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-3px)';
          e.currentTarget.style.boxShadow = '0 6px 20px rgba(37, 211, 102, 0.6), 0 12px 40px rgba(0, 0, 0, 0.3)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 4px 15px rgba(37, 211, 102, 0.4), 0 8px 30px rgba(0, 0, 0, 0.25)';
        }}
      >
        <svg
          style={{ width: '24px', height: '24px', fill: 'currentColor' }}
          viewBox="0 0 24 24"
        >
          <path d="M.057 24l1.687-6.163c-1.041-1.804-1.588-3.849-1.587-5.946C.06 5.348 5.397.01 12.008.01c3.202.001 6.212 1.246 8.477 3.514 2.266 2.268 3.507 5.28 3.505 8.484-.004 6.657-5.34 11.997-11.953 11.997-2.005-.001-3.973-.502-5.724-1.455L0 24zm6.59-4.846c1.66.986 3.284 1.48 4.961 1.482 5.49 0 9.957-4.461 9.96-9.953.003-2.66-1.029-5.163-2.906-7.04C16.381 1.8 13.875.768 11.21.765 5.719.765 1.258 5.226 1.255 10.718c-.001 1.785.485 3.532 1.408 5.04l-.999 3.648 3.734-.979zm11.367-5.592c-.3-.15-1.77-.875-2.046-.975-.276-.102-.477-.15-.677.15-.199.299-.774.975-.95 1.174-.176.199-.351.224-.651.075-3.079-1.54-4.518-2.825-5.603-4.688-.292-.502.292-.465.836-1.548.09-.18.044-.337-.023-.487-.067-.15-.575-1.385-.788-1.898-.207-.5-.436-.433-.6-.441-.157-.008-.337-.01-.518-.01a1.003 1.003 0 0 0-.724.337c-.249.271-1.002.978-1.002 2.384 0 1.407 1.024 2.767 1.168 2.956.144.19 2.017 3.08 4.886 4.319.684.295 1.218.471 1.635.604.687.218 1.312.187 1.806.114.55-.082 1.77-.723 2.022-1.42.252-.697.252-1.294.176-1.42-.077-.127-.277-.202-.577-.352z" />
        </svg>
        <span>Contactar por WhatsApp</span>
      </a>
    </>
  );
}

export default App;
