import 'package:dio/dio.dart';

class ApiException implements Exception {
  const ApiException({
    required this.code,
    required this.message,
    this.statusCode,
  });

  final String code;
  final String message;
  final int? statusCode;

  factory ApiException.fromDio(DioException error) {
    final statusCode = error.response?.statusCode;
    final body = error.response?.data;
    if (body is Map<String, dynamic>) {
      final errorBody = body['error'];
      if (errorBody is Map<String, dynamic>) {
        return ApiException(
          code: errorBody['code']?.toString() ?? 'API_ERROR',
          message: errorBody['message']?.toString() ?? 'Request failed',
          statusCode: statusCode,
        );
      }
    }

    final fallback = switch (error.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.receiveTimeout ||
      DioExceptionType.sendTimeout =>
        'Koneksi terlalu lama. Pastikan backend berjalan.',
      DioExceptionType.connectionError =>
        'Tidak dapat terhubung ke server. Periksa koneksi atau pastikan backend berjalan.',
      _ => error.message ?? 'Permintaan belum berhasil.',
    };

    return ApiException(
      code: statusCode == 401 ? 'UNAUTHORIZED' : 'NETWORK_ERROR',
      message: fallback,
      statusCode: statusCode,
    );
  }

  String get friendlyMessage {
    return switch (code) {
      'OUT_OF_RADIUS' =>
        'Anda berada di luar area absensi. Pastikan Anda berada dekat lokasi kantor/sekolah.',
      'DUPLICATE_CLOCK_IN' => 'Anda sudah melakukan clock-in hari ini.',
      'NO_CLOCK_IN' => 'Anda belum melakukan clock-in.',
      'ALREADY_CLOCKED_OUT' => 'Anda sudah melakukan clock-out hari ini.',
      'FACE_MISMATCH' => 'Verifikasi wajah gagal.',
      'LATE_NOT_ALLOWED' => 'Clock-in terlambat tidak diizinkan.',
      'UNAUTHORIZED' => 'Sesi Anda sudah berakhir. Silakan login kembali.',
      'NETWORK_ERROR' =>
        'Tidak dapat terhubung ke server. Periksa koneksi atau pastikan backend berjalan.',
      _ =>
        message.isNotEmpty
            ? message
            : 'Permintaan belum berhasil. Silakan coba lagi.',
    };
  }

  @override
  String toString() => '$code: $message';
}
