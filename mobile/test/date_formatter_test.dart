import 'package:flutter_test/flutter_test.dart';
import 'package:hadivo_mobile/core/utils/date_formatter.dart';

void main() {
  test('formats work duration minutes', () {
    expect(DateFormatter.duration(null), '-');
    expect(DateFormatter.duration(45), '45m');
    expect(DateFormatter.duration(120), '2h');
    expect(DateFormatter.duration(135), '2h 15m');
  });
}
