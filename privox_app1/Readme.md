# Privox App — Documentación Técnica

Aplicación Android de llamadas de voz con distorsión de audio en tiempo real sobre WebRTC.

---

## 📁 Estructura del Proyecto

```
privox_app1/
├── app/src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt               # Configuración de compilación NDK
│   │   └── native-lib.cpp               # Motor de pitch shift en C++ (JNI)
│   ├── java/com/example/privox_app1/
│   │   ├── AudioDistortionEngine.kt     # Motor DSP principal (Kotlin)
│   │   ├── MainActivity.kt              # Activity principal y navegación
│   │   ├── data/remote/
│   │   │   ├── SocketService.kt         # WebRTC + WebSocket + intercepción de audio
│   │   │   ├── AuthService.kt           # Autenticación
│   │   │   └── Constants.kt             # URLs base
│   │   └── ui/screens/                  # Pantallas Compose
│   └── AndroidManifest.xml
```

---

## 🎙️ Pipeline de Distorsión de Voz

La distorsión de voz usa un **pipeline híbrido** (Kotlin + C++ nativo vía JNI) que intercepta el audio del micrófono *antes* de que WebRTC lo transmita al otro extremo.

### Diagrama del Flujo

```
┌─────────────┐
│ 🎤 Micrófono │
└──────┬──────┘
       │  PCM raw (int16, 48kHz, mono)
       ▼
┌─────────────────────────────────────────┐
│  JavaAudioDeviceModule (ADM)            │
│  onAudioDataRecorded() callback         │
│  SocketService.kt : líneas 111–134      │
└──────┬──────────────────────────────────┘
       │
       ├─── isDistortionEnabled = false ──► Audio sin procesar
       │
       ▼  isDistortionEnabled = true
┌─────────────────────────────────────────┐
│  AudioDistortionEngine                  │
│  .processByteBuffer()                   │
│  ByteBuffer → ShortArray → processFrame │
└──────┬──────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────┐
│  processPitchShift() — JNI → C++        │
│  native-lib.cpp                         │
│  Resampling por interpolación lineal    │
│  step = 1.0 / pitchFactor               │
└──────┬──────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────┐
│  Filtros de carácter (por modo)         │
│  AudioDistortionEngine.kt               │
│  RobotState / AlienState / VocoderState │
│  formantCorrection()                    │
└──────┬──────────────────────────────────┘
       │  Double → Short, clamp [-32768, 32767]
       ▼
┌─────────────────────────────────────────┐
│  Buffer modificado in-place             │
│  WebRTC ADM lee el audio ya procesado   │
└──────┬──────────────────────────────────┘
       │  Codificado como Opus / SRTP / UDP
       ▼
┌─────────────────┐
│ 🌐 Receptor     │
└─────────────────┘
```

---

## ⚙️ Etapas del Pipeline

### 1. Captura e Intercepción — `JavaAudioDeviceModule`
**Archivo:** `SocketService.kt` (líneas 111–134)

```kotlin
val adm = JavaAudioDeviceModule.builder(context)
    .setAudioSource(MediaRecorder.AudioSource.MIC)
    .setAudioRecordDataCallback(object : AudioRecordDataCallback {
        override fun onAudioDataRecorded(format: Int, channels: Int, rate: Int, buffer: ByteBuffer) {
            if (isDistortionEnabled) {
                distortionEngine.processByteBuffer(buffer, buffer.remaining(), currentDistortionMode)
            }
        }
    })
    .createAudioDeviceModule()
```

> **Punto clave:** El buffer se modifica **in-place en memoria**. WebRTC lee el mismo buffer ya procesado sin saber que fue alterado.

Los procesamientos nativos de WebRTC están desactivados intencionalmente para no interferir con el DSP personalizado:

```kotlin
audioConstraints.mandatory.add(KeyValuePair("echoCancellation", "false"))
audioConstraints.mandatory.add(KeyValuePair("noiseSuppression", "false"))
audioConstraints.mandatory.add(KeyValuePair("autoGainControl", "false"))
```

---

### 2. Conversión de Formato — `processByteBuffer()`
**Archivo:** `AudioDistortionEngine.kt` (líneas 157–170)

| Paso | Operación |
|------|-----------|
| Entrada | `ByteBuffer` (PCM 16-bit, orden nativo) |
| Conversión | `ByteBuffer → ShortArray` vía `asShortBuffer().get()` |
| Procesamiento | `processFrame()` |
| Salida | `ShortArray → ByteBuffer` (escritura in-place) |

---

### 3. Normalización — `processFrame()`
**Archivo:** `AudioDistortionEngine.kt` (líneas 172–198)

```
Short (int16)  ÷ 32768.0  →  Double en [-1.0, 1.0]
```

Si el modo es `NONE`, el frame se devuelve sin modificar. Para todos los demás modos, se llama primero al motor C++ de pitch shift.

---

### 4. Pitch Shift Nativo — `processPitchShift()` (C++)
**Archivo:** `native-lib.cpp`

Algoritmo de **resampling por interpolación lineal**:

```
step    = 1.0 / pitchFactor
readPos = 0.0

Para cada sample de salida i:
    intPos  = floor(readPos)
    frac    = readPos - intPos
    output  = sample[intPos] * (1 - frac) + sample[intPos+1] * frac
    readPos += step
```

- `pitchFactor < 1.0` → avanza más lento → voz más **grave**
- `pitchFactor > 1.0` → avanza más rápido → voz más **aguda**
- `pitchFactor ≈ 1.0` → bypass (sin cambio)

#### Factores por Modo

| Modo | pitchFactor | Efecto |
|------|:-----------:|--------|
| `NONE` | 1.00 | Sin cambio |
| `ROBOT` | 0.70 | Muy grave |
| `VOCODER` | 0.85 | Ligeramente grave |
| `MAN` | 0.78 | Voz masculina profunda |
| `PITCH` | 1.30 | Más agudo |
| `FEMALE` | 1.42 | Voz femenina |
| `ALIEN` | 1.45 | Alienígena |
| `SQUIRREL` | 1.85 | Ardilla (Chipmunks) |

---

### 5. Filtros de Carácter (post pitch-shift)
**Archivo:** `AudioDistortionEngine.kt`

#### 🤖 ROBOT — `RobotState`
- **Técnica:** Ring Modulation (modulación en amplitud)
- **Portadora:** `carrier = 0.7 + 0.3 · sin(2π · 50Hz · t)`
- **Aplicación:** `sample *= carrier`
- **Efecto:** Timbre robótico metálico

#### 👽 ALIEN — `AlienState`
- **Técnica:** Chorus con delay dinámico
- **LFO:** 1.5 Hz, profundidad ±20 muestras (buffer circular de 1024)
- **Mezcla:** `70% directo + 30% delayed`
- **Efecto:** Sonido espacial/etéreo

#### 📻 VOCODER — `VocoderState`
- **BandPass:** 400–3500 Hz (rango de voz telefónica)
- **Bitcrushing:** Cuantización a 4 bits: `round(x · 16) / 16`
- **Ganancia:** `× 0.9`
- **Efecto:** Voz digital/robótica con textura cuantizada

#### 🐿️ SQUIRREL — `formantCorrection()`
- **BandPass:** 380–4800 Hz (espectro más brillante)
- **Soft-clip:** `tanh(wet · 0.85)` — suaviza sin distorsión dura
- **Mezcla Dry/Wet:** 50% señal original + 50% procesada
- **Ganancia final:** `× 0.95`
- **Efecto:** Voz tipo "Alvin y las ardillas", inteligible

#### 👩 FEMALE — `formantCorrection()`
- **HighPass:** @ 80 Hz (atenúa frecuencias graves)
- **Soft-clip:** `tanh(x · 1.1)`
- **Efecto:** Voz femenina con brillo en agudos

#### 👨 MAN — `formantCorrection()`
- **Saturación suave:** `output = x · 0.9 + sin(x · 2.5) · 0.05`
- **Efecto:** Añade armónicos bajos para voz más grave y corpulenta

---

### 6. Activación Automática — `MainActivity.kt`
**Líneas 150–163**

```kotlin
LaunchedEffect(currentScreen) {
    if (currentScreen == "CallingIncoming" ||
        currentScreen == "CallingOutgoing" ||
        currentScreen == "Call") {

        val savedStyle = prefs.getString("voice_style", "ROBOT") ?: "ROBOT"
        val mode = DistortionMode.valueOf(savedStyle)

        socketService.currentDistortionMode = mode
        socketService.isDistortionEnabled   = true
    }
}
```

> La distorsión se activa **automáticamente** al iniciar cualquier fase de llamada, usando el estilo guardado en `SharedPreferences` con la clave `voice_style`.

---

### 7. Transmisión — `PeerConnection`
**Archivo:** `SocketService.kt` (líneas 392–474)

El ADM con el callback de distorsión se inyecta directamente en la `PeerConnectionFactory`. WebRTC toma el audio ya procesado y lo codifica (Opus) para transmitir por UDP/SRTP.

```kotlin
peerConnectionFactory = PeerConnectionFactory.builder()
    .setOptions(options)
    .setAudioDeviceModule(adm)  // ← ADM con distorsión integrada
    .createPeerConnectionFactory()
```

---

## 🧰 Motor Local para Pruebas

`AudioDistortionEngine.start()` puede operar en **modo local** (sin llamada activa):

```
Mic (AudioRecord) → processFrame() → Speaker (AudioTrack)
```

Disponible desde la pantalla `VoiceChangerTestScreen` en la tab de la app.

---

## 🔧 Primitivas DSP Internas

| Clase | Tipo | Parámetros |
|-------|------|-----------|
| `HighPassState` | Filtro IIR 1er orden | `cutoffHz` (default: 80 Hz) |
| `BandPassState` | HP + LP en cascada | `lowHz`, `highHz` |
| `RobotState` | Ring Modulation | portadora 120 Hz |
| `AlienState` | Chorus + delay | LFO 1.5 Hz, buffer 1024 |
| `VocoderState` | BandPass + bitcrush | 400–3500 Hz, 4-bit |

---

## 📌 Notas Importantes

- El pipeline está diseñado para **48 kHz, mono, PCM 16-bit** (`FRAME_SIZE = 1024` samples).
- La distorsión introduce una **latencia mínima** (solo la duración de un frame: ~21 ms a 48kHz).
- Los ICE Servers se obtienen dinámicamente desde `GET /api/ice` + fallback a `stun:stun.l.google.com:19302`.
- El auto-hangup por desconexión está configurado a **15 segundos** (`handleReconnectionTimeout`).

---

## 📱 Sensor de Proximidad Inteligente

**Archivo:** [CallScreen.kt](file:///Users/williamanchundiasoza/PRY-TRABAJO/futura/privox_llamada/privox_app1/app/src/main/java/com/futura/privox_app/ui/screens/CallScreen.kt)

Para evitar toques accidentales con el rostro (mejilla/oreja) durante una llamada de voz activa, se implementó un control inteligente usando el sensor de proximidad del dispositivo:

*   **Detección en tiempo real:** Registra un `SensorEventListener` con el tipo `Sensor.TYPE_PROXIMITY` dentro de un `DisposableEffect`.
*   **Condición del Altavoz:** La detección solo se activa si el altavoz de manos libres está **apagado** (`isSpeakerOn = false`). Si el usuario activa el manos libres, el sensor se desactiva automáticamente.
*   **Bloqueo de Pantalla Físico y Lógico:**
    *   **Lógico:** Al detectar que el usuario acerca el dispositivo al oído, se superpone un overlay opaco de color negro a pantalla completa con el máximo nivel de profundidad (`zIndex(Float.MAX_VALUE)`). Este overlay intercepta y consume todos los eventos táctiles utilizando el modificador `.clickable(...)` sin efecto de onda (ripple), protegiendo botones críticos como colgar, silenciar o pausar.
    *   **Físico:** Utiliza de forma complementaria el `PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK` para apagar físicamente la pantalla en dispositivos compatibles.

---

## 🛜 Reconexión Automática del WebSocket (Resiliencia de Red)

**Archivo:** [SocketService.kt](file:///Users/williamanchundiasoza/PRY-TRABAJO/futura/privox_llamada/privox_app1/app/src/main/java/com/futura/privox_app/data/remote/SocketService.kt)

En entornos móviles donde las conexiones de red son propensas a interrupciones rápidas (cambios de Wi-Fi a datos móviles o pérdida momentánea de señal), se incorporó un motor de auto-reconexión para la señalización:

*   **Intercepción de Fallos:** Los eventos `onClosed` (desconexiones anormales) y `onFailure` (errores de socket como *Software caused connection abort*) inician un hilo de reconexión.
*   **Reintentos Programados:** Utiliza una tarea en segundo plano (`wsReconnectionJob`) que intenta volver a conectar de forma segura tras un intervalo de **5 segundos**.
*   **Control de Estado:** Asegura no colapsar el servidor limpiando cualquier cola de reconexión activa si la conexión se realiza de forma manual o si el usuario cierra su sesión deliberadamente (`logout()`).

---

## 🔔 Soporte de Servicio en Segundo Plano (Android 12+)

**Archivo:** [IncomingCallService.kt](file:///Users/williamanchundiasoza/PRY-TRABAJO/futura/privox_llamada/privox_app1/IncomingCallService.kt)

A partir de Android 12 (API 31+), Google introdujo restricciones severas sobre la ejecución de servicios en primer plano, prohibiendo `startForegroundService()` si la aplicación se encuentra en segundo plano bajo ciertas condiciones (lanzando `ForegroundServiceStartNotAllowedException`).

Para mitigar cierres inesperados (crashes) y asegurar la recepción de llamadas entrantes:
*   **Bloque Try-Catch Seguro:** El inicio del servicio se encuentra encapsulado. Si el sistema operativo deniega la instanciación del servicio en primer plano, el error se captura de manera silenciosa para evitar la excepción fatal.
*   **Fallback Inmediato:** En caso de fallo, la aplicación muestra directamente la notificación de llamada entrante en alta prioridad (Heads-up Notification) mediante el `NotificationManager` del contexto.
*   **Ciclo de Vida Consistente:** Las llamadas de limpieza y parada del servicio están protegidas para ejecutarse con seguridad en cualquier estado en el que termine la llamada.

---

## 🔒 Políticas de Privacidad Integradas

**Archivo:** [SettingsScreen.kt](file:///Users/williamanchundiasoza/PRY-TRABAJO/futura/privox_llamada/privox_app1/app/src/main/java/com/futura/privox_app/ui/screens/SettingsScreen.kt)

Para cumplir con políticas de privacidad estrictas y aportar transparencia a los usuarios, se diseñó e integró un panel completo de información legal y técnica dentro de la sección de Configuración:

*   **Ejes de Privacidad Documentados:**
    1.  **Registro Anónimo:** Detalla la creación automática de cuentas con IDs aleatorios, eliminando la necesidad de datos personales (correos, nombres reales, números telefónicos).
    2.  **Motor DSP Local:** Confirma que el procesamiento de distorsión de voz ocurre localmente en memoria sin almacenar grabaciones.
    3.  **Cifrado P2P:** Explica el funcionamiento de la comunicación WebRTC cifrada directa entre usuarios.
    4.  **Justificación de Permisos:** Explica por qué son requeridos los permisos de Micrófono, Notificaciones, Proximity Sensor y Estado de Red.
    5.  **Seguridad Local:** Declara el almacenamiento exclusivo de credenciales y efectos dentro de `SharedPreferences` sin cookies de rastreo de terceros.
*   **Diseño Visual Premium:** Cuenta con un modal estilizado que incluye una cabecera con un escudo de seguridad y listados horizontales estructurados con iconos temáticos específicos para cada punto (`Security`, `AccountCircle`, `Mic`, `Lock`, `Build`, `Storage`, `AssignmentInd`), usando una paleta minimalista y moderna.

