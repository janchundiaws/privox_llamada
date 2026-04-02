import 'dart:math';

import 'package:flutter/material.dart';
import 'package:privox/screens/login_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/utils/auth.dart';
import 'package:privox/utils/prefs.dart';
import 'package:privox/widgets/logout_button.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../variables.dart';

class UsersListAddScreen extends StatefulWidget {
  const UsersListAddScreen({super.key});

  @override
  State<UsersListAddScreen> createState() => _UsersListAddScreentate();
}

class User {
  final String id;
  final String userId;
  final String username;
  final String displayName;

  User({required this.id, required this.userId, required this.username, required this.displayName});

  factory User.fromMap(Map m) {
    return User(
      id: m['_id']?.toString() ?? '',
      userId: m['userId']?.toString() ?? '',
      username: m['username']?.toString() ?? '',
      displayName: m['displayName']?.toString() ?? '',
    );
  }
}

class _UsersListAddScreentate extends State<UsersListAddScreen> {
  List<User> _users = [];
  List<User> _filteredUsers = [];
  bool _loading = true;
  String? _error;
  final TextEditingController _searchController = TextEditingController();


  @override
  void initState() {
    super.initState();
    _fetchUsers();
    getPreferencesInit();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _fetchUsers() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final uri = Uri.parse('${URL_API}api/users/usersadd');

      // Obtener token guardado (si existe) y colocarlo en Authorization header
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

          // Soporta estructura: { "users": [ {...} ] }
          if (body is Map && body.containsKey('users') && body['users'] is List) {
            for (final item in body['users']) {
              if (item is Map) users.add(User.fromMap(item));
            }
          } else if (body is List) {
            for (final item in body) {
              if (item is String) {
                users.add(User(id: '', userId: '', username: item, displayName: item));
              } else if (item is Map && item.containsKey('username')) {
                users.add(User.fromMap(item));
              } else if (item is Map && item.containsKey('name')) {
                users.add(User(id: '', userId: '', username: item['name'].toString(), displayName: item['name'].toString()));
              }
            }
          }

          setState(() {
            _users = users;
            _filteredUsers = users;
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

  void _showCallOptions(BuildContext context, User user) {
    showModalBottomSheet(
      context: context,
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.person_add),
              title: const Text('Agregar contacto'),
              onTap: () async {
                await createRequest(context, user.userId);
                Navigator.of(context).pop();
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

  void _onSearchChanged() {
    final query = _searchController.text.toLowerCase();
    setState(() {
      _filteredUsers = _users.where((u) {
        final display = u.displayName.isNotEmpty ? u.displayName : u.username;
        return display.toLowerCase().contains(query) ||
               u.username.toLowerCase().contains(query) ||
               u.userId.toLowerCase().contains(query);
      }).toList();
    });
  }

  Future<void> createRequest(BuildContext context, String toUserId) async {

    // Obtener token guardado (si existe) y colocarlo en Authorization header
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('token');

    // Construir headers tal como el ejemplo proporcionado
    final Map<String, String> headersList = {
      'Accept': '*/*',
      'User-Agent': 'GhoxClient/1.0',
      'Content-Type': 'application/json'
    };
    if (token != null && token.isNotEmpty) {
      headersList['Authorization'] = 'Bearer $token';
    }
    final url = Uri.parse('${URL_API}api/requests');

    final body = {
      "to": toUserId,
      "meta": {}
    };

    try {
      final req = http.Request('POST', url);
      req.headers.addAll(headersList);
      req.body = json.encode(body);

      final res = await req.send();
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
                Expanded(
                  child: Text('Solicitud creada correctamente'),
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
        _fetchUsers();
      } else {
        final Map<String, dynamic> errorBody = jsonDecode(resBody);
        final String errorMessage = errorBody["error"] ?? "Error desconocido";

        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:
          Row(
            children: [
              const Icon(
                Icons.error_outline,
                color: Colors.white,
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text("$errorMessage")
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
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:
        Row(
          children: [
            const Icon(
              Icons.error_outline,
              color: Colors.white,
              size: 20,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text("💥 Excepción al enviar la petición: $e")
            ),
          ],
        ),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ));
    }
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
                const Text('Agregar contacto'),
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
          body: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.search),
                    hintText: 'Buscar contacto...',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: _fetchUsers,
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
                                Text(_error!, textAlign: TextAlign.center),
                                const SizedBox(height: 12),
                                ElevatedButton(onPressed: _fetchUsers, child: const Text('Reintentar')),
                              ],
                            ),
                          ),
                        ],
                      );
                    }

                    if (_filteredUsers.isEmpty) {
                      return ListView(
                        physics: const AlwaysScrollableScrollPhysics(),
                        children: const [
                          SizedBox(height: 80),
                          Center(child: Text('No hay contactos disponibles')),
                        ],
                      );
                    }

                    return ListView.separated(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _filteredUsers.length,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final user = _filteredUsers[index];
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
    });
  }

}
