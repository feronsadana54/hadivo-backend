import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../../../core/config/app_config.dart';
import '../domain/leave_request.dart';

final leaveRequestRepositoryProvider = Provider<LeaveRequestRepository>((ref) {
  return LeaveRequestRepository(ref.watch(dioProvider));
});

final leaveRequestListProvider = FutureProvider.autoDispose<List<LeaveRequest>>(
  (ref) {
    return ref.watch(leaveRequestRepositoryProvider).list();
  },
);

class CreateLeaveRequestPayload {
  const CreateLeaveRequestPayload({
    required this.requestType,
    required this.startDate,
    required this.endDate,
    this.reason,
    this.requestedClockInTime,
    this.requestedClockOutTime,
  });

  final LeaveRequestType requestType;
  final String startDate;
  final String endDate;
  final String? reason;
  final DateTime? requestedClockInTime;
  final DateTime? requestedClockOutTime;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'requestType': requestType.apiValue,
      'startDate': startDate,
      'endDate': endDate,
    };
    if (reason != null && reason!.trim().isNotEmpty) {
      map['reason'] = reason!.trim();
    }
    if (requestedClockInTime != null) {
      map['requestedClockInTime'] = requestedClockInTime!
          .toUtc()
          .toIso8601String();
    }
    if (requestedClockOutTime != null) {
      map['requestedClockOutTime'] = requestedClockOutTime!
          .toUtc()
          .toIso8601String();
    }
    return map;
  }
}

class LeaveRequestRepository {
  LeaveRequestRepository(this._dio);

  final Dio _dio;

  String get _basePath => '/tenants/${AppConfig.tenantId}/leave-requests';

  Future<List<LeaveRequest>> list() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_basePath);
      final data = response.data?['data'];
      if (data is List) {
        return data
            .whereType<Map<String, dynamic>>()
            .map(LeaveRequest.fromJson)
            .toList();
      }
      return const [];
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<LeaveRequest> create(CreateLeaveRequestPayload payload) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _basePath,
        data: payload.toJson(),
      );
      final data = response.data?['data'];
      if (data is Map<String, dynamic>) return LeaveRequest.fromJson(data);
      throw const ApiException(
        code: 'INVALID_RESPONSE',
        message: 'Leave request response is invalid.',
      );
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<LeaveRequest> cancel(String requestId) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '$_basePath/$requestId/cancel',
      );
      final data = response.data?['data'];
      if (data is Map<String, dynamic>) return LeaveRequest.fromJson(data);
      throw const ApiException(
        code: 'INVALID_RESPONSE',
        message: 'Leave request response is invalid.',
      );
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }
}
