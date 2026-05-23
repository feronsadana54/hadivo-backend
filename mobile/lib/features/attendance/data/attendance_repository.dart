import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../../../core/config/app_config.dart';
import '../../../core/utils/date_formatter.dart';
import '../domain/attendance_record.dart';
import '../domain/history_range.dart';
import 'device_info_service.dart';
import 'location_service.dart';

final attendanceRepositoryProvider = Provider<AttendanceRepository>((ref) {
  return AttendanceRepository(
    ref.watch(dioProvider),
    ref.watch(deviceInfoServiceProvider),
  );
});

final todayAttendanceProvider = FutureProvider.autoDispose<AttendanceRecord?>((
  ref,
) {
  return ref.watch(attendanceRepositoryProvider).today();
});

final attendanceHistoryProvider = FutureProvider.autoDispose
    .family<List<AttendanceRecord>, HistoryRange>((ref, range) {
      return ref.watch(attendanceRepositoryProvider).history(range);
    });

class AttendanceRepository {
  AttendanceRepository(this._dio, this._deviceInfo);

  final Dio _dio;
  final DeviceInfoService _deviceInfo;

  Future<AttendanceRecord?> today() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/attendance/me/today',
      );
      final data = _data(response);
      if (data == null) return null;
      if (data is Map<String, dynamic>) return AttendanceRecord.fromJson(data);
      throw _invalidResponse();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<AttendanceRecord> clockIn(AttendanceLocation location) async {
    return _clock(
      path: '/tenants/${AppConfig.tenantId}/attendance/clock-in',
      location: location,
    );
  }

  Future<AttendanceRecord> clockOut(AttendanceLocation location) async {
    return _clock(
      path: '/tenants/${AppConfig.tenantId}/attendance/clock-out',
      location: location,
    );
  }

  Future<List<AttendanceRecord>> history(HistoryRange range) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/attendance/me',
        queryParameters: {
          'from': DateFormatter.apiDate(range.from),
          'to': DateFormatter.apiDate(range.to),
        },
      );
      final data = _data(response);
      if (data is List) {
        return data
            .whereType<Map<String, dynamic>>()
            .map(AttendanceRecord.fromJson)
            .toList();
      }
      throw _invalidResponse();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<AttendanceRecord> _clock({
    required String path,
    required AttendanceLocation location,
  }) async {
    try {
      final device = await _deviceInfo.currentDevice();
      final response = await _dio.post<Map<String, dynamic>>(
        path,
        data: {
          'latitude': location.latitude,
          'longitude': location.longitude,
          'deviceId': device.deviceId,
          'deviceName': device.deviceName,
          'platform': device.platform,
          'faceImageBase64': null,
        },
      );
      final data = _data(response);
      if (data is Map<String, dynamic>) return AttendanceRecord.fromJson(data);
      throw _invalidResponse();
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Object? _data(Response<Map<String, dynamic>> response) {
    final body = response.data;
    if (body == null) return null;
    return body['data'];
  }

  ApiException _invalidResponse() {
    return const ApiException(
      code: 'INVALID_RESPONSE',
      message: 'Attendance response is invalid.',
    );
  }
}
