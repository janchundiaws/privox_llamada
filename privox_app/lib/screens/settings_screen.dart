import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:privox/screens/login_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/utils/auth.dart';
import 'package:privox/utils/prefs.dart';
import 'package:privox/variables.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsScreen extends StatefulWidget {
  final String username;

  const SettingsScreen({super.key, required this.username});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _eliminaCuenta = false;
  String version = "";

  @override
  void initState() {
    super.initState();
    obtenerVersion();
    getPreferencesInit();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<SocketService>(
      builder : (context, socketService, child) {
        return Scaffold(
          appBar: AppBar(
            title: const Text("Configuración"),
            elevation: 0,
          ),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text(
                "Cuenta",
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Card(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: SwitchListTile(
                  title: Row(
                    children: [
                      const Text("Online"),
                      if (!STAY_ONLINE) ...[
                        const SizedBox(width: 8),
                        const Icon(
                          Icons.warning_amber_rounded,
                          color: Colors.orange,
                          size: 20,
                        ),
                      ],
                    ],
                  ),
                  subtitle: const Text("Permite que otros usuarios me puedan llamar"),
                  value: STAY_ONLINE,
                  onChanged: (value) async {
                    setState(() => STAY_ONLINE = value);
                    try {
                      if (STAY_ONLINE) {
                        socketService.connect();
                      } else {
                        socketService.disconnect();
                      } 
                      await setStayOnlinePref(value);
                    } catch (e) {
                      // ignore write errors
                    }
                  },
                  secondary: const Icon(Icons.wifi_tethering),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                "Seguridad",
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),

              Card(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: Column(
                  children: [
                    SwitchListTile(
                      title: Row(
                        children: [
                          const Text("Notificaciones"),
                          if (!NOTIFICATIONS_ENABLED) ...[
                            const SizedBox(width: 8),
                            const Icon(
                              Icons.warning_amber_rounded,
                              color: Colors.orange,
                              size: 20,
                            ),
                          ],
                        ],
                      ),
                      subtitle: const Text("Recibir alertas de llamadas entrantes"),
                      value: NOTIFICATIONS_ENABLED,
                      onChanged: (value) async {

                        await _changeStatusNotificationPermission(value);
                      },
                      secondary: const Icon(Icons.notifications_active),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      leading: const Icon(Icons.delete_forever, color: Colors.red),
                      title: const Text("Eliminar cuenta"),
                      subtitle: const Text("Esta acción no se puede deshacer"),
                      trailing: const Icon(Icons.arrow_forward_ios, size: 16),
                      onTap: () {
                    showDialog(
                      context: context,
                      builder: (_) {
                        String typedName = "";
                        final username = widget.username;

                        return StatefulBuilder(
                          builder: (context, setState) {
                            final isCorrect = typedName.trim() == username.trim();

                            return AlertDialog(
                              title: const Text('Eliminar cuenta'),
                              content: Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'Para eliminar tu cuenta, escribe tu nombre de usuario exactamente como aparece:',
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    username,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 16,
                                    ),
                                  ),
                                  const SizedBox(height: 16),
                                  TextField(
                                    decoration: const InputDecoration(
                                      labelText: 'Escribe tu nombre de usuario',
                                      border: OutlineInputBorder(),
                                    ),
                                    onChanged: (value) {
                                      setState(() => typedName = value);
                                    },
                                  ),
                                ],
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () => Navigator.pop(context),
                                  child: const Text('Cancelar'),
                                ),

                                TextButton(
                                  onPressed: isCorrect
                                      ? () async {
                                          await _eliminarCuenta();
                                        }
                                      : null, // deshabilitado si no coincide
                                  child: _eliminaCuenta
                                      ? const Text('Eliminando...')
                                      : Text(
                                          'Eliminar',
                                          style: TextStyle(
                                            color: isCorrect ? Colors.redAccent : Colors.grey,
                                          ),
                                        ),
                                ),
                              ],
                            );
                          },
                        );
                      },
                    );
                  },
                ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                "Información",
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Card(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: Column(
                  children: [
                    ListTile(
                      leading: Icon(Icons.info_outline),
                      title: Text("Versión de la app"),
                      subtitle: Text(version),
                    ),
                    Divider(height: 1),
                    ListTile(
                      leading: Icon(Icons.privacy_tip_outlined),
                      title: Text("Política de privacidad"),
                      trailing: const Icon(Icons.arrow_forward_ios, size: 16),
                      onTap: _showPrivacyPolicy,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future <void> _eliminarCuenta () async{
    setState(() {
      _eliminaCuenta = true;
    });

    try {

      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');

      // Construir headers tal como el ejemplo proporcionado
      final Map<String, String> headersList = {
        'Accept': '*/*',
        'User-Agent': 'GhoxClient/1.0',
      };
      if (token != null && token.isNotEmpty) {
        headersList['Authorization'] = 'Bearer $token';
      }


      var url = Uri.parse('${URL_API}api/users/me');

      var req = http.Request('DELETE', url);
      req.headers.addAll(headersList);

      var res = await req.send();
      final resBody = await res.stream.bytesToString();

      if (res.statusCode >= 200 && res.statusCode < 300) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Row(
            children: [
              const Icon(
                Icons.check_circle_outline,
                color: Colors.white,
                size: 20,
              ),
              const SizedBox(width: 12),
              const Expanded(
                child: Text("Cuenta eliminada"),
              ),
            ],
          ),
          backgroundColor: Colors.green,
          duration: const Duration(
            seconds: 2,
          ),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),   
        ),
        );
        await clearAllPrefs();
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
      else {
        final Map<String, dynamic> errorBody = jsonDecode(resBody);
        final String errorMessage = errorBody["error"] ?? "Error desconocido";

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Row(
            children: [
              const Icon(
                Icons.error_outline,
                color: Colors.white,
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text("$errorMessage"),
              ),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(
            seconds: 2,
          ),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),   
        ),
        );
        Navigator.pop(context);
      }

    }catch(e){
      setState(() {
        _eliminaCuenta = false;
      });
      print(e);
    }

    setState(() {
      _eliminaCuenta = false;
    });

  }

  Future<void> obtenerVersion() async {
    final info = await PackageInfo.fromPlatform();
    setState(() {
      version = info.version;
    });
  }

  Future<void> _changeStatusNotificationPermission(bool _status) async {
    try {

      if (_status == false) {
        setState((){
          NOTIFICATIONS_ENABLED = false;
        });
        
        await setNotificationsEnabledPref(_status);

        ScaffoldMessenger.of(context).showSnackBar( 
        SnackBar(content: Row(
          children: const [
            Icon(
              Icons.check_circle_outline,
              color: Colors.white,
              size: 20,
            ),
            SizedBox(width: 12),
            Expanded(
              child: Text("Notificaciones desactivadas"),
            ),
          ],        ),
        backgroundColor: Colors.green,
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ) ,
        ),
        );
        return;
      }else {
        // Solicitar permiso de notificaciones
      final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin = FlutterLocalNotificationsPlugin();

      final iOSPlugin = flutterLocalNotificationsPlugin.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>();
      
      bool? iOSGranted;
      if (iOSPlugin != null) {
        iOSGranted = await iOSPlugin.requestPermissions(
          alert: true,
          badge: true,
          sound: true,
        );
      }

      if (iOSGranted == true) {
        setState(() {
          NOTIFICATIONS_ENABLED = true;
        });
        await setNotificationsEnabledPref(_status);
        ScaffoldMessenger.of(context).showSnackBar( 
          SnackBar(content: 
          Row(
            children: const [
              Icon(
                Icons.check_circle_outline,
                color: Colors.white,
                size: 20,
              ),
              SizedBox(width: 12),
              Expanded(
                child: Text("Notificaciones activadas"),
              ),
            ],
          ),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ) , 
        )
          );
        return;
      }

      // Android: Request using flutter_local_notifications (for Android 13+)
      final AndroidFlutterLocalNotificationsPlugin? androidPlugin = flutterLocalNotificationsPlugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      
      final bool? androidGranted = await androidPlugin?.requestNotificationsPermission();

      if (androidGranted == true) {
        setState(() {
          NOTIFICATIONS_ENABLED = true;
        });
        await setNotificationsEnabledPref(_status);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Row(
                children: const [
                  Icon(
                    Icons.check_circle_outline,
                    color: Colors.white,
                    size: 20,
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text("Notificaciones activadas"),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
              duration: const Duration(seconds: 2),
              behavior: SnackBarBehavior.floating,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            )
          );
        return;
      }

      final status = await Permission.notification.status;
      
      if (status.isGranted) {
        //_showNotificationSettings();
      } else if (status.isDenied) {
        final result = await Permission.notification.request();
        setState(() {
          NOTIFICATIONS_ENABLED = result.isGranted;
        });

        if (result.isGranted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Row(
                children: const [
                  Icon(
                    Icons.check_circle_outline,
                    color: Colors.white,
                    size: 20,
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text("Notificaciones activadas"),
                  ),
                ],
              ),
              backgroundColor: Colors.green,
              duration: const Duration(seconds: 2),
              behavior: SnackBarBehavior.floating,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            )
          );
        } else if (result.isPermanentlyDenied) {
          _showPermissionDeniedDialog();
        }
      } else if (status.isPermanentlyDenied) {
        _showPermissionDeniedDialog();
      }
      }

    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Row(
          children: [
            Icon(
              Icons.error_outline,
              color: Colors.white,
              size: 20,
            ),
            SizedBox(width: 12),
            Expanded(
              child: Text("Error: $e"),
            ),
          ],
        ),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        )
      );
    }
  }

  void _showPermissionDeniedDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Permiso denegado'),
        content: const Text(
          'Para recibir notificaciones de llamadas, necesitas activar el permiso en la configuración del sistema.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancelar'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              openAppSettings();
            },
            child: const Text('Abrir configuración'),
          ),
        ],
      ),
    );
  }

  void _showPrivacyPolicy() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.privacy_tip_outlined, color: Colors.blue),
            SizedBox(width: 12),
            Text('Política de Privacidad'),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Última actualización: Marzo 2026\n',
                style: TextStyle(
                  fontStyle: FontStyle.italic,
                  fontSize: 12,
                  color: Colors.grey,
                ),
              ),
              const Text(
                '1. Información que recopilamos',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                '• Datos de cuenta: nombre de usuario\n'
                '• Datos de llamadas: registros de llamadas, participantes\n'
                '• Datos de audio: durante las llamadas activas para transmisión en tiempo real',
              ),
              const SizedBox(height: 16),
              const Text(
                '2. Cómo usamos tu información',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                '• Proporcionar servicios de llamadas VoIP\n'
                '• Mejorar la calidad de las llamadas\n'
                '• Enviar notificaciones de llamadas entrantes\n'
              ),
              const SizedBox(height: 16),
              const Text(
                '3. Almacenamiento y seguridad',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                '• Las llamadas utilizan cifrado punto a punto (WebRTC)\n'
                '• No almacenamos el audio de las llamadas\n'
                '• Los metadatos se almacenan de forma segura en servidores cifrados\n'
              ),
              const SizedBox(height: 16),
              const Text(
                '4. Compartir información',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                'No vendemos ni compartimos tu información personal con terceros.\n'
              ),
              const SizedBox(height: 16),
              const Text(
                '6. Cookies y tecnologías similares',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                'Utilizamos tokens de autenticación (JWT) y almacenamiento local para mantener tu sesión activa y mejorar tu experiencia.',
              ),
              const SizedBox(height: 16),
              const Text(
                '7. Menores de edad',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                'Este servicio no está dirigido a menores de 13 años. No recopilamos intencionalmente información de menores.',
              ),
              const SizedBox(height: 16),
              const Text(
                '8. Cambios en esta política',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 8),
              const Text(
                'Nos reservamos el derecho de actualizar esta política. Te notificaremos sobre cambios significativos a través de la aplicación.',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cerrar'),
          ),
        ],
      ),
    );
  }
  
}