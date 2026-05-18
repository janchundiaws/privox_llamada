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
