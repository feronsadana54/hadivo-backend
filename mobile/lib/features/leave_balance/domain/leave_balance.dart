class LeaveBalance {
  const LeaveBalance({
    required this.userId,
    required this.year,
    required this.annualQuotaDays,
    required this.usedDays,
    required this.adjustedDays,
    required this.remainingDays,
  });

  final String userId;
  final int year;
  final double annualQuotaDays;
  final double usedDays;
  final double adjustedDays;
  final double remainingDays;

  factory LeaveBalance.fromJson(Map<String, dynamic> json) {
    return LeaveBalance(
      userId: json['userId']?.toString() ?? '',
      year: _int(json['year']) ?? DateTime.now().year,
      annualQuotaDays: _double(json['annualQuotaDays']),
      usedDays: _double(json['usedDays']),
      adjustedDays: _double(json['adjustedDays']),
      remainingDays: _double(json['remainingDays']),
    );
  }

  static int? _int(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }

  static double _double(Object? value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? 0.0;
  }
}
