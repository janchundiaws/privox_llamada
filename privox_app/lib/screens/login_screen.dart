import 'package:flutter/material.dart';
import 'package:privox/screens/welcome_screen.dart';
import 'package:privox/variables.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'dart:math';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:privox/services/socket_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusUsername = FocusNode();
  bool _loading = false;
  String _creacionAutomatica = '';
  bool _creacionAutomaticaRealizada = false;

  @override
  void initState() {
    super.initState();
    offlineSocket();
    _initializeUser();
  }

  void offlineSocket() async {
    final socketService = SocketService();
    await socketService.disconnect();
  }

  Future<void> _initializeUser() async {
    final prefs = await SharedPreferences.getInstance();
    final savedUsername = prefs.getString('username')?.trim() ?? '';

    if (savedUsername.isNotEmpty) {
      if (mounted) {
        setState(() {
          _controller.text = savedUsername;
        });
      }
      return;
    }

    if (!mounted) return;

    setState(() {
      _creacionAutomaticaRealizada = false;
      _creacionAutomatica = 'Creando usuario automáticamente...';
    });

    await _createUserAutomatically();

    final refreshedPrefs = await SharedPreferences.getInstance();
    final newUsername = refreshedPrefs.getString('username')?.trim() ?? '';
    if (mounted && newUsername.isNotEmpty) {
      setState(() {
        _controller.text = newUsername;
      });
    }
  }

  Future<void> _createUserAutomatically() async {
    final prefs = await SharedPreferences.getInstance();
    final existingUsername = prefs.getString('username')?.trim() ?? '';
    if (existingUsername.isNotEmpty) return;

    final random = Random();
    final timestamp = DateTime.now().microsecondsSinceEpoch;
    final suffix = random.nextInt(999999).toString().padLeft(6, '0');
    final username = 'ghox_${timestamp}_$suffix';
    final displayName = 'Usuario $suffix';

    try {
      final uri = Uri.parse('${URL_API}api/auth/register');
      final body = json.encode({
        'username': username,
        'displayName': displayName,
      });
      final response = await http
          .post(
            uri,
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
            body: body,
          )
          .timeout(const Duration(seconds: 15));

      if (response.statusCode == 200 || response.statusCode == 201) {
        final data = json.decode(response.body);
        if (data is Map) {
          final u = data['user'] as Map;
          final deviceId = (u['deviceId'] ?? '').toString();
          if (deviceId.isNotEmpty) {
            await prefs.setString('deviceId', deviceId);
          }
          await prefs.setString('username', username);
          await prefs.setString('displayName', displayName);
        }
      }
    } catch (e) {
      setState(() {
        _creacionAutomaticaRealizada = true;
        _creacionAutomatica = 'Error al crear usuario automáticamente';
      });
      print('Error al crear usuario automáticamente: ${e.toString()}');
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusUsername.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final username = _controller.text.trim();
    if (username.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              Icon(Icons.warning, color: Colors.white),
              SizedBox(width: 8),
              Text('Por favor ingresa un username'),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );
      return;
    }

    setState(() => _loading = true);

    //aqui debo obtener el deviceId
    final prefs = await SharedPreferences.getInstance();
    String deviceId = prefs.getString('deviceId') ?? '';

    try {
      if (deviceId.isEmpty && username != 'admin') {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: const [
                Icon(Icons.warning, color: Colors.white),
                SizedBox(width: 8),
                Text('El username no fue creado en este dispositivo.'),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        );
        return;
      }

      final uri = Uri.parse('${URL_API}api/auth/login');
      final body = json.encode({
        'username': username,
        'deviceId': username == 'admin' ? 'deviceAdmin' : deviceId,
      });
      final response = await http
          .post(
            uri,
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
            body: body,
          )
          .timeout(const Duration(seconds: 15));

      if (response.statusCode == 200 || response.statusCode == 201) {
        final data = json.decode(response.body);
        final token = data is Map && data.containsKey('token')
            ? data['token']
            : null;

        dynamic userId;
        String? displayName;
        if (data is Map) {
          final u = data['user'] as Map;
          userId = u['id'];
          displayName = u['displayName'];
        }

        try {
          final prefs = await SharedPreferences.getInstance();
          if (token != null) await prefs.setString('token', token.toString());
          await prefs.setString('username', username);
          if (userId != null)
            await prefs.setString('userId', userId.toString());
          if (displayName != null)
            await prefs.setString('displayName', displayName);
        } catch (_) {}

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                Icon(Icons.check_circle, color: Colors.white),
                SizedBox(width: 8),
                Text('Login exitoso. ${token != null ? 'Token recibido' : ''}'),
              ],
            ),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 2),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        );

        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => WelcomeScreen(
              username: username,
              userId: (userId != null ? userId.toString() : username),
              deviceId: deviceId,
            ),
          ),
        );
      } else {
        FocusScope.of(context).requestFocus(_focusUsername);
        final data = json.decode(response.body);
        final message = data['error'];
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                Icon(Icons.warning, color: Colors.white),
                SizedBox(width: 8),
                Text(message.toString()),
              ],
            ),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        );
      }
    } on http.ClientException catch (e) {
      FocusScope.of(context).requestFocus(_focusUsername);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              Icon(Icons.warning, color: Colors.white),
              SizedBox(width: 8),
              Text('Error de cliente: ${e.message}'),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );
    } on Exception catch (e) {
      FocusScope.of(context).requestFocus(_focusUsername);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: [
              Icon(Icons.warning, color: Colors.white),
              SizedBox(width: 8),
              Text('Error: ${e.toString()}'),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          child: Card(
            elevation: 8,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
            ),
            margin: const EdgeInsets.symmetric(horizontal: 24),
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                    child: Image(
                      image: AssetImage('assets/images/privox3.png'),
                      width: 120,
                    ),
                  ),
                  const Text(
                    'Privox',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF2575FC),
                    ),
                  ),
                  const Text(
                    'Inicia para disfrutar de llamadas de voz privadas y de alta calidad',
                    style: TextStyle(fontSize: 16, color: Colors.grey),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _controller,
                    focusNode: _focusUsername,
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.person),
                      labelText: 'Username',
                      hintText: 'ej: juan123',
                      enabled: false,
                      filled: true,
                      fillColor: Colors.grey[100],
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide.none,
                      ),
                    ),
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _submit(),
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        backgroundColor: const Color(0xFF2575FC),
                      ),
                      onPressed: _loading ? null : _submit,
                      child: _loading
                          ? const SizedBox(
                              height: 18,
                              width: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text(
                              'Presionar para iniciar',
                              style: TextStyle(
                                fontSize: 16,
                                color: Colors.white,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  _controller.text.isNotEmpty ? Container() :
                  Text(_creacionAutomatica,style: TextStyle(fontSize: 14, color: Colors.grey),),
                  _controller.text.isNotEmpty ? Container() :
                  _creacionAutomaticaRealizada ? TextButton(
                    style: ButtonStyle(
                      foregroundColor: MaterialStateProperty.all<Color>(Colors.red),
                      textStyle: MaterialStateProperty.all<TextStyle>(
                        const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                      ),
                    ),
                    onPressed: () { 
                      _initializeUser();
                    },
                    child: Text('Volver a intentar'),
                  ):Container(),
                  _controller.text.isNotEmpty ? Container() :
                  const SizedBox(height: 12),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
