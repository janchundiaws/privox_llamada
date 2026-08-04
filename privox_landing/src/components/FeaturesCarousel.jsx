import React, { useState, useEffect, useRef } from 'react';
import { Key, PhoneCall, MessageSquare, Mic, Users } from 'lucide-react';

const FEATURES = [
  {
    id: 'identidad',
    title: 'Identidad Anónima',
    description: 'Regístrate sin correos ni números telefónicos. Tu cuenta se basa únicamente en llaves criptográficas generadas de forma local en tu dispositivo.',
    image: '/assets/perfil.png',
    icon: Key,
    color: '#06b6d4'
  },
  {
    id: 'llamadas',
    title: 'Llamadas de Voz Seguras',
    description: 'Conexiones de voz directas P2P protegidas con encriptación de extremo a extremo, evitando que intermediarios puedan interceptar la señal.',
    image: '/assets/secure_calls.png',
    icon: PhoneCall,
    color: '#2563eb'
  },
  {
    id: 'chat',
    title: 'Chat de Texto Cifrado',
    description: 'Envía mensajes seguros de texto auto-destruibles. Privox cuenta con tecnología anti-capturas y borrado remoto automático.',
    image: '/assets/chat_interface.png',
    icon: MessageSquare,
    color: '#ec4899'
  },
  {
    id: 'distorsionador',
    title: 'Distorsionador de Voz',
    description: 'Oculta tu identidad vocal durante llamadas activas. Aplica efectos instantáneos como robot, alienígena, hombre o mujer en tiempo real.',
    image: '/assets/distorsion_voz.png',
    icon: Mic,
    color: '#10b981'
  },
  {
    id: 'control',
    title: 'Control de Contactos',
    description: 'Tú decides quién puede comunicarse contigo. Gestiona listas blancas de nodos de confianza firmadas criptográficamente.',
    image: '/assets/contactos.png',
    icon: Users,
    color: '#a855f7'
  }
];

export default function FeaturesCarousel() {
  const [activeIndex, setActiveIndex] = useState(0);
  const timerRef = useRef(null);

  const startTimer = () => {
    stopTimer();
    timerRef.current = setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % FEATURES.length);
    }, 7000);
  };

  const stopTimer = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
  };

  useEffect(() => {
    startTimer();
    return () => stopTimer();
  }, []);

  const handleSelect = (index) => {
    setActiveIndex(index);
    startTimer(); // Reset timer on manual select
  };

  const activeColor = FEATURES[activeIndex].color;

  return (
    <div
      className="features-carousel-container"
      onMouseEnter={stopTimer}
      onMouseLeave={startTimer}
    >
      <div className="features-carousel-grid">
        {/* Left Side: Tabs List */}
        <div className="features-tabs-list">
          {FEATURES.map((item, index) => {
            const Icon = item.icon;
            const isActive = index === activeIndex;
            return (
              <button
                key={item.id}
                className={`features-tab-btn glass ${isActive ? 'active' : ''}`}
                onClick={() => handleSelect(index)}
                style={isActive ? {
                  borderColor: item.color,
                  boxShadow: `0 0 20px ${item.color}22`,
                  background: 'var(--bg-card-hover)'
                } : {}}
              >
                <div
                  className="tab-icon-wrapper"
                  style={{
                    color: isActive ? item.color : 'var(--text-muted)',
                    backgroundColor: isActive ? `${item.color}15` : 'rgba(255, 255, 255, 0.02)'
                  }}
                >
                  <Icon size={24} />
                </div>
                <div className="tab-text-content">
                  <h3 style={{
                    color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                    transition: 'color 0.3s'
                  }}>
                    {item.title}
                  </h3>
                  <div className={`tab-desc-wrapper ${isActive ? 'expanded' : ''}`}>
                    <p style={{ marginTop: '0.5rem', fontSize: '0.9rem', lineHeight: '1.5' }}>
                      {item.description}
                    </p>
                  </div>
                </div>

                {/* Auto rotation progress indicator line */}
                {isActive && (
                  <div
                    className="tab-progress-bar"
                    style={{ backgroundColor: item.color }}
                  />
                )}
              </button>
            );
          })}
        </div>

        {/* Right Side: Virtual Phone Displaying Selected Feature Screenshot */}
        <div className="features-phone-display">
          <div className="phone-container" style={{
            borderColor: activeColor,
            boxShadow: `0 25px 50px -12px rgba(0, 0, 0, 0.8), 0 0 35px ${activeColor}25`
          }}>
            {FEATURES.map((item, index) => {
              const isActive = index === activeIndex;
              return (
                <img
                  key={item.id}
                  src={item.image}
                  alt={item.title}
                  className="phone-screen"
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    opacity: isActive ? 1 : 0,
                    transform: isActive ? 'scale(1) translateY(0)' : 'scale(0.95) translateY(10px)',
                    transition: 'opacity 0.6s ease-in-out, transform 0.6s ease-in-out',
                    zIndex: isActive ? 2 : 1
                  }}
                />
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
