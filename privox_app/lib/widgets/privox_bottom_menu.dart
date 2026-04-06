import 'package:flutter/material.dart';
import 'package:privox/screens/users_list_add_screen.dart';
import 'package:privox/screens/users_list_screen.dart';
import 'package:privox/screens/users_list_sol_screen.dart';
import 'package:privox/screens/welcome_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class PrivoxBottomMenu extends StatelessWidget {
  final int currentIndex;

  const PrivoxBottomMenu({
    super.key,
    required this.currentIndex,
  });

  Future<void> _onTap(BuildContext context, int index) async {
    if (index == currentIndex) return;

    //obtener informacion del usuario desde SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    String username = prefs.getString('username') ?? '';
    String userId = prefs.getString('userId') ?? '';
    String deviceId = prefs.getString('deviceId') ?? '';


    switch (index) {
      case 0:
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => WelcomeScreen(
              username: username,
              userId: userId,
              deviceId: deviceId),)
        );
        break;
      case 1:
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const UsersListAddScreen()),
        );
        break;
      case 2:
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const UsersListSolScreen()),
        );
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(
          top: BorderSide(
            color: colorScheme.outline.withOpacity(0.12),
            width: 0.8,
          ),
        ),
      ),
      child: SafeArea(
        top: false,
        child: NavigationBar(
          selectedIndex: currentIndex,
          onDestinationSelected: (index) => _onTap(context, index),
          backgroundColor: Colors.transparent,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          indicatorColor: colorScheme.primary.withOpacity(0.12),
          labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.people_outlined),
              selectedIcon: Icon(Icons.people),
              label: 'Contactos',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_add_alt_outlined),
              selectedIcon: Icon(Icons.person_add_alt_1),
              label: 'Agregar',
            ),
            NavigationDestination(
              icon: Icon(Icons.person_search_outlined),
              selectedIcon: Icon(Icons.person_search),
              label: 'Solicitudes',
            ),
          ],
        ),
      ),
    );
  }
}