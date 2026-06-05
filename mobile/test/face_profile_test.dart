import 'package:flutter_test/flutter_test.dart';
import 'package:hadivo_mobile/features/face_profile/domain/face_profile.dart';

void main() {
  test('FaceProfile.fromJson parses ACTIVE status and flags', () {
    final profile = FaceProfile.fromJson({
      'profileId': '11111111-1111-1111-1111-111111111111',
      'enrollmentStatus': 'ACTIVE',
      'consentGiven': true,
      'imageStored': true,
      'enrolledAt': '2026-06-05T03:00:00Z',
      'updatedAt': '2026-06-05T03:00:00Z',
      'message': 'OK',
    });

    expect(profile.enrollmentStatus, FaceEnrollmentStatus.active);
    expect(profile.consentGiven, isTrue);
    expect(profile.imageStored, isTrue);
    expect(profile.enrolledAt, isNotNull);
  });

  test('FaceProfile.fromJson defaults unknown status to pending', () {
    final profile = FaceProfile.fromJson({
      'profileId': 'p',
      'enrollmentStatus': 'WAITING',
      'consentGiven': false,
      'imageStored': false,
      'message': '',
    });
    expect(profile.enrollmentStatus, FaceEnrollmentStatus.pending);
    expect(profile.consentGiven, isFalse);
    expect(profile.imageStored, isFalse);
  });

  test('FaceProfile.fromJson parses RESET status', () {
    final profile = FaceProfile.fromJson({
      'profileId': 'p',
      'enrollmentStatus': 'RESET',
      'consentGiven': true,
      'imageStored': false,
      'resetAt': '2026-06-05T03:00:00Z',
      'message': 'reset',
    });
    expect(profile.enrollmentStatus, FaceEnrollmentStatus.reset);
    expect(profile.imageStored, isFalse);
    expect(profile.resetAt, isNotNull);
  });
}
