import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/config/app_config.dart';
import '../../../core/utils/date_formatter.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/state_message.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../auth/presentation/auth_controller.dart';
import '../data/attendance_repository.dart';
import '../data/location_service.dart';
import '../domain/attendance_record.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _isSubmitting = false;

  @override
  Widget build(BuildContext context) {
    final todayAsync = ref.watch(todayAttendanceProvider);
    final email = ref.watch(authControllerProvider).email ?? 'user';

    return SafeArea(
      child: RefreshIndicator(
        onRefresh: () => ref.refresh(todayAttendanceProvider.future),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            _Header(email: email),
            const SizedBox(height: 16),
            if (AppConfig.useDemoLocation) ...[
              const _DemoLocationBanner(),
              const SizedBox(height: 16),
            ],
            todayAsync.when(
              data: (record) => _TodayCard(
                record: record,
                isSubmitting: _isSubmitting,
                onClockIn: () => _clock(isClockIn: true),
                onClockOut: () => _clock(isClockIn: false),
                onRefresh: () => ref.invalidate(todayAttendanceProvider),
              ),
              loading: () => const AppCard(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 32),
                  child: Center(child: CircularProgressIndicator()),
                ),
              ),
              error: (error, _) => AppCard(
                child: StateMessage(
                  title: 'Data absensi belum bisa dimuat',
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

  Future<void> _clock({required bool isClockIn}) async {
    setState(() => _isSubmitting = true);
    try {
      final location = await ref
          .read(locationServiceProvider)
          .currentAttendanceLocation();
      final repository = ref.read(attendanceRepositoryProvider);
      if (isClockIn) {
        await repository.clockIn(location);
      } else {
        await repository.clockOut(location);
      }
      ref.invalidate(todayAttendanceProvider);
      _showSnackBar(
        isClockIn
            ? 'Clock-in berhasil. Semoga harimu lancar.'
            : 'Clock-out berhasil. Terima kasih.',
      );
    } on ApiException catch (error) {
      _showSnackBar(error.friendlyMessage, isError: true);
    } on LocationException catch (error) {
      _showSnackBar(error.message, isError: true);
    } catch (_) {
      _showSnackBar(
        'Absensi belum berhasil. Silakan coba lagi.',
        isError: true,
      );
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  String _messageFor(Object error) {
    if (error is ApiException) return error.friendlyMessage;
    return 'Tarik layar ke bawah untuk mencoba lagi.';
  }

  void _showSnackBar(String message, {bool isError = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: isError ? const Color(0xFFB91C1C) : null,
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.email});

  final String email;

  @override
  Widget build(BuildContext context) {
    final name = email.split('@').first;
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Halo, $name',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Absensi hari ini',
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
              ),
            ],
          ),
        ),
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: const Color(0xFFE5E7EB)),
          ),
          child: const Icon(Icons.badge_outlined, color: Colors.black54),
        ),
      ],
    );
  }
}

class _DemoLocationBanner extends StatelessWidget {
  const _DemoLocationBanner();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFEFF6FF),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: const Color(0xFFBFDBFE)),
      ),
      child: const Row(
        children: [
          Icon(Icons.location_on_outlined, color: Color(0xFF1D4ED8)),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Mode lokasi demo aktif untuk kebutuhan pengujian.',
              style: TextStyle(color: Color(0xFF1E3A8A)),
            ),
          ),
        ],
      ),
    );
  }
}

class _TodayCard extends StatelessWidget {
  const _TodayCard({
    required this.record,
    required this.isSubmitting,
    required this.onClockIn,
    required this.onClockOut,
    required this.onRefresh,
  });

  final AttendanceRecord? record;
  final bool isSubmitting;
  final VoidCallback onClockIn;
  final VoidCallback onClockOut;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final currentRecord = record;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Status hari ini',
                  style: Theme.of(
                    context,
                  ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
                ),
              ),
              IconButton(
                tooltip: 'Refresh',
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (currentRecord == null) ...[
            const StateMessage(
              title: 'Belum clock-in',
              message: 'Anda belum melakukan clock-in hari ini.',
              icon: Icons.login_outlined,
            ),
          ] else ...[
            Row(
              children: [
                StatusBadge(status: currentRecord.status),
                const Spacer(),
                Text(
                  DateFormatter.dateLabel(currentRecord.date),
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
                ),
              ],
            ),
            const SizedBox(height: 18),
            _TimeRow(
              label: 'Jam masuk',
              value: DateFormatter.time(currentRecord.clockInAt),
              icon: Icons.login,
            ),
            const SizedBox(height: 12),
            _TimeRow(
              label: 'Jam keluar',
              value: DateFormatter.time(currentRecord.clockOutAt),
              icon: Icons.logout,
            ),
            const SizedBox(height: 12),
            _TimeRow(
              label: 'Durasi',
              value: DateFormatter.duration(currentRecord.workDurationMinutes),
              icon: Icons.timer_outlined,
            ),
          ],
          const SizedBox(height: 22),
          _ActionButton(
            record: currentRecord,
            isSubmitting: isSubmitting,
            onClockIn: onClockIn,
            onClockOut: onClockOut,
          ),
        ],
      ),
    );
  }
}

class _TimeRow extends StatelessWidget {
  const _TimeRow({
    required this.label,
    required this.value,
    required this.icon,
  });

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 20, color: Colors.black45),
        const SizedBox(width: 10),
        Expanded(
          child: Text(label, style: Theme.of(context).textTheme.bodyMedium),
        ),
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

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.record,
    required this.isSubmitting,
    required this.onClockIn,
    required this.onClockOut,
  });

  final AttendanceRecord? record;
  final bool isSubmitting;
  final VoidCallback onClockIn;
  final VoidCallback onClockOut;

  @override
  Widget build(BuildContext context) {
    if (record?.hasClockedOut == true) {
      return OutlinedButton.icon(
        onPressed: null,
        icon: const Icon(Icons.check_circle_outline),
        label: const Text('Absensi hari ini selesai'),
      );
    }

    final canClockOut = record?.hasClockedIn == true;
    return ElevatedButton.icon(
      onPressed: isSubmitting ? null : (canClockOut ? onClockOut : onClockIn),
      icon: isSubmitting
          ? const SizedBox(
              width: 18,
              height: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(canClockOut ? Icons.logout : Icons.login),
      label: Text(canClockOut ? 'Clock Out' : 'Clock In'),
    );
  }
}
