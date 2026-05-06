import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:privox/main.dart';
import 'package:privox/screens/call_voice/call_screen.dart';
import 'package:privox/screens/call_voice/calling_screen.dart';
import 'package:privox/variables.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:http/http.dart' as http;

class SocketService extends ChangeNotifier {
  static final SocketService _instance = SocketService._internal();
  factory SocketService() => _instance;
  SocketService._internal();
  final remoteRenderer = RTCVideoRenderer(); // aunque sea audio, este reproduce streams
  final localRenderer = RTCVideoRenderer(); // aunque sea audio, este reproduce streams
  WebSocket? channel;
  RTCPeerConnection? peerConnection;
  MediaStream? localStream;
  bool isConnected = false;
  bool isConnecting = false;
  String? message;
  bool callInProgress = false;
  String? currentTargetUserId;
  String? currentTargetUsername; // 🆕 Guardar username del destinatario
  bool remoteAudioActive = false;
  String? missedCallId;
  String currentCallId = "";

  Map<String, String> _usersCache = {};

  final _eventController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get events => _eventController.stream;
  // Buffer para ICE candidates que llegan antes de setRemoteDescription
  final List<RTCIceCandidate> _pendingCandidates = [];

  Future<void> connect() async {
    try {
      final ctx = navigatorKey.currentContext;
      isConnecting = true;
      notifyListeners();
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');

      String wsBase = URL_API;
      if (wsBase.startsWith('https://')) {
        wsBase = wsBase.replaceFirst('https://', 'wss://');
      } else if (wsBase.startsWith('http://')) {
        wsBase = wsBase.replaceFirst('http://', 'ws://');
      }
      wsBase = wsBase.replaceAll(RegExp(r'/$'), '');

      final uri = Uri.parse('$wsBase?token=$token').toString();
      channel = await WebSocket.connect(
        uri,
      ).timeout(const Duration(seconds: 10));
      message = "✅ Conectado";
      isConnected = true;
      isConnecting = false;
      notifyListeners();

      channel!.listen((message) async {
        final data = jsonDecode(message);
        _eventController.add(data);

        switch (data['type']) {
          case 'incoming-call':
            final username = await _getUsernameById(data['from']);
            
            if (NOTIFICATIONS_ENABLED) {
                const AndroidNotificationDetails androidPlatformChannelSpecifics =
                    AndroidNotificationDetails(
                  'call_channel', // id único del canal
                  'Llamadas',
                  channelDescription: 'Notificaciones de llamadas entrantes',
                  importance: Importance.max,
                  priority: Priority.high,
                  fullScreenIntent: true,
                  ticker: 'Llamada entrante',
                );

                const NotificationDetails platformChannelSpecifics = NotificationDetails(android: androidPlatformChannelSpecifics);

                await flutterLocalNotificationsPlugin.show(
                  0, // id de la notificación
                  '📞 Llamada entrante',
                  '$username está llamando...',
                  platformChannelSpecifics,
                  payload: data['callId'],
                );
            }

            currentCallId = data['callId'];
            final ctx = navigatorKey.currentContext;
            if (ctx != null) {
              Navigator.push(
                ctx,
                MaterialPageRoute(
                  settings: const RouteSettings(name: 'calling-screen'),
                  builder: (_) => CallingScreen(
                    callId: data['callId'],
                    fromUserId: data['from'],
                    username: username,
                    toUsername: data['toUsername'],
                    isEmisor: false,
                  ),
                ),
              );
            }
            break;
          case 'call-accepted':
            if (currentTargetUserId != null) {
              await _startOffer(currentTargetUserId!);
            } else {
              print("⚠️ No se conoce el destino de la llamada");
            }
            //primero cierro la pantalla de calling screen
            if (ctx != null) {
              Navigator.of(ctx).popUntil((route) {
                return route.settings.name != "calling-screen";
              });
            }
            if (ctx != null) {
              Navigator.push(
                ctx,
                MaterialPageRoute(
                  settings: const RouteSettings(name: 'call-screen'),
                  builder: (_) => CallScreen(
                    callId: data['callId'],
                    fromUserId: data['from'] ?? currentTargetUserId,
                    username: currentTargetUsername, // 🆕 Usar el username guardado
                    isEmisor: true,
                  ),
                ),
              );
            }
            break;
          case 'call-reject':
            message = "❌ Llamada rechazada servicio: ${data['callId']}";
            flutterLocalNotificationsPlugin.cancelAll();
            notifyListeners();
            //aqui debo cerrar una pantalla en especifico siempre y cuando exista
            if (ctx != null) {
              Navigator.of(ctx).popUntil((route) {
                return route.settings.name != "calling-screen";
              });

              await disposeWebRTC(data['callId']);
              Navigator.of(ctx).popUntil((route) {
                return route.settings.name != "call-screen";
              });
            }
            break;
          case 'hangup':
          flutterLocalNotificationsPlugin.cancelAll();
            await disposeWebRTC(data['callId']);
            //aqui debo cerrar una pantalla en especifico siempre y cuando exista
            if (ctx != null) {
              Navigator.of(ctx).popUntil((route) {
                return route.settings.name != "call-screen";
              });
              
              Navigator.of(ctx).popUntil((route) {
                return route.settings.name != "calling-screen";
              });
            }
            break;
          case 'offer':
          case 'answer':
          case 'ice':
            await handleSignal(data);
            break;
          case 'call-missed':
            missedCallId = data['callId'];
            message = "❌ Llamada perdida servicio: ${data['callId']}";
            notifyListeners();
            break;
          case 'peer-offline':
            // Alert(
            //   context: context,
            //   desc: "⚠️ Usuario destino offline: ${data['to']}",
            // ).show();
            print("⚠️ Usuario destino offline: ${data['to']}");
            break;
        }
      }, onDone: () {
        isConnected = false;
        message = "❌ Desconectado del servidor";
        notifyListeners();
      }, onError: (error) {
        isConnected = false;
        message = "❌ Error en la conexión: $error";
        notifyListeners();
      });
      notifyListeners();
    } catch (e) {
      isConnected = false;
      isConnecting = false;
      notifyListeners();
      message = "❌ Error al conectar: $e";
      print("❌ Error al conectar socket: $e");
    }
  }

  Future<void> disconnect() async {
    try {
      await channel?.close();
      isConnected = false;
      message = "❌ Desconectado";
      notifyListeners();
    } catch (e) {
      message = "Desconectado $e";
      notifyListeners();
    } finally {
      channel = null;
    }
  }

  // Método para obtener el username por ID de usuario (con caché)

  Future<String?> _getUsernameById(String userId) async {
    // Verificar caché primero
    if (_usersCache.containsKey(userId)) {
      return _usersCache[userId];
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');
      final uri = Uri.parse('${URL_API}api/users/usersaccount');
      final Map<String, String> headers = {
        'Accept': '*/*',
        'User-Agent': 'GhoxClient/1.0',
      };
      if (token != null && token.isNotEmpty) {
        headers['Authorization'] = 'Bearer $token';
      }
      final response = await http
          .get(uri, headers: headers)
          .timeout(const Duration(seconds: 5));
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final body = json.decode(response.body);
        List users = [];
        if (body is Map && body.containsKey('users') && body['users'] is List) {
          users = body['users'];
        } else if (body is List) {
          users = body;
        }
        // Actualizar caché con todos los usuarios
        //_lastCacheUpdate = DateTime.now();
        for (final user in users) {
          if (user is Map && user['userId'] != null) {
            final id = user['userId'].toString();
            final displayName = user['displayName']?.toString();
            final username = user['username']?.toString();
            final name = username?.isNotEmpty == true
                ? username
                : displayName;
            if (name?.isNotEmpty == true) {
              _usersCache[id] = name!;
            }
          }
        }
        // Devolver el usuario solicitado
        return _usersCache[userId];
      }
    } catch (e) {
      print("❌ Error obteniendo username para $userId: $e");
    }
    return null; // Si no se encuentra, devolver null
  }

  Future<void> _startOffer(String toUserId) async {
    try {
      if (peerConnection == null) {
        print("⚠️ No hay peerConnection, initWebRTC primero");
        return;
      }
      final offer = await peerConnection!.createOffer();
      await peerConnection!.setLocalDescription(offer);
      // if (peerConnection != null &&
      //     peerConnection?.signalingState != RTCSignalingState.RTCSignalingStateClosed) {
      //   await peerConnection!.setLocalDescription(offer);
      // } else {
      //   print("⚠️ Ignoro setLocalDescription: peer cerrado");
      // }
      channel?.add(jsonEncode({...offer.toMap(), 'to': toUserId}));
      print("📤 Offer enviada al usuario $toUserId");
    } catch (e) {
      print("❌ _startOffer error: $e");
    }
  }

  Future<List<Map<String, dynamic>>> _getIceServers() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');
      final uri = Uri.parse('${URL_API}api/ice');
      final Map<String, String> headers = {
        'Accept': 'application/json',
        'User-Agent': 'GhoxClient/1.0',
      };
      if (token != null && token.isNotEmpty) {
        headers['Authorization'] = 'Bearer $token';
      }
      
      final response = await http
          .get(uri, headers: headers)
          .timeout(const Duration(seconds: 5));
      
      if (response.statusCode >= 200 && response.statusCode < 300) {
        final body = json.decode(response.body);
        if (body is Map && body.containsKey('iceServers')) {
          print("🌐 Servidores ICE obtenidos: ${body['iceServers']}");
          return List<Map<String, dynamic>>.from(body['iceServers']);
        }
      }
      print("⚠️ Error obteniendo servidores ICE, código: ${response.statusCode}");
    } catch (e) {
      print("❌ Error obteniendo servidores ICE: $e");
    }
    
    // Fallback: retornar lista vacía (sin servidores públicos)
    print("⚠️ No se pudieron obtener servidores ICE del backend");
    return [];
  }

  Future<void> initWebRTC(String toUserId, {bool isEmisor = false}) async {
    try {
      // Obtener servidores ICE desde el backend
      final iceServers = await _getIceServers();
      
      final config = {
        'iceServers': iceServers,
      };

      peerConnection = await createPeerConnection(config);

      localStream = await navigator.mediaDevices.getUserMedia({
        'audio': {
          'echoCancellation': true,
          'noiseSuppression': true,
          'autoGainControl': false,
          'googAutoGainControl': false,
          'sampleRate': 48000,
        }
      });

      for (var track in localStream!.getTracks()) {
        await peerConnection?.addTrack(track, localStream!);
      }
      localRenderer.srcObject = localStream;

      peerConnection?.onTrack = (RTCTrackEvent event) {
        if (event.streams.isNotEmpty) {
          remoteRenderer.srcObject = event.streams[0];
        }
      };

      peerConnection?.onTrack = (RTCTrackEvent event) {
        if (event.streams.isNotEmpty) {
          remoteRenderer.srcObject = event.streams[0];
          print("🎧 Stream remoto recibido: ${event.streams[0].id}");
          _startRemoteAudioStats(); // 🔑 aquí arrancas el monitoreo
        }
      };

      peerConnection?.onIceConnectionState = (state) {
        print("🌐 ICE state: $state");
      };
      peerConnection?.onConnectionState = (state) {
        print("🔗 PeerConnection state: $state");
      };

      peerConnection?.onIceCandidate = (candidate) {
        final payload = {
          'type': 'ice',
          'candidate': candidate.candidate,
          'sdpMid': candidate.sdpMid,
          'sdpMLineIndex': candidate.sdpMLineIndex,
          'to': toUserId,
        };
        channel?.add(jsonEncode(payload));
      };

      // Opcional: logs de estado
      peerConnection?.onSignalingState = (RTCSignalingState s) =>
          print("📶 Signaling: $s");

      // Si eres emisor, arranca la oferta aquí (no fuera)
      // if (currentTargetUserId == toUserId) {
      //   await _startOffer(toUserId);
      // }
      if (isEmisor) {
        await _startOffer(toUserId); // 🔑 solo emisor
      }
    } catch (e) {
      print("❌ Error al inicializar WebRTC: $e");
    }
  }

  Future<void> disposeWebRTC(String userId) async {
    try {
      _statsTimer?.cancel(); // 🔑 detiene el monitoreo
      _statsTimer = null;

      for (var track in localStream?.getTracks() ?? []) {
        track.stop();
      }
      await localStream?.dispose();
      localStream = null;

      localRenderer.srcObject = null;
      remoteRenderer.srcObject = null;

      await peerConnection?.close();
      peerConnection = null;

      _pendingCandidates.clear();
      print("✅ WebRTC cerrado para $userId");
    } catch (e) {
      print("❌ Error al cerrar WebRTC: $e");
    }
  }

  Future<void> handleSignal(Map<String, dynamic> data) async {
    switch (data['type']) {
      case 'offer':
        if (peerConnection == null) {
          print("⚠️ Recibí offer sin peer; creando peer");
          await initWebRTC(data['from']); // usar from como destino para ICE
        }
        final remoteOffer = RTCSessionDescription(data['sdp'], data['type']);
        await peerConnection?.setRemoteDescription(remoteOffer);
        // if (peerConnection?.signalingState == RTCSignalingState.RTCSignalingStateStable) {
        //   await peerConnection!.setRemoteDescription(remoteOffer);
        // } else {
        //   print("⚠️ Ignoro offer: estado ${peerConnection?.signalingState}");
        // }

        final answer = await peerConnection!.createAnswer();
        await peerConnection?.setLocalDescription(answer);

        channel?.add(jsonEncode({...answer.toMap(), 'to': data['from']}));

        // Aplicar ICE pendientes
        for (final c in _pendingCandidates) {
          await peerConnection?.addCandidate(c);
        }
        _pendingCandidates.clear();
        break;

      case 'answer':
        // Solo aplica si enviaste una oferta local
        if (peerConnection?.signalingState ==
            RTCSignalingState.RTCSignalingStateHaveLocalOffer) {
          final remoteAnswer = RTCSessionDescription(data['sdp'], data['type']);
          await peerConnection?.setRemoteDescription(remoteAnswer);

          for (final c in _pendingCandidates) {
            await peerConnection?.addCandidate(c);
          }
          _pendingCandidates.clear();
        } else {
          print("⚠️ Ignoro answer: estado ${peerConnection?.signalingState}");
        }
        break;

      case 'ice':
        final candidate = RTCIceCandidate(
          data['candidate'],
          data['sdpMid'],
          data['sdpMLineIndex'],
        );
        if (peerConnection?.getRemoteDescription() != null) {
          await peerConnection?.addCandidate(candidate);
        } else {
          _pendingCandidates.add(candidate);
          print("⏳ ICE buffer: esperando remoteDescription");
        }
        break;
    }
  }

  Future<void> resetCall() async {
    await peerConnection?.close();
    peerConnection = null;

    await localRenderer.dispose();
    await remoteRenderer.dispose();

    await localRenderer.initialize();
    await remoteRenderer.initialize();

    localStream = null;
    _pendingCandidates.clear();
  }

  Timer? _statsTimer;
  void _startRemoteAudioStats() {
    try {
      print("🚀 Iniciando monitoreo de estadísticas de audio remoto");
      _statsTimer?.cancel();
      _statsTimer = Timer.periodic(const Duration(seconds: 2), (t) async {
        if (peerConnection == null) return;
        final stats = await peerConnection!.getStats();
        // Busca reportes de inbound-rtp audio
        for (final report in stats) {
          if (report.type == 'inbound-rtp' &&
              report.values['kind'] == 'audio') {
            final bytes = report.values['bytesReceived'];
            final jitter = report.values['jitter'];
            final packets = report.values['packetsReceived'];
            print(
              "📈 Remote audio: bytes=$bytes, packets=$packets, jitter=$jitter",
            );
          }
          // Opcional: outbound-rtp para tu micrófono
          if (report.type == 'outbound-rtp' &&
              report.values['kind'] == 'audio') {
            final bytes = report.values['bytesSent'];
            final packets = report.values['packetsSent'];
            print("🎙️ Local audio: bytes=$bytes, packets=$packets");
          }
        }
      });
    } catch (e) {
      print("❌ _startRemoteAudioStats error: $e");
    }
  }
}