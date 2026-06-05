enum FaceEnrollmentStatus { pending, active, reset }

class FaceProfile {
  const FaceProfile({
    required this.profileId,
    required this.enrollmentStatus,
    required this.consentGiven,
    required this.imageStored,
    required this.message,
    this.enrolledAt,
    this.resetAt,
    this.updatedAt,
  });

  final String profileId;
  final FaceEnrollmentStatus enrollmentStatus;
  final bool consentGiven;
  final bool imageStored;
  final String message;
  final DateTime? enrolledAt;
  final DateTime? resetAt;
  final DateTime? updatedAt;

  factory FaceProfile.fromJson(Map<String, dynamic> json) {
    return FaceProfile(
      profileId: json['profileId']?.toString() ?? '',
      enrollmentStatus: _parseStatus(json['enrollmentStatus']?.toString()),
      consentGiven: json['consentGiven'] == true,
      imageStored: json['imageStored'] == true,
      message: json['message']?.toString() ?? '',
      enrolledAt: _parseDate(json['enrolledAt']),
      resetAt: _parseDate(json['resetAt']),
      updatedAt: _parseDate(json['updatedAt']),
    );
  }

  static FaceEnrollmentStatus _parseStatus(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'ACTIVE':
        return FaceEnrollmentStatus.active;
      case 'RESET':
        return FaceEnrollmentStatus.reset;
      default:
        return FaceEnrollmentStatus.pending;
    }
  }

  static DateTime? _parseDate(Object? value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }
}
