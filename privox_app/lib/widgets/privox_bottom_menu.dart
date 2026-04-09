import 'package:flutter/material.dart';
import 'package:privox/screens/users_list_add_screen.dart';
import 'package:privox/screens/users_list_sol_screen.dart';
import 'package:privox/screens/welcome_screen.dart';

class PrivoxTabsScreen extends StatefulWidget {
  final String username;
  final String userId;
  final String deviceId;

  const PrivoxTabsScreen({
    super.key,
    required this.username,
    required this.userId,
    required this.deviceId,
  });

  @override
  State<PrivoxTabsScreen> createState() => _PrivoxTabsScreenState();
}

class _PrivoxTabsScreenState extends State<PrivoxTabsScreen> {
  int _selectedIndex = 0;
  late final PageController _pageController;
  late final List<Widget> _widgetOptions;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    _widgetOptions = <Widget>[
      WelcomeScreen(
        username: widget.username,
        userId: widget.userId,
        deviceId: widget.deviceId,
      ),
      const UsersListAddScreen(),
      const UsersListSolScreen(),
    ];
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
    _pageController.animateToPage(
      index,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeInOut,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: PageView(
        controller: _pageController,
        onPageChanged: (index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        children: _widgetOptions,
      ),
      bottomNavigationBar: PrivoxBottomMenu(
        currentIndex: _selectedIndex,
        onTap: _onItemTapped,
      ),
    );
  }
}

class PrivoxBottomMenu extends StatelessWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;

  const PrivoxBottomMenu({
    super.key,
    required this.currentIndex,
    required this.onTap,
  });

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
          onDestinationSelected: onTap,
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