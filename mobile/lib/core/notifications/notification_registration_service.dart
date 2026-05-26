import 'package:dio/dio.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/attendance/data/device_info_service.dart';
import '../api/dio_client.dart';
import '../config/app_config.dart';

final notificationRegistrationServiceProvider =
    Provider<NotificationRegistrationService>((ref) {
      return NotificationRegistrationService(
        ref.watch(dioProvider),
        ref.watch(deviceInfoServiceProvider),
      );
    });

class NotificationRegistrationService {
  const NotificationRegistrationService(this._dio, this._deviceInfo);

  final Dio _dio;
  final DeviceInfoService _deviceInfo;

  Future<void> registerIfAvailable() async {
    if (!AppConfig.enableFirebaseMessaging || Firebase.apps.isEmpty) return;

    try {
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission(alert: true, badge: true, sound: true);
      final token = await messaging.getToken();
      if (token == null || token.trim().isEmpty) return;

      final device = await _deviceInfo.currentDevice();
      await _dio.post<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/notification-tokens',
        data: {
          'deviceId': device.deviceId,
          'fcmToken': token,
          'platform': device.platform,
        },
      );
    } catch (_) {
      debugPrint('Push token registration skipped.');
    }
  }
}
