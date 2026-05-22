import 'package:intl/intl.dart';

class DateFormatter {
  DateFormatter._();

  static final DateFormat _apiDateFormat = DateFormat('yyyy-MM-dd');
  static final DateFormat _dateFormat = DateFormat('d MMM yyyy');
  static final DateFormat _timeFormat = DateFormat('HH:mm');

  static String apiDate(DateTime date) => _apiDateFormat.format(date);

  static String dateLabel(String apiDate) {
    final parsed = DateTime.tryParse(apiDate);
    if (parsed == null) return apiDate;
    return _dateFormat.format(parsed);
  }

  static String time(DateTime? value) {
    if (value == null) return '-';
    return _timeFormat.format(value.toLocal());
  }

  static String duration(int? minutes) {
    if (minutes == null) return '-';
    final hours = minutes ~/ 60;
    final remainingMinutes = minutes % 60;
    if (hours == 0) return '${remainingMinutes}m';
    if (remainingMinutes == 0) return '${hours}h';
    return '${hours}h ${remainingMinutes}m';
  }
}
