import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:privox/screens/settings_screen.dart';
import 'package:privox/screens/users_list_add_screen.dart';
import 'package:privox/screens/users_list_screen.dart';
import 'package:privox/screens/users_list_sol_screen.dart';
import 'package:privox/services/socket_service.dart';
import 'package:privox/utils/auth.dart';
import 'package:privox/screens/login_screen.dart';
import 'package:privox/variables.dart';
import 'package:privox/widgets/logout_button.dart';
import 'package:provider/provider.dart';

class WelcomeScreen extends StatefulWidget {
  final String username;
  final String userId;
  final String deviceId;

  const WelcomeScreen({super.key, required this.username,required this.userId, required this.deviceId});

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  bool _notificationsEnabled = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (STAY_ONLINE) {
        final socketService = Provider.of<SocketService>(context, listen: false);
        socketService.disconnect();
        socketService.connect();
      }
    });

  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {

    final initials = (widget.username.isNotEmpty ? widget.username[0] : '?').toUpperCase();
    return Consumer<SocketService>(
      builder: (context, socketService, _) {
    
      return Scaffold(
        appBar: AppBar(
          elevation: 0,
          backgroundColor: Colors.transparent,
          foregroundColor: Theme.of(context).colorScheme.onBackground,
          actions: [
            Stack(
              children: [
                IconButton(
                  tooltip: 'Configuración',
                  icon: const Icon(Icons.settings),
                  onPressed: () async {
                    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => SettingsScreen(username: widget.username)));
                  },
                ),
                if (!_notificationsEnabled || !socketService.isConnected)
                  Positioned(
                    right: 8,
                    top: 8,
                    child: Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(
                        color: Colors.red,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 1.5),
                      ),
                    ),
                  ),
              ],
            ),          
            // LogoutButton(
            //   onLogout: () async {
            //     await logoutClearPrefs();
            //     Navigator.of(context).pushAndRemoveUntil(
            //       MaterialPageRoute(builder: (_) => const LoginScreen()),
            //       (route) => false,
            //     );
            //   },
            // ),          
          ],
        ),
        backgroundColor: Theme.of(context).colorScheme.background,
        body: SafeArea(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: 16),
                  Card(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
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
                              gradient: LinearGradient(colors: [Colors.blue.shade400, Colors.purple.shade400]),
                            ),
                            child: Center(
                              child: Text(initials, style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold)),
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(widget.username, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                                const SizedBox(height: 6),
                                Text('Id: ${widget.userId}', style: TextStyle(color: Colors.grey[700])),
                                const SizedBox(height: 8),
                                IntrinsicWidth(
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: socketService.isConnected ? Colors.green.withOpacity(0.15) : Colors.red.withOpacity(0.15),
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min, // clave para que no se expanda
                                      children: [
                                        Icon(
                                          Icons.circle,
                                          size: 10,
                                          color: socketService.isConnected ? Colors.green : Colors.red,
                                        ),
                                        const SizedBox(width: 6),
                                        Text(
                                          socketService.isConnected ? "Online" : "Offline",
                                          style: TextStyle(
                                            color: socketService.isConnected ? Colors.green : Colors.red,
                                            fontSize: 12,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                )
                              ],
                            ),
                          )
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 20),

                  // Action buttons
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: const Icon(Icons.people, size: 18, color: Colors.white),
                          label: const Text('Contactos',style: TextStyle(color: Colors.white),),
                          style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14),backgroundColor: Colors.blue, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                          onPressed: () {
                            Navigator.of(context).push(MaterialPageRoute(builder: (_) => const UsersListScreen()));
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: const Icon(Icons.person_add),
                          label: const Text('Agregar'),
                          style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                          onPressed: () {
                            Navigator.of(context).push(MaterialPageRoute(builder: (_) => const UsersListAddScreen()));
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: const Icon(Icons.person_search_outlined),
                          label: const Text('Solicitudes',),
                          style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                          onPressed: () {
                          Navigator.of(context).push(MaterialPageRoute(builder: (_) => const UsersListSolScreen()));
                          },
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 20),

                  // Info / tips card
                  Card(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    color: Colors.grey[50],
                    child: Padding(
                      padding: const EdgeInsets.all(12.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Consejos rápidos', style: TextStyle(fontWeight: FontWeight.bold)),
                          SizedBox(height: 8),
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.wifi, size: 16),
                              Expanded(
                                child: Text(
                                  ' Debes estar online para realizar o recibir llamadas.',
                                  softWrap: true,
                                ),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              Icon(Icons.people, size: 16),
                              Expanded(
                                child: Text(
                                  ' Usa lista de contactos para iniciar una llamada.',
                                  softWrap: true,
                                ),
                              ),
                            ],
                          ),
                          Row(
                            children: [
                              Icon(Icons.lock, size: 16),
                              Expanded(
                                child: RichText(
                                  text: TextSpan(
                                    style: TextStyle(color: Colors.black), // estilo base
                                    children: [
                                      TextSpan(text: 'Tus llamadas están '),
                                      TextSpan(
                                        text: 'cifradas de extremo a extremo.',
                                        style: TextStyle(color: Color(0xFF2575FC),fontWeight: FontWeight.bold), // aquí el rojo
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                
                  // const SizedBox(height: 20),

                  // Padding(
                  //   padding: const EdgeInsets.only(top: 12.0, bottom: 8.0),
                  //   child: Center(
                  //     child: ElevatedButton(
                  //       onPressed: (){
                  //         if (socketService.isConnected) {
                  //           socketService.disconnect();
                  //         } else {
                  //           socketService.connect();
                  //         } 
                  //       },
                  //       style: ElevatedButton.styleFrom(
                  //         backgroundColor: socketService.isConnected ? Colors.blue : Colors.red,
                  //         padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
                  //         shape: RoundedRectangleBorder(
                  //           borderRadius: BorderRadius.circular(12), // esquinas redondeadas
                  //         ),
                  //       ),
                  //       child: socketService.isConnecting ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : socketService.isConnected ? const Text('Online',style: TextStyle(color: Colors.white),) : const Text('Offline',style: TextStyle(color: Colors.white),),
                  //     ),
                  //   ),
                  // ),              
                ],
              ),
            ),
          ),
        ),
      );
    });
  }
}