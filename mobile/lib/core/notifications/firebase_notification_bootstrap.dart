import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';

class FirebaseNotificationBootstrap {
  const FirebaseNotificationBootstrap._();

  static Future<void> initialize() async {
    if (!AppConfig.enableFirebaseMessaging) return;

    try {
      await Firebase.initializeApp();
    } catch (_) {
      debugPrint(
        'Firebase messaging is not configured. Push registration is skipped.',
      );
    }
  }
}
