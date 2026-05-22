import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/utils/date_formatter.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/state_message.dart';
import '../../../shared/widgets/status_badge.dart';
import '../data/attendance_repository.dart';
import '../domain/attendance_record.dart';
import '../domain/history_range.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  late HistoryRange _range;

  @override
  void initState() {
    super.initState();
    final today = DateTime.now();
    final normalizedToday = DateTime(today.year, today.month, today.day);
    _range = HistoryRange(
      from: normalizedToday.subtract(const Duration(days: 6)),
      to: normalizedToday,
    );
  }

  @override
  Widget build(BuildContext context) {
    final recordsAsync = ref.watch(attendanceHistoryProvider(_range));

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: () => ref.refresh(attendanceHistoryProvider(_range).future),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Riwayat',
                        style: Theme.of(context).textTheme.headlineSmall
                            ?.copyWith(fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${DateFormatter.apiDate(_range.from)} to ${DateFormatter.apiDate(_range.to)}',
                        style: Theme.of(
                          context,
                        ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Refresh',
                  onPressed: () =>
                      ref.invalidate(attendanceHistoryProvider(_range)),
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: 16),
            recordsAsync.when(
              data: (records) {
                if (records.isEmpty) {
                  return const AppCard(
                    child: StateMessage(
                      title: 'Belum ada riwayat',
                      message:
                          'Belum ada riwayat absensi dalam 7 hari terakhir.',
                      icon: Icons.event_busy_outlined,
                    ),
                  );
                }
                return Column(
                  children: [
                    for (final record in records) ...[
                      _HistoryItem(record: record),
                      const SizedBox(height: 10),
                    ],
                  ],
                );
              },
              loading: () => const AppCard(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 32),
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
              error: (error, _) => AppCard(
                child: StateMessage(
                  title: 'Riwayat belum bisa dimuat',
                  message: _messageFor(error),
                  icon: Icons.wifi_off_outlined,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _messageFor(Object error) {
    if (error is ApiException) return error.friendlyMessage;
    return 'Tarik layar ke bawah untuk mencoba lagi.';
  }
}

class _HistoryItem extends StatelessWidget {
  const _HistoryItem({required this.record});

  final AttendanceRecord record;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  DateFormatter.dateLabel(record.date),
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              StatusBadge(status: record.status),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: _Metric(
                  label: 'Masuk',
                  value: DateFormatter.time(record.clockInAt),
                  icon: Icons.login,
                ),
              ),
              Expanded(
                child: _Metric(
                  label: 'Keluar',
                  value: DateFormatter.time(record.clockOutAt),
                  icon: Icons.logout,
                ),
              ),
              Expanded(
                child: _Metric(
                  label: 'Durasi',
                  value: DateFormatter.duration(record.workDurationMinutes),
                  icon: Icons.timer_outlined,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value, required this.icon});

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 16, color: Colors.black45),
            const SizedBox(width: 5),
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
      ],
    );
  }
}
