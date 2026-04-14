import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:privox/main.dart';
import 'package:privox/screens/call_voice/calling_screen.dart';
import 'package:privox/screens/settings_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/utils/prefs.dart';
import 'package:privox/variables.dart';
import 'package:provider/provider.dart';
import 'package:rflutter_alert/rflutter_alert.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ─── Local models ────────────────────────────────────────────────────────────

class _ContactUser {
  final String id;
  final String userId;
  final String username;
  final String displayName;

  _ContactUser({
    required this.id,
    required this.userId,
    required this.username,
    required this.displayName,
  });

  factory _ContactUser.fromMap(Map<dynamic, dynamic> m) => _ContactUser(
        id: m['_id']?.toString() ?? '',
        userId: m['userId']?.toString() ?? '',
        username: m['username']?.toString() ?? '',
        displayName: m['displayName']?.toString() ?? '',
      );
}

class _CallResult {
  final String? callId;
  final String type;
  _CallResult({this.callId, required this.type});
}

// ─── Widget ──────────────────────────────────────────────────────────────────

class WelcomeScreen extends StatefulWidget {
  final String username;
  final String userId;
  final String deviceId;

  const WelcomeScreen({
    super.key,
    required this.username,
    required this.userId,
    required this.deviceId,
  });

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  bool _notificationsEnabled = true;

  // contacts state
  List<_ContactUser> _users = [];
  List<_ContactUser> _filteredUsers = [];
  final TextEditingController _searchController = TextEditingController();
  bool _loadingContacts = true;
  String? _contactsError;
  bool _eliminaContacto = false;

  @override
  void initState() {
    super.initState();
    _fetchUsers();
    getPreferencesInit();
    _searchController.addListener(_onSearchChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      print("janchundia la maravilla ==>");
      print(STAY_ONLINE);


      if (STAY_ONLINE) {
        final socketService = Provider.of<SocketService>(context, listen: false);
        print(socketService.isConnected);
        socketService.disconnect();
        socketService.connect();
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  // ── search ──────────────────────────────────────────────────────────────

  void _onSearchChanged() {
    final query = _searchController.text.toLowerCase();
    setState(() {
      _filteredUsers = _users.where((u) {
        final display =
            u.displayName.isNotEmpty ? u.displayName : u.username;
        return display.toLowerCase().contains(query) ||
            u.username.toLowerCase().contains(query) ||
            u.userId.toLowerCase().contains(query);
      }).toList();
    });
  }

  // ── fetch contacts ───────────────────────────────────────────────────────

  Future<void> _fetchUsers() async {
    setState(() {
      _loadingContacts = true;
      _contactsError = null;
    });
    try {
      final uri = Uri.parse('${URL_API}api/users/usersaccount');
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');
      final headers = <String, String>{
        'Accept': '*/*',
        'User-Agent': 'GhoxClient/1.0',
        if (token != null && token.isNotEmpty)
          'Authorization': 'Bearer $token',
      };
      final client = http.Client();
      try {
        final req = http.Request('GET', uri)..headers.addAll(headers);
        final streamed = await client
            .send(req)
            .timeout(const Duration(seconds: 15));
        final resBody = await streamed.stream.bytesToString();
        if (streamed.statusCode >= 200 && streamed.statusCode < 300) {
          final body = json.decode(resBody);
          final List<_ContactUser> users = [];
          if (body is Map && body['users'] is List) {
            for (final item in body['users'] as List) {
              users.add(_ContactUser.fromMap(item as Map));
            }
          }
          setState(() {
            _users = users;
            _filteredUsers = users;
          });
        } else {
          setState(() =>
              _contactsError = 'Error del servidor: ${streamed.statusCode}');
        }
      } finally {
        client.close();
      }
    } catch (e) {
      setState(() => _contactsError = 'Error de red: $e');
    } finally {
      if (mounted) setState(() => _loadingContacts = false);
    }
  }

  // ── call ─────────────────────────────────────────────────────────────────

  Future<void> requestCall(String toUserId, String username) async {
    final socketService =
        Provider.of<SocketService>(context, listen: false);
    try {
      socketService.currentTargetUserId = toUserId;
      socketService.currentTargetUsername = username;

      if (socketService.channel == null) {
        Alert(
          context: context,
          desc: 'Debes estar Online para realizar llamadas',
          buttons: [
            DialogButton(
              onPressed: () => Navigator.pop(context),
              color: Colors.green,
              child: const Text('Aceptar',
                  style: TextStyle(color: Colors.white, fontSize: 20)),
            ),
          ],
        ).show();
        return;
      }

      final payload = {
        'type': 'call-init',
        'to': toUserId,
        'toUsername': username,
        'meta': {'mode': 'voice'},
      };
      socketService.channel?.add(jsonEncode(payload));

      final completer = Completer<_CallResult>();
      late StreamSubscription<dynamic> sub;
      sub = socketService.events.listen((data) {
        if (data['type'] == 'call-init-ack') {
          completer.complete(_CallResult(
              callId: data['callId'].toString(),
              type: data['type'].toString()));
          sub.cancel();
        } else if (data['type'] == 'call-missed' ||
            data['type'] == 'call-init-denied') {
          completer.complete(
              _CallResult(callId: null, type: data['type'].toString()));
          sub.cancel();
        }
      });

      final result = await completer.future;

      if (result.callId != null) {
        socketService.currentCallId = result.callId!;
        final ctx = navigatorKey.currentContext;
        if (ctx != null) {
          Navigator.push(
            ctx,
            MaterialPageRoute(
              settings: const RouteSettings(name: 'calling-screen'),
              builder: (_) => CallingScreen(
                callId: result.callId!,
                fromUserId: toUserId,
                username: username,
                isEmisor: true,
                toUsername: null,
              ),
            ),
          );
        }
      } else {
        Alert(
          context: context,
          desc: result.type == 'call-init-denied'
              ? '$username está ocupado en otra llamada.'
              : result.type == 'call-missed'
                  ? 'Llamada perdida.'
                  : '$username no está online.',
          buttons: [
            DialogButton(
              onPressed: () => Navigator.pop(context),
              color: Colors.green,
              child: const Text('Aceptar',
                  style: TextStyle(color: Colors.white, fontSize: 20)),
            ),
          ],
        ).show();
      }
    } catch (e) {
      debugPrint('❌ Error al solicitar llamada: $e');
    }
  }

  // ── bottom-sheet options ─────────────────────────────────────────────────

  void _showCallOptions(BuildContext ctx, _ContactUser user) {
    showModalBottomSheet(
      context: ctx,
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.call),
              title: const Text('Llamada por voz'),
              onTap: () {
                Navigator.of(ctx).pop();
                requestCall(user.userId, user.username);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline),
              title: const Text('Eliminar contacto'),
              onTap: () {
                Navigator.of(ctx).pop();
                _confirmDelete(user);
              },
            ),
            ListTile(
              leading: const Icon(Icons.close),
              title: const Text('Cancelar'),
              onTap: () => Navigator.of(ctx).pop(),
            ),
          ],
        ),
      ),
    );
  }

  void _confirmDelete(_ContactUser user) {
    showDialog(
      context: context,
      builder: (_) {
        String typedName = '';
        return StatefulBuilder(
          builder: (context, setDialogState) {
            final isCorrect =
                typedName.trim() == user.username.trim();
            return AlertDialog(
              title: const Text('Eliminar contacto'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Escribe el nombre del contacto para confirmar:',
                  ),
                  const SizedBox(height: 12),
                  Text(user.username,
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 16),
                  TextField(
                    decoration: const InputDecoration(
                      labelText: 'Nombre del contacto',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (v) =>
                        setDialogState(() => typedName = v),
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
                          Navigator.pop(context);
                          await _eliminarContactoById(user.userId);
                        }
                      : null,
                  child: _eliminaContacto
                      ? const Text('Eliminando...')
                      : Text(
                          'Eliminar',
                          style: TextStyle(
                            color: isCorrect
                                ? Colors.redAccent
                                : Colors.grey,
                          ),
                        ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _eliminarContactoById(String userId) async {
    setState(() => _eliminaContacto = true);
    try {
      final prefs = await SharedPreferences.getInstance();
      final token = prefs.getString('token');
      final headers = <String, String>{
        'Accept': '*/*',
        'User-Agent': 'GhoxClient/1.0',
        if (token != null && token.isNotEmpty)
          'Authorization': 'Bearer $token',
      };
      final req = http.Request(
          'DELETE', Uri.parse('${URL_API}api/requests/contact/$userId'))
        ..headers.addAll(headers);
      final res = await req.send();
      final resBody = await res.stream.bytesToString();

      if (!mounted) return;

      if (res.statusCode >= 200 && res.statusCode < 300) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: const Row(children: [
            Icon(Icons.check_circle_outline,
                color: Colors.white, size: 20),
            SizedBox(width: 12),
            Text('Contacto eliminado'),
          ]),
          backgroundColor: Colors.green,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12)),
        ));
        _fetchUsers();
      } else {
        final errorBody = jsonDecode(resBody) as Map;
        final errorMessage =
            errorBody['error']?.toString() ?? 'Error desconocido';
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Row(children: [
            const Icon(Icons.error_outline,
                color: Colors.white, size: 20),
            const SizedBox(width: 12),
            Expanded(child: Text(errorMessage)),
          ]),
          backgroundColor: Colors.red,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12)),
        ));
      }
    } catch (e) {
      debugPrint('Error eliminando contacto: $e');
    } finally {
      if (mounted) setState(() => _eliminaContacto = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final initials = (widget.username.isNotEmpty ? widget.username[0] : '?').toUpperCase();

    return Consumer<SocketService>(
      builder: (context, socketService, _) {
        print("isaias 1");
        print(socketService.isConnected);
        print("isaias 2");
        return Scaffold(
          appBar: AppBar(
            elevation: 0,
            backgroundColor: Colors.transparent,
            foregroundColor:
                Theme.of(context).colorScheme.onBackground,
            actions: [
              Stack(
                children: [
                  IconButton(
                    tooltip: 'Configuración',
                    icon: const Icon(Icons.settings),
                    onPressed: () async {
                      await Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => SettingsScreen(
                              username: widget.username),
                        ),
                      );
                    },
                  ),
                  if (!_notificationsEnabled ||
                      !socketService.isConnected)
                    Positioned(
                      right: 8,
                      top: 8,
                      child: Container(
                        width: 10,
                        height: 10,
                        decoration: BoxDecoration(
                          color: Colors.red,
                          shape: BoxShape.circle,
                          border: Border.all(
                              color: Colors.white, width: 1.5),
                        ),
                      ),
                    ),
                ],
              ),
            ],
          ),

          backgroundColor: Theme.of(context).colorScheme.background,

          // ── Body ────────────────────────────────────────────────────────
          body: RefreshIndicator(
            onRefresh: _fetchUsers,
            child: SafeArea(
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                child: Padding(
                  padding: const EdgeInsets.symmetric( horizontal: 20.0, vertical: 16.0),
                  child: Column(
                    crossAxisAlignment:
                        CrossAxisAlignment.stretch,
                    children: [
                      // ── Profile card ─────────────────────────────────
                      Card(
                        shape: RoundedRectangleBorder(
                            borderRadius:
                                BorderRadius.circular(16)),
                        elevation: 4,
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Row(
                            children: [
                              Container(
                                width: 120,
                                height: 120,
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
                                      fontSize: 32,
                                      fontWeight:
                                          FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 16),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      widget.username,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight:
                                            FontWeight.w700,
                                      ),
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      'Id: ${widget.userId}',
                                      style: TextStyle(
                                          color:
                                              Colors.grey[700]),
                                    ),
                                    const SizedBox(height: 8),
                                    IntrinsicWidth(
                                      child: Container(
                                        padding: const EdgeInsets
                                            .symmetric(
                                            horizontal: 10,
                                            vertical: 4),
                                        decoration:
                                            BoxDecoration(
                                          color: socketService
                                                  .isConnected
                                              ? Colors.green
                                                  .withOpacity(
                                                      0.15)
                                              : Colors.red
                                                  .withOpacity(
                                                      0.15),
                                          borderRadius:
                                              BorderRadius
                                                  .circular(12),
                                        ),
                                        child: Row(
                                          mainAxisSize:
                                              MainAxisSize.min,
                                          children: [
                                            Icon(
                                              Icons.circle,
                                              size: 10,
                                              color: socketService
                                                      .isConnected
                                                  ? Colors.green
                                                  : Colors.red,
                                            ),
                                            const SizedBox(
                                                width: 6),
                                            Text(
                                              socketService
                                                      .isConnected
                                                  ? 'Online'
                                                  : 'Offline',
                                              style: TextStyle(
                                                color: socketService
                                                        .isConnected
                                                    ? Colors.green
                                                    : Colors.red,
                                                fontSize: 12,
                                                fontWeight:
                                                    FontWeight
                                                        .w600,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),

                      const SizedBox(height: 20),

                      // ── Search bar ───────────────────────────────────
                      TextField(
                        controller: _searchController,
                        decoration: InputDecoration(
                          prefixIcon:
                              const Icon(Icons.search),
                          hintText: 'Buscar contacto...',
                          border: OutlineInputBorder(
                              borderRadius:
                                  BorderRadius.circular(8)),
                        ),
                      ),
                      const SizedBox(height: 8),

                      // ── Contacts list ────────────────────────────────
                      if (_loadingContacts)
                        const Padding(
                          padding: EdgeInsets.symmetric(
                              vertical: 32),
                          child: Center(
                              child:
                                  CircularProgressIndicator()),
                        )
                      else if (_contactsError != null)
                        Padding(
                          padding: const EdgeInsets.symmetric(
                              vertical: 24),
                          child: Column(
                            children: [
                              const Icon(Icons.error_outline,
                                  color: Colors.red, size: 48),
                              const SizedBox(height: 12),
                              Text(_contactsError!,
                                  textAlign: TextAlign.center),
                              const SizedBox(height: 12),
                              ElevatedButton(
                                onPressed: _fetchUsers,
                                child:
                                    const Text('Reintentar'),
                              ),
                            ],
                          ),
                        )
                      else if (_filteredUsers.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(
                              vertical: 40),
                          child: Center(
                              child: Text(
                                  'No hay contactos disponibles')),
                        )
                      else
                        ListView.separated(
                          shrinkWrap: true,
                          physics:
                              const NeverScrollableScrollPhysics(),
                          itemCount: _filteredUsers.length,
                          separatorBuilder: (_, __) =>
                              const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final user = _filteredUsers[index];
                            final name = user.username;
                            final letter =
                                (name.isNotEmpty ? name[0] : '?')
                                    .toUpperCase();
                            final sub =
                                user.displayName.isNotEmpty
                                    ? '${user.displayName} · ${user.userId}'
                                    : user.userId;
                            final bgColor =
                                Colors.primaries[Random().nextInt(
                                    Colors.primaries.length)];
                            return ListTile(
                              leading: CircleAvatar(
                                backgroundColor: bgColor,
                                child: Text(
                                  letter,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              title: Text(name),
                              subtitle: Text(sub),
                              trailing: const Icon(
                                  Icons.more_vert),
                              onTap: () => _showCallOptions(
                                  context, user),
                            );
                          },
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}
