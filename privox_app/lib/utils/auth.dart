import 'package:shared_preferences/shared_preferences.dart';

/// Limpia las claves relacionadas con la sesión almacenadas en SharedPreferences.
///
/// Por seguridad elimina solo las claves usadas por la app: `token`, `username`,
/// `userId` y `displayName`. Si prefieres eliminar todo el contenido usa
/// `clearAllPrefs()`.
Future<void> logoutClearPrefs() async {
  final prefs = await SharedPreferences.getInstance();
  try {
    await prefs.remove('token');
    await prefs.remove('username');
    await prefs.remove('userId');
    await prefs.remove('displayName');
  } catch (_) {

  }
}

/// Elimina todas las entradas de SharedPreferences.
Future<void> clearAllPrefs() async {
  final prefs = await SharedPreferences.getInstance();
  try {
    await prefs.clear();
  } catch (_) {}
}
