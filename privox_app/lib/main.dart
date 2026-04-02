import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:privox/screens/welcome_screen.dart';
import 'package:privox/screens/login_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/variables.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'utils/prefs.dart';

final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();
final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin = FlutterLocalNotificationsPlugin();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // initialize preferences-derived runtime values (e.g. STAY_ONLINE)
  await initPreferences();

  final prefs = await SharedPreferences.getInstance();
  final savedToken = prefs.getString('token');
  final savedUsername = prefs.getString('username');
  final savedUserId = prefs.getString('userId');
  final deviceId = prefs.getString('deviceId');

  
  const AndroidInitializationSettings initializationSettingsAndroid = AndroidInitializationSettings('@mipmap/ic_launcher');
  
  final AndroidFlutterLocalNotificationsPlugin? androidPlugin =
      flutterLocalNotificationsPlugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();

  await androidPlugin?.createNotificationChannel(
    const AndroidNotificationChannel(
      'high_importance_channel',
      'High Importance Notifications',
      description: 'This channel is used for important notifications.',
      importance: Importance.max,
      enableVibration: true,
      playSound: true,
    ),
  );

  const DarwinInitializationSettings iosPlatformChannelSpecifics = DarwinInitializationSettings(
    requestAlertPermission: true,
    requestBadgePermission: true,
    requestSoundPermission: true,
    onDidReceiveLocalNotification: null,
  );
  const InitializationSettings initializationSettings = InitializationSettings(
    android: initializationSettingsAndroid,
    iOS: iosPlatformChannelSpecifics,
  );

  WidgetsFlutterBinding.ensureInitialized();

  await flutterLocalNotificationsPlugin.initialize(initializationSettings);


  runApp(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => SocketService()),
        ],
        child: MyApp(
          savedToken: savedToken,
          savedUsername: savedUsername,
          savedUserId: savedUserId,
          deviceId: deviceId,
        ),
      )
    );
}

class MyApp extends StatefulWidget {
  final String? savedToken;
  final String? savedUsername;
  final String? savedUserId;
  final String? deviceId;

  const MyApp({super.key, this.savedToken, this.savedUsername, this.savedUserId, this.deviceId});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final socketService = Provider.of<SocketService>(context, listen: false);

        //validacion cuando este permitido el estar oline siempre y la conexion este perdida
        if (STAY_ONLINE && !socketService.isConnected && !socketService.isConnecting) {
          print("janchundia===>");
          print("[WelcomeScreen] STAY_ONLINE is enabled but socket is disconnected. Reconnecting...");
          //socketService.connect();
        }

      if (STAY_ONLINE && !socketService.isConnected) {
        final socketService = Provider.of<SocketService>(context, listen: false);
        socketService.disconnect();
        socketService.connect();
      }
    });
  }

 /// colgar llamada
  Future<void> hangupCall(String callId, String fromUserId) async {
    try {
      final socketService = Provider.of<SocketService>(navigatorKey.currentContext!,listen: false,);
      final payload = {
        "type": "hangup",
        "callId": callId,
        "from": fromUserId,
        "to": socketService.currentTargetUserId,
      };
      socketService.channel?.add(jsonEncode(payload));
    } catch (e) {
      print("❌ hangup: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool logged = widget.savedToken != null && widget.savedUsername != null && widget.deviceId != null;

    return MaterialApp(
      navigatorKey: navigatorKey,
      debugShowCheckedModeBanner: false,
      title: 'Comunicate - Login',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: logged
          ? WelcomeScreen(username: widget.savedUsername!, userId: widget.savedUserId!, deviceId: widget.deviceId!)
          : const LoginScreen(),
    );
  }
}

