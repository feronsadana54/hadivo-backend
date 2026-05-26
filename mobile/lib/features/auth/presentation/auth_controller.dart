import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/api/dio_client.dart';
import '../../../core/auth/token_storage.dart';
import '../../../core/notifications/notification_registration_service.dart';
import '../data/auth_repository.dart';
import '../domain/auth_state.dart';

final authControllerProvider = StateNotifierProvider<AuthController, AuthState>(
  (ref) {
    final controller = AuthController(
      ref.watch(authRepositoryProvider),
      ref.watch(tokenStorageProvider),
      ref.watch(notificationRegistrationServiceProvider),
    );

    ref.listen<int>(unauthorizedSignalProvider, (previous, next) {
      if ((previous ?? 0) != next) {
        controller.handleUnauthorized();
      }
    });

    return controller;
  },
);

class AuthController extends StateNotifier<AuthState> {
  AuthController(this._repository, this._tokenStorage, this._notifications)
    : super(const AuthState.bootstrapping()) {
    _restoreSession();
  }

  final AuthRepository _repository;
  final TokenStorage _tokenStorage;
  final NotificationRegistrationService _notifications;

  Future<void> _restoreSession() async {
    try {
      final token = await _tokenStorage.readAccessToken();
      if (token == null || token.isEmpty || _isExpired(token)) {
        await _tokenStorage.clear();
        state = const AuthState.unauthenticated();
        return;
      }

      final email = await _tokenStorage.readEmail() ?? _emailFromToken(token);
      state = AuthState.authenticated(email: email ?? 'user@hadivo.local');
      unawaited(_registerNotificationToken());
    } catch (_) {
      await _tokenStorage.clear();
      state = const AuthState.unauthenticated();
    }
  }

  Future<bool> login({required String email, required String password}) async {
    state = const AuthState.unauthenticated().asSubmitting();
    try {
      final tokenPair = await _repository.login(
        email: email,
        password: password,
      );
      final tokenEmail = _emailFromToken(tokenPair.accessToken) ?? email;
      await _tokenStorage.saveSession(
        accessToken: tokenPair.accessToken,
        refreshToken: tokenPair.refreshToken,
        email: tokenEmail,
      );
      state = AuthState.authenticated(email: tokenEmail);
      unawaited(_registerNotificationToken());
      return true;
    } on ApiException catch (error) {
      final message = error.code == 'UNAUTHORIZED'
          ? 'Email atau password belum benar.'
          : error.friendlyMessage;
      state = AuthState.unauthenticated(errorMessage: message);
      return false;
    } catch (_) {
      state = const AuthState.unauthenticated(
        errorMessage: 'Login belum berhasil. Periksa email dan password.',
      );
      return false;
    }
  }

  Future<void> logout() async {
    await _tokenStorage.clear();
    state = const AuthState.unauthenticated();
  }

  Future<void> handleUnauthorized() async {
    await _tokenStorage.clear();
    state = const AuthState.unauthenticated(
      errorMessage: 'Sesi Anda sudah berakhir. Silakan login kembali.',
    );
  }

  Future<void> _registerNotificationToken() async {
    await _notifications.registerIfAvailable();
  }

  bool _isExpired(String token) {
    final payload = _decodePayload(token);
    final exp = payload?['exp'];
    if (exp is! int) return false;
    final expiresAt = DateTime.fromMillisecondsSinceEpoch(exp * 1000);
    return DateTime.now().isAfter(expiresAt);
  }

  String? _emailFromToken(String token) {
    final payload = _decodePayload(token);
    return payload?['email']?.toString();
  }

  Map<String, dynamic>? _decodePayload(String token) {
    final parts = token.split('.');
    if (parts.length != 3) return null;
    try {
      final normalized = base64Url.normalize(parts[1]);
      final decoded = utf8.decode(base64Url.decode(normalized));
      final payload = jsonDecode(decoded);
      if (payload is Map<String, dynamic>) return payload;
    } catch (_) {
      return null;
    }
    return null;
  }
}
