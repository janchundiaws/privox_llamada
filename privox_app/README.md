# privox App - Aplicación de Llamadas por Voz

Aplicación Flutter para realizar llamadas de voz en tiempo real usando WebRTC, signaling por WebSocket y autenticación basada en token JWT.

## Características Implementadas

### 1. Autenticación
- **Pantalla de Login**: formulario simple que solicita solo username
- **Endpoint**: `POST /api/auth/login`
- **Persistencia**: token y username guardados en `SharedPreferences`
- **Auto-login**: la app inicia directamente en `WelcomeScreen` si existe token guardado

### 2. Pantalla de Bienvenida
- Conexión WebSocket con signaling
- Logs en pantalla de eventos de conexión/desconexión
- Navegación a `UsersListScreen` para iniciar llamadas
- Manejo de token en query params: `?token=<jwt>`

### 3. Listado de Usuarios
- Consume endpoint `GET /api/users` (requiere `Authorization: Bearer <token>`)
- Soporta múltiples estructuras JSON (`{ "users": [...] }` o array directo)
- Mapeo a modelo `User` con campos: `_id`, `userId`, `username`, `displayName`
- Renderizado de avatar con inicial, nombre y username
- Pull-to-refresh para actualizar lista

### 4. Llamadas de Voz (WebRTC)
- **Flujo completo**:
  1. Crear registro de llamada: `POST /api/calls/voice`
  2. Obtener servidores ICE: `GET /api/ice-config`
  3. Crear `RTCPeerConnection` con ICE servers
  4. Solicitar permiso de micrófono (`Permission.microphone.request()`)
  5. Capturar stream de audio local: `getUserMedia({audio: true, video: false})`
  6. Conectar WebSocket de signaling
  7. Crear y enviar oferta SDP
  8. Recibir y procesar respuesta + candidatos ICE

- **Mensajes de Signaling** (JSON por WebSocket):
  - `{ "type": "offer", "to": "userId", "sdp": "..." }`
  - `{ "type": "answer", "sdp": "..." }`
  - `{ "type": "ice", "to": "userId", "candidate": {...} }`

- **Alertas progresivas**: cada paso muestra un diálogo (creación de llamada, ICE servers, audio, offer enviada, etc.)

### 5. Gestión de Permisos

#### iOS
- `NSMicrophoneUsageDescription`: descripción de permiso de micrófono
- `NSCameraUsageDescription`: descripción de permiso de cámara (para videollamadas futuras)
- `platform :ios, '13.0'` en `Podfile` (requerido por `flutter_webrtc`)

#### Android
- `RECORD_AUDIO` en `AndroidManifest.xml`
- `CAMERA` en `AndroidManifest.xml`
- Solicitud en tiempo de ejecución usando `permission_handler`

#### Flujo de Permisos
1. Al iniciar "Llamada por voz", se solicita permiso de micrófono
2. Si está **denied**, se ofrece abrir Ajustes
3. Si está **permanentlyDenied**, se muestran instrucciones manuales
4. Botón de diagnóstico en `AppBar` (icono de privacidad) que muestra:
   - Estado actual del permiso
   - Bundle identifier (para localizar la app en Ajustes)
   - Opciones para abrir Ajustes o ver instrucciones

### 6. Dependencias Principales

```yaml
flutter_webrtc: ^0.9.28        # WebRTC peer connections
http: ^0.13.6                  # Requests HTTP
shared_preferences: ^2.1.1     # Almacenamiento local
permission_handler: ^10.4.0    # Solicitud de permisos
package_info_plus: ^4.0.0      # Info de la app (bundle id)
```

## Instalación y Setup

### Requisitos
- Flutter 3.10+
- Dart 3.10+
- iOS 13.0+
- Android 5.0+

### Instalación Local

```bash
# Clonar/descargar proyecto
cd privox_app

# Descargar dependencias
flutter pub get

# Instalar pods iOS
cd ios
pod install --repo-update
cd ..

# Ejecutar en dispositivo/emulador
flutter run
```

### Configuración de Servidor

Actualizar `lib/variables.dart` con la URL correcta del servidor:

```dart
String URL_API = "https://tu-servidor.com/";
```

## Arquitectura de la App

### Estructura de Carpetas
```
lib/
├── main.dart                      # App entry, LoginScreen, WelcomeScreen
├── variables.dart                 # Constantes (URL_API)
└── screen/
    └── users_list_screen.dart     # Lista de usuarios + lógica de llamadas
```

### Flujo de Usuario
1. **Login**: usuario ingresa username → recibe token → guardado en `SharedPreferences`
2. **Welcome Screen**: muestra logs de WebSocket, button para ver usuarios
3. **Users List**: lista consumida desde `/api/users`, cada usuario tiene opción "Llamada por voz"
4. **Llamada de Voz**: solicita permiso → WebRTC + signaling → alertas de progreso

### Headers HTTP
- `Authorization: Bearer <token>` (en `_fetchUsers()` y `_startVoiceCall()`)
- `Content-Type: application/json` (para POST)
- `User-Agent: privoxClient/1.0` (en requests GET)

## Troubleshooting

### Permiso de Micrófono no Solicita en iOS

**Síntoma**: La app muestra `PermissionStatus.permanentlyDenied` y no aparece en Ajustes → Privacidad → Micrófono

**Soluciones**:

1. **Reinstalar la app** (limpia el estado de permiso):
   ```bash
   flutter clean
   flutter pub get
   cd ios
   pod install --repo-update
   cd ..
   flutter run
   ```

2. **Resetear permisos globales** (si la app no aparece en Ajustes):
   - Ajustes → General → Transferir o Resetear iPhone → Reset → Reset Location & Privacy
   - Reinstalar la app con `flutter run`

3. **Verificar Bundle Identifier**:
   - Abrir `ios/Runner.xcworkspace` en Xcode
   - Target Runner → General → Bundle Identifier
   - Usa el icono de permisos en la app para ver el bundle id exacto y confirmarlo en Ajustes

### CocoaPods/iOS Build Issues

```bash
cd ios
pod repo update
pod install --repo-update
```

Luego eliminar derivados de build:
```bash
rm -rf ios/Pods
rm ios/Podfile.lock
cd ios
pod install --repo-update
cd ..
flutter clean
flutter pub get
```

## Configuración de Build Phases (para publicación en App Store)

Ingresa al target Runner → Build Phases → New run script phases:
```bash
FRAMEWORK_PATH="${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}/WebRTC.framework/WebRTC"
DSYM_PATH="${DWARF_DSYM_FOLDER_PATH}/WebRTC.framework.dSYM"

echo "🔧 Generando dSYM para WebRTC.framework..."

if [ -f "$FRAMEWORK_PATH" ]; then
    dsymutil "$FRAMEWORK_PATH" -o "$DSYM_PATH"
    echo "✅ dSYM generado en: $DSYM_PATH"
else
    echo "❌ No se encontró WebRTC.framework en $FRAMEWORK_PATH"
fi
```

## Endpoints Esperados del Servidor

| Método | Endpoint | Autenticación | Body | Response |
|--------|----------|---|---------|----------|
| POST | `/api/auth/login` | No | `{ "username": "..." }` | `{ "token": "...", "userId": "..." }` |
| GET | `/api/users` | JWT | - | `{ "users": [...] }` |
| POST | `/api/calls/voice` | JWT | `{ "to": "userId", "meta": {...} }` | `{ "callId": "...", "id": "..." }` |
| GET | `/api/ice-config` | JWT | - | `{ "iceServers": [...] }` |
| WS | `/` (con `?token=<jwt>`) | JWT query param | - | Mensajes JSON signaling |

## Notas de Desarrollo

- **WebSocket Signaling**: se usa token en query param por simplicidad; considera usar header en futuro
- **ICE Candidates**: se envían tan pronto como se generen; algunos servidores pueden esperar más de una batida
- **Audio-only**: `getUserMedia` configura `{ audio: true, video: false }`; para videollamadas extender a video
- **Logs de Debug**: ver console con `flutter run --verbose` o en la pantalla Welcome logs
- **Test Local**: cambiar `URL_API` a `http://localhost:3000/` para servidor local
