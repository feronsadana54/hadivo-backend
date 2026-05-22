class HistoryRange {
  const HistoryRange({required this.from, required this.to});

  final DateTime from;
  final DateTime to;

  @override
  bool operator ==(Object other) {
    return other is HistoryRange &&
        _sameDay(other.from, from) &&
        _sameDay(other.to, to);
  }

  @override
  int get hashCode =>
      Object.hash(from.year, from.month, from.day, to.year, to.month, to.day);

  static bool _sameDay(DateTime left, DateTime right) {
    return left.year == right.year &&
        left.month == right.month &&
        left.day == right.day;
  }
}
