import 'package:flutter/rendering.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../variables.dart';

Future<void> initPreferences() async {
  try {
    final prefs = await SharedPreferences.getInstance();
    
    // stayOnline preference
    final _stayOnline = prefs.getBool('stayOnline');
    if (_stayOnline != null) {
      STAY_ONLINE = _stayOnline;
    } else {
      // Si no existe, crear con valor inicial
      await prefs.setBool('stayOnline', STAY_ONLINE);
    }

    // notificationsEnabled preference
    final _notificationsEnabled = prefs.getBool('notificationsEnabled');
    if (_notificationsEnabled != null) {
      NOTIFICATIONS_ENABLED = _notificationsEnabled;
    } else {
      // Si no existe, crear con valor inicial
      await prefs.setBool('notificationsEnabled', NOTIFICATIONS_ENABLED);
    }

  } catch (_) {}
}

Future<void> getPreferencesInit() async {
  try {
    
    final prefs = await SharedPreferences.getInstance();

    STAY_ONLINE = prefs.getBool('stayOnline') ?? STAY_ONLINE;
    NOTIFICATIONS_ENABLED = prefs.getBool('notificationsEnabled') ?? NOTIFICATIONS_ENABLED;

  } catch (_) {}
}

Future<void> setStayOnlinePref(bool value) async {
  try {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('stayOnline', value);
    STAY_ONLINE = value;
  } catch (_) {}
}

Future<void> setNotificationsEnabledPref(bool value) async {
  try {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('notificationsEnabled', value);
    NOTIFICATIONS_ENABLED = value;
  } catch (_) {}
} 

