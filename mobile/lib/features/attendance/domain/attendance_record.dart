class AttendanceRecord {
  const AttendanceRecord({
    required this.id,
    required this.tenantId,
    required this.userId,
    required this.date,
    required this.status,
    this.clockInAt,
    this.clockOutAt,
    this.clockInLatitude,
    this.clockInLongitude,
    this.clockOutLatitude,
    this.clockOutLongitude,
    this.clockInDeviceId,
    this.clockOutDeviceId,
    this.clockOutOutsideRadius = false,
    this.workDurationMinutes,
  });

  final String id;
  final String tenantId;
  final String userId;
  final String date;
  final String status;
  final DateTime? clockInAt;
  final DateTime? clockOutAt;
  final double? clockInLatitude;
  final double? clockInLongitude;
  final double? clockOutLatitude;
  final double? clockOutLongitude;
  final String? clockInDeviceId;
  final String? clockOutDeviceId;
  final bool clockOutOutsideRadius;
  final int? workDurationMinutes;

  bool get hasClockedIn => clockInAt != null;
  bool get hasClockedOut => clockOutAt != null;

  factory AttendanceRecord.fromJson(Map<String, dynamic> json) {
    return AttendanceRecord(
      id: json['id']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      userId: json['userId']?.toString() ?? '',
      date: json['date']?.toString() ?? '',
      status: json['status']?.toString() ?? 'UNKNOWN',
      clockInAt: _dateTime(json['clockInAt']),
      clockOutAt: _dateTime(json['clockOutAt']),
      clockInLatitude: _double(json['clockInLatitude']),
      clockInLongitude: _double(json['clockInLongitude']),
      clockOutLatitude: _double(json['clockOutLatitude']),
      clockOutLongitude: _double(json['clockOutLongitude']),
      clockInDeviceId: json['clockInDeviceId']?.toString(),
      clockOutDeviceId: json['clockOutDeviceId']?.toString(),
      clockOutOutsideRadius: json['clockOutOutsideRadius'] == true,
      workDurationMinutes: _int(json['workDurationMinutes']),
    );
  }

  static DateTime? _dateTime(Object? value) {
    if (value == null) return null;
    return DateTime.tryParse(value.toString());
  }

  static double? _double(Object? value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '');
  }

  static int? _int(Object? value) {
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }
}
