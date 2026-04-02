import 'package:flutter/material.dart';

class LogoutButton extends StatelessWidget {
  final Future<void> Function() onLogout;

  const LogoutButton({
    super.key,
    required this.onLogout,
  });

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: 'Cerrar sesión',
      icon: const Icon(Icons.exit_to_app),
      onPressed: () {
        showDialog(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('Cerrar sesión'),
            content: const Text(
              '¿Estás seguro de que deseas cerrar sesión?'
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancelar'),
              ),
              TextButton(
                onPressed: () async {
                  await onLogout();
                },
                child: const Text(
                  'Cerrar sesión',
                  style: TextStyle(color: Colors.redAccent),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}