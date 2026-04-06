import 'dart:math';

import 'package:flutter/material.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/utils/prefs.dart';
import 'package:privox/widgets/privox_bottom_menu.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../variables.dart';

class UsersListSolScreen extends StatefulWidget {
  const UsersListSolScreen({super.key});

  @override
  State<UsersListSolScreen> createState() => _UsersListSolScreentate();
}

class User {
  final String id;
  final String userId;
  final String username;
  final String displayName;
  String requestId;

  User({required this.id, required this.userId, required this.username, required this.displayName, required this.requestId});

  factory User.fromMap(Map m) {
    return User(
      id: m['_id']?.toString() ?? '',
      userId: m['userId']?.toString() ?? '',
      username: m['username']?.toString() ?? '',
      displayName: m['displayName']?.toString() ?? '',
      requestId: m['requestId']?.toString() ?? '',
    );
  }

}

class _UsersListSolScreentate extends State<UsersListSolScreen> {
  List<User> _users = [];
  bool _loading = true;
  String? _error;
  bool _showIncoming = true; 


  @override
  void initState() {
    super.initState();
    _fetchRequests();
    getPreferencesInit();
  }

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _fetchRequests() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {

      var urlSuffix = _showIncoming ? 'outgoing' : 'incoming' ;

      final uri = Uri.parse('${URL_API}api/requests?direction=${urlSuffix}&status=pending');
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

      final client = http.Client();
      try {
        final req = http.Request('GET', uri);
        req.headers.addAll(headersList);

        final streamed = await client.send(req).timeout(const Duration(seconds: 15));
        final resBody = await streamed.stream.bytesToString();

        if (streamed.statusCode >= 200 && streamed.statusCode < 300) {
          final body = json.decode(resBody);
          List<User> users = [];
          final requests = body['requests'] as List;

          for (var req in requests) {
            final from = req[_showIncoming?'to':'from'];
            if (from != null && from is Map) {
            var user = User.fromMap(from);
            user.requestId = req['_id'].toString();
            users.add(user);
            }
          }
           setState(() {
            _users = users;
          });
          return;
        } else {
          setState(() {
            _error = 'Error: ${streamed.statusCode} ${streamed.reasonPhrase ?? ''}';
          });
          return;
        }
      } finally {
        client.close();
      }
    } catch (e) {
      setState(() {
        _error = 'Error de red: ${e.toString()}';
      });
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> updateRequestStatus(BuildContext context, String requestId, String newStatus) async {

    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('token');

    // Construir headers tal como el ejemplo proporcionado
    final Map<String, String> headersList = {
      'Accept': '*/*',
      'User-Agent': 'GhoxClient/1.0',
      'Content-Type': 'application/json',
    };
    if (token != null && token.isNotEmpty) {
      headersList['Authorization'] = 'Bearer $token';
    }


    var url = Uri.parse('${URL_API}api/requests/$requestId');

    var body = {
      "status": newStatus
    };

    try {
      var req = http.Request('PATCH', url);
      req.headers.addAll(headersList);
      req.body = json.encode(body);
      
      final client = http.Client();
      final streamed = await client.send(req).timeout(const Duration(seconds: 15));
      final resBody = await streamed.stream.bytesToString();

    if (streamed.statusCode >= 200 && streamed.statusCode < 300) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Row(
            children: [
              const Icon(
                Icons.check_circle_outline,
                color: Colors.white,
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text('Solicitud actualizada correctamente'),
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
      _fetchRequests();
      } else {
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
      }
    } catch (e) {
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
              child: Text("Excepción al enviar la petición: $e"),
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
    }
  }

  void _showCallOptions(BuildContext context, User user) {
    showModalBottomSheet(
      context: context,
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!_showIncoming)
            ListTile(
              leading: const Icon(Icons.person_add),
              title: const Text('Aceptar solicitud'),
              onTap: () async {
                Navigator.of(context).pop();
                await updateRequestStatus(context, user.requestId, "accepted");
              },
            ),
            if (!_showIncoming)
            ListTile(
              leading: const Icon(Icons.person_off),
              title: const Text('Rechazar solicitud'),
              onTap: () async {
                Navigator.of(context).pop();
                await updateRequestStatus(context, user.requestId, "rejected");
              },
            ),
            if (_showIncoming)
            ListTile(
              leading: const Icon(Icons.person_off),
              title: const Text('Cancelar solicitud'),
              onTap: () async {
                Navigator.of(context).pop();
                await updateRequestStatus(context, user.requestId, "cancelled");
              },
            ),
            ListTile(
              leading: const Icon(Icons.close),
              title: const Text('Cancelar'),
              onTap: () => Navigator.of(context).pop(),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<SocketService>(
      builder: (context, socketService, _) {
        return Scaffold(
          appBar: AppBar(
            title: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Solicitudes de contacto'),
                const SizedBox(width: 8),
                Baseline(
                  baseline: 20,
                  baselineType: TextBaseline.alphabetic,
                  child: Icon(
                    Icons.circle,
                    size: 14,
                    color: socketService.isConnected ? Colors.green : Colors.red,
                  ),
                ),
              ],
            ),
            // actions: [
            //   LogoutButton(
            //     onLogout: () async {
            //       await logoutClearPrefs();
            //       Navigator.of(context).pushAndRemoveUntil(
            //         MaterialPageRoute(builder: (_) => const LoginScreen()),
            //         (route) => false,
            //       );
            //     },
            //   ),
            // ],
          ),
          bottomNavigationBar: const PrivoxBottomMenu(currentIndex: 2),
          body: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(12.0),
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  padding: const EdgeInsets.all(4),
                  child: Row(
                    children: [
                      Expanded(
                        child: GestureDetector(
                          onTap: () async {
                            setState(() => _showIncoming = true);
                            await _fetchRequests();
                          },
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 250),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              color: _showIncoming ? Colors.blue : Colors.transparent,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: const [
                                Icon(Icons.send, size: 18, color: Colors.white),
                                SizedBox(width: 6),
                                Text("Enviadas", style: TextStyle(color: Colors.white)),
                              ],
                            ),
                          ),
                        ),
                      ),                  
                      Expanded(
                        child: GestureDetector(
                          onTap: () async {
                            setState(() => _showIncoming = false);
                            await _fetchRequests();
                          },
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 250),
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            decoration: BoxDecoration(
                              color: !_showIncoming ? Colors.blue : Colors.transparent,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: const [
                                Icon(Icons.receipt, size: 18, color: Colors.white),
                                SizedBox(width: 6),
                                Text("Recibidas", style: TextStyle(color: Colors.white)),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: _fetchRequests,
                  child: Builder(builder: (context) {
                    if (_loading) {
                      return const Center(child: CircularProgressIndicator());
                    }

                    if (_error != null) {
                      return ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: [
                          Padding(
                            padding: const EdgeInsets.all(24.0),
                            child: Column(
                              children: [
                                const Icon(Icons.error_outline,
                                color: Colors.red, size: 48),
                                const SizedBox(height: 12),
                                Text(_error!, textAlign: TextAlign.center),
                                const SizedBox(height: 12),
                                ElevatedButton(onPressed: _fetchRequests, child: const Text('Reintentar')),
                              ],
                            ),
                          ),
                        ],
                      );
                    }

                    if (_users.isEmpty) {
                      return ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: [
                          SizedBox(height: 80),
                          Center(child: Text(_showIncoming?'No hay solicitudes enviadas':'No hay solicitudes recibidas')),
                        ],
                      );
                    }

                    return ListView.separated(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _users.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final user = _users[index];
                        final username = user.username.isNotEmpty ? user.username : user.displayName;
                        final avatarLetter = (username.isNotEmpty ? username[0] : (user.username.isNotEmpty ? user.username[0] : '?')).toUpperCase();
                        final subtitle = '${user.displayName}${user.userId.isNotEmpty ? ' · ${user.userId}' : ''}';

                        // Generar color aleatorio
                        final random = Random();
                        final bgColor = Colors.primaries[random.nextInt(Colors.primaries.length)];

                        return ListTile(
                          leading: CircleAvatar(
                            backgroundColor: bgColor,
                            child: Text(
                              avatarLetter,
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          title: Text(username),
                          subtitle: Text(subtitle),
                          trailing: const Icon(Icons.more_vert),
                          onTap: () => _showCallOptions(context, user),
                        );
                      },
                    );
                  }),
                  ),
                ),
              ],
            ),
        );
      } 
    );
  }

}
