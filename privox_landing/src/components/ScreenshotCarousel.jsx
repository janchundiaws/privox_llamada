import React, { useState, useEffect, useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

const IMAGES = [
  {
    src: '/assets/secure_calls.png',
    title: 'Llamadas Seguras con Distorsión',
    description: 'Comunícate sin revelar tu identidad real. Aplica filtros de voz como robot, hombre, mujer o alienígena en tiempo real.'
  },
  {
    src: '/assets/chat_interface.png',
    title: 'Mensajes Cifrados de Extremo a Extremo',
    description: 'Los chats de texto son cifrados localmente en el dispositivo. Cero registros en servidores centralizados y auto-destrucción opcional.'
  },
  {
    src: '/assets/contacts_interface.png',
    title: 'Control Absoluto de tu Identidad',
    description: 'Regístrate de manera 100% anónima con claves criptográficas. Controla quién puede llamarte y mantén tus contactos bajo llave.'
  }
];

export default function ScreenshotCarousel() {
  const [activeIndex, setActiveIndex] = useState(0);
  const timerRef = useRef(null);

  const startAutoPlay = () => {
    stopAutoPlay();
    timerRef.current = setInterval(() => {
      handleNext();
    }, 6000);
  };

  const stopAutoPlay = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
  };

  const handleNext = () => {
    setActiveIndex((prev) => (prev + 1) % IMAGES.length);
  };

  const handlePrev = () => {
    setActiveIndex((prev) => (prev - 1 + IMAGES.length) % IMAGES.length);
  };

  useEffect(() => {
    startAutoPlay();
    return () => stopAutoPlay();
  }, []);

  // Restart timer when user manual overrides
  const setIndexManual = (index) => {
    setActiveIndex(index);
    startAutoPlay();
  };

  return (
    <div
      style={{ display: 'flex', flexDirection: 'column', gap: '2rem', width: '100%' }}
      onMouseEnter={stopAutoPlay}
      onMouseLeave={startAutoPlay}
    >
      <div className="carousel-container">
        <button className="carousel-btn prev" onClick={handlePrev} aria-label="Anterior">
          <ChevronLeft size={24} />
        </button>

        <div className="carousel-track">
          {IMAGES.map((img, index) => {
            let positionClass = '';
            if (index === activeIndex) {
              positionClass = 'active';
            } else if (index === (activeIndex - 1 + IMAGES.length) % IMAGES.length) {
              positionClass = 'prev';
            } else {
              positionClass = 'next';
            }

            return (
              <div key={index} className={`carousel-slide ${positionClass}`}>
                <div className="phone-container" style={{ borderColor: index === activeIndex ? 'var(--primary)' : '#1e293b' }}>
                  <img src={img.src} alt={img.title} className="phone-screen" />
                </div>
              </div>
            );
          })}
        </div>

        <button className="carousel-btn next" onClick={handleNext} aria-label="Siguiente">
          <ChevronRight size={24} />
        </button>
      </div>

      <div style={{ textAlign: 'center', maxWidth: '600px', margin: '0 auto' }}>
        <h3 style={{ marginBottom: '0.5rem', color: 'var(--text-primary)' }}>
          {IMAGES[activeIndex].title}
        </h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
          {IMAGES[activeIndex].description}
        </p>
      </div>

      <div className="carousel-indicators">
        {IMAGES.map((_, index) => (
          <div
            key={index}
            className={`indicator ${index === activeIndex ? 'active' : ''}`}
            onClick={() => setIndexManual(index)}
          />
        ))}
      </div>
    </div>
  );
}
