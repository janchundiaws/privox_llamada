// import 'dart:async';
// import 'dart:math';
// import 'package:flutter/material.dart';
// import 'package:privox/main.dart';
// import 'package:privox/screens/call_voice/calling_screen.dart';
// import 'package:privox/screens/login_screen.dart';
// import 'package:privox/services/socket_service.dart';
// import 'package:privox/utils/auth.dart';
// import 'package:privox/utils/prefs.dart';
// import 'package:privox/widgets/privox_bottom_menu.dart';
// import 'package:http/http.dart' as http;
// import 'package:provider/provider.dart';
// import 'package:rflutter_alert/rflutter_alert.dart';
// import 'dart:convert';
// import 'package:shared_preferences/shared_preferences.dart';
// import '../variables.dart';

// class UsersListScreen extends StatefulWidget {
//   const UsersListScreen({super.key});

//   @override
//   State<UsersListScreen> createState() => _UsersListScreenState();
// }

// class User {
//   final String id;
//   final String userId;
//   final String username;
//   final String displayName;

//   User({
//     required this.id,
//     required this.userId,
//     required this.username,
//     required this.displayName,
//   });

//   factory User.fromMap(Map m) {
//     return User(
//       id: m['_id']?.toString() ?? '',
//       userId: m['userId']?.toString() ?? '',
//       username: m['username']?.toString() ?? '',
//       displayName: m['displayName']?.toString() ?? '',
//     );
//   }
// }

// class CallResult {
//   final String? callId;
//   final String type;

//   CallResult({this.callId, required this.type});
// }

// class _UsersListScreenState extends State<UsersListScreen> {
//   List<User> _users = [];
//   List<User> _filteredUsers = [];
//   final TextEditingController _searchController = TextEditingController();
//   bool _loading = true;
//   String? _error;
//   String? currentCallId;
//   String toUsername = "";
//   bool _eliminaContacto = false;

//   /// Solicitar una llamada de voz
//   Future<void> requestCall(String toUserId, String username) async {
//     final socketService = Provider.of<SocketService>(context, listen: false);

//     try {
//       socketService.currentTargetUserId = toUserId;
//       socketService.currentTargetUsername = username; // 🆕 Guardar username del destinatario
//       final payload = {
//         "type": "call-init",
//         "to": toUserId,
//         "toUsername": username,
//         "meta": {"mode": "voice"},
//       };
//       socketService.channel?.add(jsonEncode(payload));
//       if (socketService.channel == null) {
//         Alert(
//           context: context,
//           desc: "Debes estar Online para realizar llamadas",
//           buttons: [
//             DialogButton(
//               child: Text(
//                 "Aceptar",
//                 style: TextStyle(color: Colors.white, fontSize: 20),
//               ),
//               onPressed: () {
//                 Navigator.pop(context);
//               },
//               color: Colors.green,
//             ),
//           ],
//         ).show();
//         return;
//       }

//       // Esperar confirmación del servidor antes de navegar
//       final completer = Completer<CallResult>();
//       late StreamSubscription sub;

//       sub = socketService.events.listen((data) {
//         print(data);
//         if (data['type'] == 'call-init-ack') {
//           completer.complete(CallResult(callId: data['callId'].toString(), type: data['type'].toString()));
//           toUsername = data['toUsername'].toString();
//           sub.cancel();
//         }
//         if (data['type'] == 'call-missed') {
//           completer.complete(CallResult(callId: null, type: data['type'].toString()));
//           sub.cancel();
//         }
//         if (data['type'] == 'call-init-denied') {
//           completer.complete(CallResult(callId: null, type: data['type'].toString()));
//           sub.cancel();
//         }
//       });

//       // Esperar el callId confirmado
//       final responseCall = await completer.future;
//       if (responseCall.callId != null) {
//         socketService.currentCallId = responseCall.callId!;
//         socketService.currentTargetUserId = toUserId;
//         final ctx = navigatorKey.currentContext;
//         if (ctx != null) {
//           Navigator.push(
//             ctx,
//             MaterialPageRoute(
//               settings: const RouteSettings(name: 'calling-screen'),
//               builder: (_) => CallingScreen(
//                 callId: responseCall.callId??'', // ahora usas el ID real
//                 fromUserId: toUserId,
//                 username: username,
//                 isEmisor: true,
//                 toUsername: null,
//               ),
//             ),
//           );
//         }
//       } else {
//         Alert(
//           context: context,
//           desc: responseCall.type=="call-init-denied"?"$username esta ocupado en otra llamada.": responseCall.type=="call-missed"?"Llamada perdida.":"$username no esta online.",
//           buttons: [
//             DialogButton(
//               child: const Text("Aceptar", style: TextStyle(color: Colors.white, fontSize: 20)),
//               onPressed: () {
//                 Navigator.pop(context);
//               },
//               color: Colors.green,
//             ),
//           ],
//         ).show();
//       }
//     } catch (e) {
//       print("❌ Error al solicitar llamada: $e");
//     }
//   }

//   void _onSearchChanged() {
//     final query = _searchController.text.toLowerCase();
//     setState(() {
//       _filteredUsers = _users.where((u) {
//         final display = u.displayName.isNotEmpty ? u.displayName : u.username;
//         return display.toLowerCase().contains(query) ||
//             u.username.toLowerCase().contains(query) ||
//             u.userId.toLowerCase().contains(query);
//       }).toList();
//     });
//   }

//   @override
//   void initState() {
//     super.initState();
//     _fetchUsers();
//     getPreferencesInit();
//     _searchController.addListener(_onSearchChanged);
//   }

//   @override
//   void dispose() {
//     super.dispose();
//   }

//   Future<void> _fetchUsers() async {
//     setState(() {
//       _loading = true;
//       _error = null;
//     });

//     try {
//       final uri = Uri.parse('${URL_API}api/users/usersaccount');

//       // Obtener token guardado (si existe) y colocarlo en Authorization header
//       final prefs = await SharedPreferences.getInstance();
//       final token = prefs.getString('token');

//       // Construir headers tal como el ejemplo proporcionado
//       final Map<String, String> headersList = {
//         'Accept': '*/*',
//         'User-Agent': 'GhoxClient/1.0',
//       };
//       if (token != null && token.isNotEmpty) {
//         headersList['Authorization'] = 'Bearer $token';
//       }

//       final client = http.Client();
//       try {
//         final req = http.Request('GET', uri);
//         req.headers.addAll(headersList);

//         final streamed = await client
//             .send(req)
//             .timeout(const Duration(seconds: 15));
//         final resBody = await streamed.stream.bytesToString();

//         if (streamed.statusCode >= 200 && streamed.statusCode < 300) {
//           final body = json.decode(resBody);
//           List<User> users = [];

//           if (body is Map &&
//               body.containsKey('users') &&
//               body['users'] is List) {
//             for (final item in body['users']) {
//               users.add(
//                 User(
//                   id: item['_id'].toString(),
//                   userId: item['userId'].toString(),
//                   username: item['username'].toString(),
//                   displayName: item['displayName'].toString(),
//                 ),
//               );
//             }
//           }

//           setState(() {
//             _users = users;
//             _filteredUsers = users;
//           });
//           return;
//         } else {
//           setState(() {
//             _error =
//                 'Error: ${streamed.statusCode} ${streamed.reasonPhrase ?? ''}';
//           });
//           return;
//         }
//       } finally {
//         client.close();
//       }
//     } catch (e) {
//       setState(() {
//         _error = 'Error de red: ${e.toString()}';
//       });
//     } finally {
//       setState(() => _loading = false);
//     }
//   }

//   void _showCallOptions(BuildContext context, User user) {
//     showModalBottomSheet(
//       context: context,
//       builder: (_) => SafeArea(
//         child: Column(
//           mainAxisSize: MainAxisSize.min,
//           children: [
//             ListTile(
//               leading: const Icon(Icons.call),
//               title: const Text('Llamada por voz'),
//               onTap: () async {
//                 Navigator.of(context).pop();
//                 requestCall(user.userId, user.username);
//               },
//             ),
//             // ListTile(
//             //   leading: const Icon(Icons.videocam),
//             //   title: const Text('Video llamada'),
//             //   onTap: () {
//             //     Navigator.of(context).pop();
//             //     ScaffoldMessenger.of(context).showSnackBar(
//             //       SnackBar(content: Text('Iniciando videollamada a $display')),
//             //     );
//             //   },
//             // ),
//             // ListTile(
//             //   leading: const Icon(Icons.mail_outline_sharp),
//             //   title: const Text('Mensaje de texto'),
//             //   onTap: () {
//             //     Navigator.push(
//             //       context,
//             //       MaterialPageRoute(
//             //         builder: (_) => ChatScreen(
//             //           chatId: "chat123",
//             //           fromUserId: "userA",
//             //           toUserId: "userB",
//             //         ),
//             //       ),
//             //     );
//             //   }, 
//             // ),
//             ListTile(
//               leading: const Icon(Icons.delete_outline),
//               title: const Text('Eliminar contacto'),
//               onTap: () async {
//                 Navigator.of(context).pop();
//                     showDialog(
//                       context: context,
//                       builder: (_) {
//                         String typedName = "";
//                         final username = user.username;

//                         return StatefulBuilder(
//                           builder: (context, setState) {
//                             final isCorrect = typedName.trim() == username.trim();

//                             return AlertDialog(
//                               title: const Text('Eliminar contacto'),
//                               content: Column(
//                                 mainAxisSize: MainAxisSize.min,
//                                 crossAxisAlignment: CrossAxisAlignment.start,
//                                 children: [
//                                   const Text(
//                                     'Para eliminar tu contacto, escribe el nombre del contacto exactamente como aparece:',
//                                   ),
//                                   const SizedBox(height: 12),
//                                   Text(
//                                     username,
//                                     style: const TextStyle(
//                                       fontWeight: FontWeight.bold,
//                                       fontSize: 16,
//                                     ),
//                                   ),
//                                   const SizedBox(height: 16),
//                                   TextField(
//                                     decoration: const InputDecoration(
//                                       labelText: 'Escribe el nombre de contacto',
//                                       border: OutlineInputBorder(),
//                                     ),
//                                     onChanged: (value) {
//                                       setState(() => typedName = value);
//                                     },
//                                   ),
//                                 ],
//                               ),
//                               actions: [
//                                 TextButton(
//                                   onPressed: () => Navigator.pop(context),
//                                   child: const Text('Cancelar'),
//                                 ),

//                                 TextButton(
//                                   onPressed: isCorrect
//                                       ? () async {
//                                           await _eliminarContacto(user.userId);
//                                           Navigator.pop(context);
//                                         }
//                                       : null, // deshabilitado si no coincide
//                                   child: _eliminaContacto
//                                       ? const Text('Eliminando...')
//                                       : Text(
//                                           'Eliminar',
//                                           style: TextStyle(
//                                             color: isCorrect ? Colors.redAccent : Colors.grey,
//                                           ),
//                                         ),
//                                 ),
//                               ],
//                             );
//                           },
//                         );
//                       },
//                     );
//               },
//             ),
//             ListTile(
//               leading: const Icon(Icons.close),
//               title: const Text('Cancelar'),
//               onTap: () => Navigator.of(context).pop(),
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   @override
//   Widget build(BuildContext context) {
//     return Consumer<SocketService>(
//       builder: (context, socketService, _) {
//         return Scaffold(
//           appBar: AppBar(
//             title: Row(
//               mainAxisSize: MainAxisSize.min,
//               children: [
//                 const Text("Contactos"),
//                 const SizedBox(width: 8),
//                 Baseline(
//                   baseline: 20,
//                   baselineType: TextBaseline.alphabetic,
//                   child: Icon(
//                     Icons.circle,
//                     size: 14,
//                     color: socketService.isConnected ? Colors.green : Colors.red,
//                   ),
//                 ),
//               ],
//             ),
//             // actions: [
//             //   LogoutButton(
//             //     onLogout: () async {
//             //       await logoutClearPrefs();
//             //       Navigator.of(context).pushAndRemoveUntil(
//             //         MaterialPageRoute(builder: (_) => const LoginScreen()),
//             //         (route) => false,
//             //       );
//             //     },
//             //   ),
//             // ],
//           ),
//           bottomNavigationBar: const PrivoxBottomMenu(currentIndex: 0),
//           body: Column(
//             children: [
//               Padding(
//                 padding: const EdgeInsets.all(8.0),
//                 child: TextField(
//                   controller: _searchController,
//                   decoration: InputDecoration(
//                     prefixIcon: const Icon(Icons.search),
//                     hintText: 'Buscar contacto...',
//                     border: OutlineInputBorder(
//                       borderRadius: BorderRadius.circular(8),
//                     ),
//                   ),
//                 ),
//               ),
//               Expanded(
//                 child: RefreshIndicator(
//                   onRefresh: _fetchUsers,
//                   child: Builder(
//                     builder: (context) {
//                       if (_loading) {
//                         return const Center(child: CircularProgressIndicator());
//                       }

//                       if (_error != null) {
//                         return ListView(
//                           physics: const AlwaysScrollableScrollPhysics(),
//                           children: [
//                             Padding(
//                               padding: const EdgeInsets.all(24.0),
//                               child: Column(
//                                 children: [
//                                   const Icon(
//                                     Icons.error_outline,
//                                     color: Colors.red,
//                                     size: 64,
//                                   ),
//                                   const SizedBox(height: 16),
//                                   Text(_error!, textAlign: TextAlign.center),
//                                   const SizedBox(height: 24),
//                                   ElevatedButton(
//                                     onPressed: _fetchUsers,
//                                     child: const Text('Reintentar'),
//                                   ),
//                                   const SizedBox(height: 12),
//                                   OutlinedButton.icon(
//                                     onPressed: () async {
//                                       await logoutClearPrefs();
//                                       if (mounted) {
//                                         Navigator.of(context).pushAndRemoveUntil(
//                                           MaterialPageRoute(builder: (_) => const LoginScreen()),
//                                           (route) => false,
//                                         );
//                                       }
//                                     },
//                                     icon: const Icon(Icons.logout),
//                                     label: const Text('Cerrar sesión'),
//                                     style: OutlinedButton.styleFrom(
//                                       foregroundColor: Colors.red,
//                                     ),
//                                   ),
//                                 ],
//                               ),
//                             ),
//                           ],
//                         );
//                       }

//                       if (_filteredUsers.isEmpty) {
//                         return ListView(
//                           physics: const AlwaysScrollableScrollPhysics(),
//                           children: const [
//                             SizedBox(height: 80),
//                             Center(child: Text('No hay contactos disponibles')),
//                           ],
//                         );
//                       }

//                       return ListView.separated(
//                         padding: const EdgeInsets.symmetric(vertical: 8),
//                         itemCount: _filteredUsers.length,
//                         separatorBuilder: (_, __) => const Divider(height: 1),
//                         itemBuilder: (context, index) {
//                           final user = _filteredUsers[index];
//                           final username = user.username;
//                           final avatarLetter =
//                               (username.isNotEmpty
//                                       ? username[0]
//                                       : (user.username.isNotEmpty
//                                             ? user.username[0]
//                                             : '?'))
//                                   .toUpperCase();
//                           final subtitle =
//                               '${user.displayName}${user.userId.isNotEmpty ? ' · ${user.userId}' : ''}';

//                           // Generar color aleatorio
//                           final random = Random();
//                           final bgColor =
//                               Colors.primaries[random.nextInt(
//                                 Colors.primaries.length,
//                               )];

//                           return ListTile(
//                             leading: CircleAvatar(
//                               backgroundColor: bgColor,
//                               child: Text(
//                                 avatarLetter,
//                                 style: const TextStyle(
//                                   color: Colors.white,
//                                   fontWeight: FontWeight.bold,
//                                 ),
//                               ),
//                             ),
//                             title: Text(username),
//                             subtitle: Text(subtitle),
//                             trailing: const Icon(Icons.more_vert),
//                             onTap: () => _showCallOptions(context, user),
//                           );
//                         },
//                       );
//                     },
//                   ),
//                 ),
//               ),
//             ],
//           ),
//         );
//       },
//     );
//   }

//   Future<void> _eliminarContacto(String userId) async {

//     setState(() {
//       _eliminaContacto = true;
//     });

//     try {

//       final prefs = await SharedPreferences.getInstance();
//       final token = prefs.getString('token');

//       // Construir headers tal como el ejemplo proporcionado
//       final Map<String, String> headersList = {
//         'Accept': '*/*',
//         'User-Agent': 'GhoxClient/1.0',
//       };
//       if (token != null && token.isNotEmpty) {
//         headersList['Authorization'] = 'Bearer $token';
//       }


//       var url = Uri.parse('${URL_API}api/requests/contact/$userId');

//       var req = http.Request('DELETE', url);
//       req.headers.addAll(headersList);

//       var res = await req.send();
//       final resBody = await res.stream.bytesToString();


//       if (res.statusCode >= 200 && res.statusCode < 300) {
//         ScaffoldMessenger.of(context).showSnackBar(
//           SnackBar(content: Row(
//             children: [
//               const Icon(
//                 Icons.check_circle_outline,
//                 color: Colors.white,
//                 size: 20,
//               ),
//               const SizedBox(width: 12),
//               Expanded(
//                 child: Text('Contacto eliminado'),
//               ),
//             ],
//           ),
//           backgroundColor: Colors.green,
//           duration: const Duration(
//             seconds: 2,
//           ),
//           behavior: SnackBarBehavior.floating,
//           shape: RoundedRectangleBorder(
//             borderRadius: BorderRadius.circular(12),
//           ),   
//         ),
//         );
//         _fetchUsers();
//       }
//       else {
//         final Map<String, dynamic> errorBody = jsonDecode(resBody);
//         final String errorMessage = errorBody["error"] ?? "Error desconocido";

//         ScaffoldMessenger.of(context).showSnackBar(
//           SnackBar(content: Row(
//             children: [
//               const Icon(
//                 Icons.error_outline,
//                 color: Colors.white,
//                 size: 20,
//               ),
//               const SizedBox(width: 12),
//               Expanded(
//                 child: Text("$errorMessage"),
//               ),
//             ],
//           ),
//           backgroundColor: Colors.red,
//           duration: const Duration(
//             seconds: 2,
//           ),
//           behavior: SnackBarBehavior.floating,
//           shape: RoundedRectangleBorder(
//             borderRadius: BorderRadius.circular(12),
//           ),   
//         ),
//         );
//         Navigator.pop(context);
//       }

//     }catch(e){
//       setState(() {
//         _eliminaContacto = false;
//       });
//       print(e);
//     }

//     setState(() {
//       _eliminaContacto = false;
//     });

    
//   }
// }
