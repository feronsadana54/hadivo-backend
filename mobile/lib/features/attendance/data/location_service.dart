import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../../core/config/app_config.dart';

final locationServiceProvider = Provider<LocationService>((ref) {
  return LocationService();
});

class AttendanceLocation {
  const AttendanceLocation({
    required this.latitude,
    required this.longitude,
    required this.isDemo,
  });

  final double latitude;
  final double longitude;
  final bool isDemo;
}

class LocationException implements Exception {
  const LocationException(this.message);

  final String message;

  @override
  String toString() => message;
}

class LocationService {
  Future<AttendanceLocation> currentAttendanceLocation() async {
    if (AppConfig.useDemoLocation) {
      return const AttendanceLocation(
        latitude: AppConfig.demoLatitude,
        longitude: AppConfig.demoLongitude,
        isDemo: true,
      );
    }

    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      throw const LocationException(
        'Layanan lokasi belum aktif. Aktifkan lokasi untuk melakukan absensi.',
      );
    }

    final permission = await Permission.locationWhenInUse.request();
    if (permission.isDenied) {
      throw const LocationException(
        'Izin lokasi diperlukan untuk melakukan absensi.',
      );
    }
    if (permission.isPermanentlyDenied) {
      throw const LocationException(
        'Izin lokasi diblokir. Aktifkan izin lokasi dari pengaturan aplikasi.',
      );
    }
    if (permission.isRestricted) {
      throw const LocationException('Akses lokasi dibatasi di perangkat ini.');
    }

    final position = await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        timeLimit: Duration(seconds: 12),
      ),
    );

    return AttendanceLocation(
      latitude: position.latitude,
      longitude: position.longitude,
      isDemo: false,
    );
  }
}
