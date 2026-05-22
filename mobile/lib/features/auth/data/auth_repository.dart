import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../domain/token_pair.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(dioProvider));
});

class AuthRepository {
  AuthRepository(this._dio);

  final Dio _dio;

  Future<TokenPair> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {'email': email, 'password': password},
      );
      final body = response.data;
      final data = body?['data'];
      if (data is Map<String, dynamic>) {
        return TokenPair.fromJson(data);
      }
      throw const ApiException(
        code: 'INVALID_RESPONSE',
        message: 'Login response is invalid.',
      );
    } on DioException catch (error) {
      throw ApiException.fromDio(error);
    }
  }
}
