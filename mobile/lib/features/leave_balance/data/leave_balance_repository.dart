import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../../../core/auth/token_storage.dart';
import '../../../core/config/app_config.dart';
import '../domain/leave_balance.dart';

final leaveBalanceRepositoryProvider = Provider<LeaveBalanceRepository>((ref) {
  return LeaveBalanceRepository(
    ref.watch(dioProvider),
    ref.watch(tokenStorageProvider),
  );
});

final myLeaveBalanceProvider = FutureProvider.autoDispose<LeaveBalance?>((ref) {
  return ref.watch(leaveBalanceRepositoryProvider).fetchMine();
});

class LeaveBalanceRepository {
  LeaveBalanceRepository(this._dio, this._tokenStorage);

  final Dio _dio;
  final TokenStorage _tokenStorage;

  Future<LeaveBalance?> fetchMine() async {
    final token = await _tokenStorage.readAccessToken();
    if (token == null || token.isEmpty) return null;
    final userId = _userIdFromToken(token);
    if (userId == null) return null;

    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/members/$userId/leave-balance',
      );
      final data = response.data?['data'];
      if (data is Map<String, dynamic>) return LeaveBalance.fromJson(data);
      return null;
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  String? _userIdFromToken(String token) {
    final parts = token.split('.');
    if (parts.length != 3) return null;
    try {
      final normalized = base64Url.normalize(parts[1]);
      final decoded = utf8.decode(base64Url.decode(normalized));
      final payload = jsonDecode(decoded);
      if (payload is Map<String, dynamic>) {
        return payload['sub']?.toString();
      }
    } catch (_) {
      return null;
    }
    return null;
  }
}
