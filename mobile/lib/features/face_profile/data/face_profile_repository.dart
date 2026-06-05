import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../../../core/auth/token_storage.dart';
import '../../../core/config/app_config.dart';
import '../domain/face_profile.dart';

final faceProfileRepositoryProvider = Provider<FaceProfileRepository>((ref) {
  return FaceProfileRepository(
    ref.watch(dioProvider),
    ref.watch(tokenStorageProvider),
  );
});

final myFaceProfileProvider = FutureProvider.autoDispose<FaceProfile?>((ref) {
  return ref.watch(faceProfileRepositoryProvider).fetchMine();
});

class FaceProfileRepository {
  FaceProfileRepository(this._dio, this._tokenStorage);

  final Dio _dio;
  final TokenStorage _tokenStorage;

  Future<String?> _myUserId() async {
    final token = await _tokenStorage.readAccessToken();
    if (token == null || token.isEmpty) return null;
    return _userIdFromToken(token);
  }

  Future<FaceProfile?> fetchMine() async {
    final userId = await _myUserId();
    if (userId == null) return null;

    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/members/$userId/face-profile',
      );
      final data = response.data?['data'];
      if (data is Map<String, dynamic>) return FaceProfile.fromJson(data);
      return null;
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }

  Future<FaceProfile> enrollMine({
    required String imageBase64,
    required bool consentGiven,
  }) async {
    final userId = await _myUserId();
    if (userId == null) {
      throw const ApiException(
        code: 'UNAUTHORIZED',
        message: 'Sesi tidak valid',
      );
    }
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/tenants/${AppConfig.tenantId}/members/$userId/face-profile/enroll',
        data: {'imageBase64': imageBase64, 'consentGiven': consentGiven},
      );
      final data = response.data?['data'];
      if (data is Map<String, dynamic>) return FaceProfile.fromJson(data);
      throw const ApiException(
        code: 'API_ERROR',
        message: 'Respons enrollment tidak valid',
      );
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
