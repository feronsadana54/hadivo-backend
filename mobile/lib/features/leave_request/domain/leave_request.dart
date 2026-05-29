enum LeaveRequestType {
  sick,
  permission,
  annualLeave,
  businessTrip,
  attendanceCorrection;

  String get apiValue {
    switch (this) {
      case LeaveRequestType.sick:
        return 'SICK';
      case LeaveRequestType.permission:
        return 'PERMISSION';
      case LeaveRequestType.annualLeave:
        return 'ANNUAL_LEAVE';
      case LeaveRequestType.businessTrip:
        return 'BUSINESS_TRIP';
      case LeaveRequestType.attendanceCorrection:
        return 'ATTENDANCE_CORRECTION';
    }
  }

  String get label {
    switch (this) {
      case LeaveRequestType.sick:
        return 'Sakit';
      case LeaveRequestType.permission:
        return 'Izin';
      case LeaveRequestType.annualLeave:
        return 'Cuti';
      case LeaveRequestType.businessTrip:
        return 'Dinas luar';
      case LeaveRequestType.attendanceCorrection:
        return 'Koreksi absensi';
    }
  }

  static LeaveRequestType fromApi(String? value) {
    switch (value) {
      case 'SICK':
        return LeaveRequestType.sick;
      case 'PERMISSION':
        return LeaveRequestType.permission;
      case 'ANNUAL_LEAVE':
        return LeaveRequestType.annualLeave;
      case 'BUSINESS_TRIP':
        return LeaveRequestType.businessTrip;
      case 'ATTENDANCE_CORRECTION':
        return LeaveRequestType.attendanceCorrection;
      default:
        return LeaveRequestType.permission;
    }
  }
}

enum LeaveRequestStatus {
  pending,
  approved,
  rejected,
  cancelled;

  String get label {
    switch (this) {
      case LeaveRequestStatus.pending:
        return 'Menunggu';
      case LeaveRequestStatus.approved:
        return 'Disetujui';
      case LeaveRequestStatus.rejected:
        return 'Ditolak';
      case LeaveRequestStatus.cancelled:
        return 'Dibatalkan';
    }
  }

  static LeaveRequestStatus fromApi(String? value) {
    switch (value) {
      case 'APPROVED':
        return LeaveRequestStatus.approved;
      case 'REJECTED':
        return LeaveRequestStatus.rejected;
      case 'CANCELLED':
        return LeaveRequestStatus.cancelled;
      default:
        return LeaveRequestStatus.pending;
    }
  }
}

class LeaveRequest {
  const LeaveRequest({
    required this.id,
    required this.requestType,
    required this.startDate,
    required this.endDate,
    required this.status,
    required this.createdAt,
    this.reason,
    this.reviewNote,
    this.reviewerFullName,
    this.reviewedAt,
  });

  final String id;
  final LeaveRequestType requestType;
  final String startDate;
  final String endDate;
  final LeaveRequestStatus status;
  final DateTime createdAt;
  final String? reason;
  final String? reviewNote;
  final String? reviewerFullName;
  final DateTime? reviewedAt;

  bool get canCancel => status == LeaveRequestStatus.pending;

  factory LeaveRequest.fromJson(Map<String, dynamic> json) {
    return LeaveRequest(
      id: json['id']?.toString() ?? '',
      requestType: LeaveRequestType.fromApi(json['requestType']?.toString()),
      startDate: json['startDate']?.toString() ?? '',
      endDate: json['endDate']?.toString() ?? '',
      status: LeaveRequestStatus.fromApi(json['status']?.toString()),
      createdAt:
          DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
          DateTime.now(),
      reason: _string(json['reason']),
      reviewNote: _string(json['reviewNote']),
      reviewerFullName: _string(json['reviewerFullName']),
      reviewedAt: DateTime.tryParse(json['reviewedAt']?.toString() ?? ''),
    );
  }

  static String? _string(Object? value) {
    final s = value?.toString();
    if (s == null || s.isEmpty) return null;
    return s;
  }
}
