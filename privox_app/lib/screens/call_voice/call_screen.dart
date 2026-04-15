import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:privox/main.dart';
import 'package:privox/services/socket_service.dart';
import 'package:proximity_sensor/proximity_sensor.dart';
import 'package:provider/provider.dart';

class CallScreen extends StatefulWidget {
  final String callId;
  final String fromUserId;
  final String? username;
  final bool isEmisor;

  const CallScreen({
    super.key,
    required this.callId,
    required this.fromUserId,
    this.username,
    required this.isEmisor,
  });

  @override
  State<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends State<CallScreen> {
  static const _proximityChannel = MethodChannel('proximity_wakelock');
  final socketService = Provider.of<SocketService>(navigatorKey.currentContext!,listen: false,);
  late Timer _timer;
  Duration _callDuration = Duration.zero;
  //variable para controlar el altavoz y micrófono
  bool _speakerOn = false;
  bool _muted = false;

  Timer? _statsTimer;
  String _statusConection = "Conectando...";

  //Sensor de proximidad
  StreamSubscription<dynamic>? _proximitySubscription;
  bool _isNear = false;
  bool _isProximityWakeLockEnabled = false;

  // Distorsión de voz (solo Android) — controla RobotVoiceProcessor nativo
  static const _distortionChannel = MethodChannel('voice_distortion');
  bool _distortionEnabled = false;

  @override
  void initState() {
    super.initState();
    _initRenderers();
    _startRemoteAudioStats();
    _initProximitySensor();
    _disableDistortion(); // desactiva distorsión al inicio de la llamada
    _toggleSpeaker(); // activa altavoz al inicio de la llamada
    flutterLocalNotificationsPlugin.cancelAll();
    // Bloquear orientación en landscape
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
  }

  Future<void> _initRenderers() async {
    try {
      await socketService.localRenderer.initialize();
      await socketService.remoteRenderer.initialize();

      await socketService.initWebRTC(
        widget.fromUserId,
        isEmisor: widget.isEmisor,
      );
      //iniciacion para colgar la llamada
      socketService.currentTargetUserId = widget.fromUserId;
    } catch (e) {
      print("Error _initRenderers" + e.toString());
    }
  }

  // ── Distorsión de voz (Android only) ───────────────────────────────────────
  // Activa/desactiva RobotVoiceProcessor en el pipeline nativo de WebRTC.
  // El procesador modifica el buffer PCM-16bit en-memoria, antes de que
  // el frame sea codificado y enviado al peer remoto.

  Future<void> _enableDistortion() async {
    if (!Platform.isAndroid) return;
    try {
      await _distortionChannel.invokeMethod('enable');
      if (mounted) setState(() => _distortionEnabled = true);
      print('🤖 Distorsión activada');
    } catch (e) {
      print('❌ Error activando distorsión: $e');
    }
  }

  Future<void> _disableDistortion() async {
    if (!Platform.isAndroid) return;
    try {
      await _distortionChannel.invokeMethod('disable');
      if (mounted) setState(() => _distortionEnabled = false);
      print('🔇 Distorsión desactivada');
    } catch (e) {
      print('❌ Error desactivando distorsión: $e');
    }
  }

  Future<void> _toggleDistortion() async {
    if (_distortionEnabled) {
      await _disableDistortion();
    } else {
      await _enableDistortion();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────

  void _initProximitySensor() async {
    try {
      // Escuchar eventos de proximidad
      _proximitySubscription = ProximitySensor.events.listen((int event) {
        if (mounted) {
          setState(() {
            _isNear = event > 0;
          });
          print("📱 Proximidad: ${_isNear ? 'Cerca (pantalla apagada)' : 'Lejos (pantalla encendida)'}");
        }
      });

      // Habilitar el wake lock solo si NO está el altavoz activado
      if (!_speakerOn) {
        await _enableProximityWakeLock();
      }
    } catch (e) {
      print("❌ Error al inicializar sensor de proximidad: $e");
    }
  }

  Future<void> _enableProximityWakeLock() async {
    if (_isProximityWakeLockEnabled) return;
    
    try {
      await _proximityChannel.invokeMethod('enable');
      _isProximityWakeLockEnabled = true;
      print("✅ ProximityWakeLock habilitado");
    } catch (e) {
      print("❌ Error al habilitar ProximityWakeLock: $e");
    }
  }

  Future<void> _disableProximityWakeLock() async {
    if (!_isProximityWakeLockEnabled) return;
    
    try {
      await _proximityChannel.invokeMethod('disable');
      _isProximityWakeLockEnabled = false;
      print("✅ ProximityWakeLock deshabilitado");
    } catch (e) {
      print("❌ Error al deshabilitar ProximityWakeLock: $e");
    }
  }

  @override
  void dispose() async {
    // Deshabilitar el wake lock de proximidad
    await _disableProximityWakeLock();
    await _disableDistortion();
    
    // Cancelar suscripción al sensor
    await _proximitySubscription?.cancel();
    _proximitySubscription = null;
    // Restaurar orientación al salir
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    _timer.cancel(); // 🔑 detiene el Timer
       _statsTimer?.cancel();
    super.dispose();
  }

  void _startRemoteAudioStats() {
    try {
      _statsTimer?.cancel();
      
      // Inicia el contador de duración solo UNA VEZ
      _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted) return;
        setState(() {
          _callDuration += const Duration(seconds: 1);
        });
      });
      
      _statsTimer = Timer.periodic(const Duration(seconds: 1), (t) async {
        if (!mounted) return;
        if (socketService.peerConnection == null) return;
        final stats = await socketService.peerConnection!.getStats();

        for (final report in stats) {
          if (report.type == 'inbound-rtp' &&
              report.values['kind'] == 'audio') {
            if (!mounted) return;
            setState(() {
              _statusConection = "En llamada - ${_formatDuration(_callDuration)}";
            });
          }
        }
      });
    } catch (e) {
      print("❌ _startRemoteAudioStats error: $e");
    }
  }

  String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return "$minutes:$seconds";
  }

  @override
  Widget build(BuildContext context) {
    String initials = (widget.username != null && widget.username!.isNotEmpty) 
        ? widget.username![0].toUpperCase() 
        : '?';

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                children: [
                  const SizedBox(height: 100),
                  // Avatar
                  Container(
                    width: 200,
                    height: 200,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [Colors.blue.shade400, Colors.purple.shade400],
                      ),
                    ),
                    child: Center(
                      child: Text(
                        initials,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 100,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 80),
                  Text(
                    widget.username ?? widget.fromUserId,
                    style: const TextStyle(color: Colors.white, fontSize: 22),
                  ),
                  Text(
                    _statusConection,//"En llamada - ${_formatDuration(_callDuration)}",
                    style: TextStyle(color: Colors.grey),
                  ),
                ],
              ),
              const SizedBox(height: 200),
              // Positioned(
              //   bottom: 20,
              //   left: 20,
              //   right: 20,
              //   child: Container(
              //     padding: const EdgeInsets.all(12),
              //     decoration: BoxDecoration(
              //       color: Colors.black.withOpacity(0.6),
              //       borderRadius: BorderRadius.circular(12),
              //     ),
              //     child: Column(
              //       crossAxisAlignment: CrossAxisAlignment.start,
              //       children: [
              //         Text(_remoteAudioStats,
              //             style: const TextStyle(color: Colors.greenAccent)),
              //         const SizedBox(height: 4),
              //         Text(_localAudioStats,
              //             style: const TextStyle(color: Colors.blueAccent)),
              //       ],
              //     ),
              //   ),
              // ),
              Padding(
                padding: const EdgeInsets.only(bottom: 30),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    Column(
                      children: [
                        FloatingActionButton(
                          heroTag: "btnMute",
                          backgroundColor: _muted ? Colors.red : Colors.grey[800],
                          onPressed: _toggleMute,
                          child: Icon(
                            _muted ? Icons.mic_off : Icons.mic,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text("Mute", style: const TextStyle(color: Colors.white)),
                      ],
                    ),
                    if (Platform.isAndroid)
                      Column(
                        children: [
                          FloatingActionButton(
                            heroTag: "btnDistortion",
                            backgroundColor:
                                _distortionEnabled ? Colors.deepPurple : Colors.grey[800],
                            onPressed: _toggleDistortion,
                            child: Icon(
                              _distortionEnabled
                                  ? Icons.spatial_audio
                                  : Icons.spatial_audio_off,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(height: 8),
                          const Text('Voz Robot',
                              style: TextStyle(color: Colors.white, fontSize: 12)),
                        ],
                      ),
                    Column(
                      children: [
                        FloatingActionButton(
                          heroTag: "btnSpeaker",
                          backgroundColor: _speakerOn
                              ? Colors.blue
                              : Colors.grey[800],
                          onPressed: _toggleSpeaker,
                          child: const Icon(Icons.volume_up, color: Colors.white),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          "Altavoz",
                          style: TextStyle(color: Colors.white),
                        ),
                      ],
                    ),
                    Column(
                      children: [
                        FloatingActionButton(
                          backgroundColor: Colors.red,
                          onPressed: () async {
                            await hangupCall(widget.callId);
                          },
                          child: const Icon(Icons.call_end),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          "Colgar",
                          style: TextStyle(color: Colors.white),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// colgar llamada
  Future<void> hangupCall(String callId) async {
    print(widget.fromUserId);
    try {
      await socketService.disposeWebRTC(
        widget.fromUserId,
      ); // cierra micrófono y conexión
      final payload = {
        "type": "hangup",
        "callId": callId,
        "from": widget.fromUserId,
        "to": socketService.currentTargetUserId,
      };
      socketService.channel?.add(jsonEncode(payload));
      Navigator.pop(context);
    } catch (e) {
      print("❌ hangup: $e");
    }
  }

  Future<void> _toggleSpeaker() async {
    _speakerOn = !_speakerOn;
    await Helper.setSpeakerphoneOn(_speakerOn);
    
    // Controlar el sensor de proximidad según el estado del altavoz
    if (_speakerOn) {
      // Si se activa el altavoz, desactivar el sensor de proximidad
      await _disableProximityWakeLock();
      print("🔊 Altavoz activado - Proximidad deshabilitada");
    } else {
      // Si se desactiva el altavoz, activar el sensor de proximidad
      await _enableProximityWakeLock();
      print("🔊 Altavoz desactivado - Proximidad habilitada");
    }
    
    if (mounted) setState(() {});
  }

  Future<void> _toggleMute() async {
    if (socketService.localStream != null) {
      for (var track in socketService.localStream!.getAudioTracks()) {
        track.enabled = _muted; // si estaba muteado, lo habilito
      }
    }
    setState(() {
      _muted = !_muted;
    });
    print("🎤 Micrófono ${_muted ? 'muteado' : 'activo'}");
  }
}
