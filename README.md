# Llamadas de Voz - privox

Sistema de comunicación VoIP en tiempo real desarrollado con Flutter y Node.js. Permite realizar llamadas de voz peer-to-peer con señalización WebRTC y gestión de usuarios.

## 📋 Descripción General

Este proyecto implementa una solución completa de llamadas de voz que incluye:
- **Aplicación móvil multiplataforma** (Flutter) con WebRTC
- **Servidor de señalización** (Node.js + Socket.IO + Express)
- **Gestión de usuarios y autenticación** con JWT
- **Integración con Twilio** para servicios de telefonía
- **Base de datos MongoDB** para persistencia

## 🏗️ Estructura del Proyecto

```
llamadas_voz/
├── privox_app/          # Aplicación Flutter (iOS, Android, Web)
│   ├── lib/           # Código fuente Dart
│   ├── assets/        # Recursos (sonidos, imágenes)
│   └── pubspec.yaml   # Dependencias Flutter
│
└── privox_server/       # Servidor Node.js
    ├── src/           # Código fuente del servidor
    ├── docker-compose.yml
    └── package.json   # Dependencias Node.js
```

## 🚀 Tecnologías Utilizadas

### Frontend (privox_app)
- **Flutter 3.10+** - Framework multiplataforma
- **flutter_webrtc** - WebRTC para llamadas P2P
- **socket.io_client** - Conexión en tiempo real
- **provider** - Gestión de estado
- **shared_preferences** - Almacenamiento local
- **audioplayers** - Reproducción de tonos
- **flutter_local_notifications** - Notificaciones
- **permission_handler** - Permisos de sistema

### Backend (privox_server)
- **Node.js** - Runtime JavaScript
- **Express** - Framework web
- **Socket.IO** - WebSockets para señalización
- **MongoDB + Mongoose** - Base de datos
- **JWT** - Autenticación y autorización
- **Twilio** - Integración telefónica
- **Swagger** - Documentación API

## 📦 Requisitos Previos

- **Flutter SDK**: >= 3.10.0
- **Node.js**: >= 18.x
- **Docker & Docker Compose** (opcional para servidor)
- **MongoDB**: >= 6.0
- **Dispositivos**: iOS 12+, Android 6.0+ (API 23+)

## 🔧 Instalación y Configuración

### 1. Clonar el Repositorio

```bash
git clone <repository-url>
cd llamadas_voz
```

### 2. Configurar el Servidor (privox_server)

```bash
cd privox_server

# Instalar dependencias
npm install

# Configurar variables de entorno
cp .env.example .env.dev

# Editar .env.dev con tus credenciales:
# - MONGO_URI
# - JWT_SECRET
# - TWILIO_ACCOUNT_SID
# - TWILIO_AUTH_TOKEN
```

**Iniciar servidor en desarrollo:**

```bash
# Con Node directamente
npm run dev

# O con Docker
docker-compose up app_dev
```

El servidor estará disponible en `http://localhost:3000`

### 3. Configurar la Aplicación (privox_app)

```bash
cd privox_app

# Obtener dependencias
flutter pub get

# Ejecutar en modo desarrollo
flutter run

# O para plataforma específica:
flutter run -d android
flutter run -d ios
flutter run -d chrome
```

## 🌐 Ambientes de Desarrollo

El servidor soporta tres ambientes:

| Ambiente | Archivo Docker | Variables | Puerto |
|----------|---------------|-----------|--------|
| **Desarrollo** | `Dockerfile.dev` | `.env.dev` | 3000 |
| **Staging** | `Dockerfile.staging` | `.env.staging` | 3000 |
| **Producción** | `Dockerfile` | `.env` | 3000 |

### Docker Compose

```bash
# Desarrollo
docker-compose up app_dev

# Staging
docker-compose up app_staging

# Producción
docker-compose up app
```

## 📱 Funcionalidades Principales

### Aplicación Móvil
- ✅ Registro e inicio de sesión
- ✅ Lista de contactos
- ✅ Llamadas salientes
- ✅ Recepción de llamadas
- ✅ Audio bidireccional con WebRTC
- ✅ Tonos de llamada y ocupado
- ✅ Notificaciones push
- ✅ Sensor de proximidad
- ✅ Manejo de permisos (micrófono, cámara)
- ✅ Historial de llamadas

### Servidor
- ✅ API REST para autenticación
- ✅ WebSocket para señalización
- ✅ Gestión de salas (rooms)
- ✅ Sincronización de estados
- ✅ Integración Twilio
- ✅ Documentación Swagger
- ✅ Logs y monitoreo

## 🔐 Autenticación

El sistema utiliza JWT (JSON Web Tokens) para autenticación:

1. El usuario se registra/inicia sesión
2. El servidor devuelve un token JWT
3. El token se incluye en las cabeceras de las peticiones
4. El servidor valida el token en cada request

## 📡 Señalización WebRTC

Flujo de llamada:

```
Llamante                Servidor               Receptor
   |                       |                      |
   |-- offer ------------->|                      |
   |                       |-- offer ------------>|
   |                       |<- answer ------------|
   |<- answer -------------|                      |
   |-- ice-candidate ----->|-- ice-candidate ---->|
   |<- ice-candidate ------|<- ice-candidate -----|
   |                       |                      |
   |<=== Conexión P2P establecida ===============>|
```

## 🔊 Gestión de Audio

- **Tonos de llamada**: Reproducción local con `audioplayers`
- **Audio en vivo**: WebRTC peer connection
- **Formatos**: Opus codec para voz
- **Muestreo**: 48kHz optimizado para voz

## 📊 API Endpoints

### Autenticación
```
POST /api/auth/register  - Registro de usuario
POST /api/auth/login     - Inicio de sesión
GET  /api/auth/me        - Obtener usuario actual
```

### Usuarios
```
GET  /api/users          - Lista de usuarios
GET  /api/users/:id      - Obtener usuario específico
```

### WebSocket Events
```
connection              - Conexión establecida
call:offer              - Oferta de llamada
call:answer             - Respuesta a llamada
call:ice-candidate      - ICE candidate
call:end                - Finalizar llamada
```

Ver documentación completa en: `http://localhost:3000/api-docs`

## 🐳 Deployment

### Con Docker

```bash
# Build imagen de producción
docker build -t privox-server:latest .

# Run container
docker run -p 3000:3000 --env-file .env privox-server:latest
```

### Configuración TURN/STUN

Para llamadas a través de NAT, configurar servidor TURN:

```bash
# Usar archivo de configuración incluido
cd privox_server
./deploy.sh
```

Ver `privox_server/DEPLOYMENT.md` para detalles completos.

## 🧪 Testing

```bash
# Backend tests
cd privox_server
npm test

# Flutter tests
cd privox_app
flutter test
```

## 📝 Variables de Entorno

### Servidor (.env)

```env
PORT=3000
MONGO_URI=mongodb://localhost:27017/privox
JWT_SECRET=your-secret-key
JWT_EXPIRES_IN=7d

TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE_NUMBER=+1234567890

NODE_ENV=production
```

### App (configurable en código)

```dart
const API_URL = 'http://localhost:3000';
const WS_URL = 'ws://localhost:3000';
```

## 🛠️ Troubleshooting

### Problemas Comunes

**No se escucha audio:**
- Verificar permisos de micrófono
- Revisar configuración STUN/TURN
- Comprobar firewall/NAT

**Error de conexión WebSocket:**
- Verificar que el servidor esté corriendo
- Revisar URL de conexión
- Comprobar CORS en servidor

**App no compila:**
```bash
flutter clean
flutter pub get
flutter run
```

## 📄 Licencia

Proyecto privado - Todos los derechos reservados

## 👥 Contribuidores

- William Anchundia Soza

## 📞 Soporte

Para reportar issues o sugerencias, contactar al equipo de desarrollo.

---

**Versión actual:**
- privox_app: v1.0.1
- privox_server: v1.0.0

**Última actualización:** Marzo 2026
