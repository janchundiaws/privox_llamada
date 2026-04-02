import 'dart:async';
import 'dart:convert';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:privox/main.dart';
import 'package:privox/screens/call_voice/call_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:provider/provider.dart';

class CallingScreen extends StatefulWidget {
  final String callId;
  final String fromUserId;
  final String? username;
  final bool isEmisor;
  final String? toUsername;

  const CallingScreen({
    super.key,
    required this.callId,
    required this.fromUserId,
    required this.username,
    required this.isEmisor,
    required this.toUsername,
  });

  @override
  State<CallingScreen> createState() => _CallingScreen();
}

class _CallingScreen extends State<CallingScreen> {
  late SocketService socketService;
  late AudioPlayer _player;

  @override
  void initState() {
    super.initState();
    _player = AudioPlayer();
    _playRingback();
    socketService = Provider.of<SocketService>(
      navigatorKey.currentContext!,
      listen: false,
    );
    // Bloquear orientación en landscape
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
  }

  @override
  void dispose() {
    // Restaurar orientación al salir
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    _player.stop();
    _player.dispose();
    super.dispose();
  }

  /// colgar llamada
  Future<void> hangupCall(String callId) async {
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
  
  Future<void> _playRingback() async {
    // Puedes usar un asset local o un archivo remoto
    await _player.setReleaseMode(ReleaseMode.loop); // que se repita
    await _player.play(AssetSource('sounds/call_soung.mp3'));
  }

  @override
  Widget build(BuildContext context) {
    String initials = widget.username![0];
    initials = initials.toUpperCase();
    return Consumer<SocketService>(
      builder: (context, socketService, _) {
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
                            colors: [
                              Colors.blue.shade400,
                              Colors.purple.shade400,
                            ],
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
                        widget.isEmisor
                            ? "Llamando a " +
                                  (widget.username ?? widget.fromUserId)
                            : "Llamada de " +
                                  (widget.username ?? widget.fromUserId),
                        style: const TextStyle(color: Colors.white, fontSize: 22),
                      ),
                      const Text(
                        "Llamando...",
                        style: TextStyle(color: Colors.grey),
                      ),
                    ],
                  ),
                  const SizedBox(height: 200),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 30),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        // Botón de aceptar (solo si no es emisor)
                      if (!widget.isEmisor)
                        Column(
                          children: [
                            FloatingActionButton(
                              heroTag: "btnAccept",
                              backgroundColor: Colors.green,
                              onPressed: () async {
                                await acceptCall(
                                  widget.callId,
                                  widget.fromUserId,
                                  widget.username ?? '',
                                );
                                Navigator.pushReplacement(
                                  context,
                                  MaterialPageRoute(
                                    settings: const RouteSettings(
                                      name: 'call-screen',
                                    ),
                                    builder: (context) => CallScreen(
                                      callId: widget.callId,
                                      fromUserId: widget.fromUserId,
                                      username: widget.username,
                                      isEmisor: widget.isEmisor,
                                    ),
                                  ),
                                );
                              },
                              child: const Icon(Icons.call),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              "Aceptar",
                              style: TextStyle(color: Colors.white),
                            ),
                          ],
                        ),
                        Column(
                          children: [
                            FloatingActionButton(
                              heroTag: "btnReject",
                              backgroundColor: Colors.red,
                              onPressed: () async {
                                // rechazar la llamada
                                await rejectCall(
                                  widget.callId,
                                  widget.fromUserId,
                                );
                                Navigator.pop(context);
                              },
                              child: const Icon(Icons.call_end),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              widget.isEmisor ? "Cancelar" : "Rechazar",
                              style: const TextStyle(color: Colors.white),
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
      },
    );
  }

  /// Aceptar llamada
  Future<void> acceptCall(String callId, String fromUserId, String toUsername) async {
    try {
      socketService.currentTargetUserId = fromUserId;
      final payload = {
        "type": "call-accept",
        "callId": callId,
        "from": fromUserId,
        "toUsername":toUsername
      };
      socketService.channel?.add(jsonEncode(payload));
    } catch (e) {
      print("❌ acceptCall: $e");
    }
  }

  /// Rechazar llamada
  Future<void> rejectCall(String callId, String fromUserId) async {
    try {
      // Cancelar notificación al rechazar
      flutterLocalNotificationsPlugin.cancelAll();
      
      final payload = {
        "type": "call-reject",
        "callId": callId,
        "from": fromUserId,
      };
      socketService.channel?.add(jsonEncode(payload));
      //await socketService.disposeWebRTC(fromUserId); // cierra micrófono y conexión - debe pasar a la pantalla de llamada
    } catch (e) {
      print("❌ rejectCall: $e");
    }
  }
}
