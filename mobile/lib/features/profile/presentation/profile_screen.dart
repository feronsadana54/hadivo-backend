import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/config/app_config.dart';
import '../../../shared/widgets/app_card.dart';
import '../../auth/presentation/auth_controller.dart';
import '../../face_profile/data/face_profile_repository.dart';
import '../../face_profile/domain/face_profile.dart';
import '../../leave_balance/data/leave_balance_repository.dart';
import '../../leave_balance/domain/leave_balance.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authControllerProvider);
    final email = authState.email ?? '-';
    final balanceAsync = ref.watch(myLeaveBalanceProvider);
    final faceAsync = ref.watch(myFaceProfileProvider);

    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            'Profil',
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(
              children: [
                const CircleAvatar(
                  radius: 32,
                  child: Icon(Icons.person, size: 34),
                ),
                const SizedBox(height: 14),
                Text(
                  email,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Status login: aktif',
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          _LeaveBalanceCard(balanceAsync: balanceAsync),
          const SizedBox(height: 12),
          _FaceProfileCard(
            faceAsync: faceAsync,
            onOpen: () => context.go('/profile/face'),
          ),
          const SizedBox(height: 12),
          AppCard(
            child: Column(
              children: const [
                _InfoRow(
                  icon: Icons.business_outlined,
                  label: 'Tenant demo',
                  value: AppConfig.tenantId,
                ),
                Divider(height: 22),
                _InfoRow(
                  icon: Icons.location_on_outlined,
                  label: 'Mode lokasi demo',
                  value: AppConfig.useDemoLocation ? 'Aktif' : 'Nonaktif',
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          OutlinedButton.icon(
            onPressed: () => ref.read(authControllerProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
            label: const Text('Keluar'),
          ),
        ],
      ),
    );
  }
}

class _LeaveBalanceCard extends StatelessWidget {
  const _LeaveBalanceCard({required this.balanceAsync});

  final AsyncValue<LeaveBalance?> balanceAsync;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.event_available_outlined, color: Colors.black45),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Sisa Cuti Tahunan',
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
                ),
                const SizedBox(height: 6),
                balanceAsync.when(
                  data: (balance) => _balanceBody(context, balance),
                  loading: () => const Text(
                    'Memuat...',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  error: (_, _) => const Text(
                    'Belum tersedia',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _balanceBody(BuildContext context, LeaveBalance? balance) {
    if (balance == null) {
      return const Text(
        'Belum tersedia',
        style: TextStyle(fontWeight: FontWeight.w600),
      );
    }
    final remaining = _formatDays(balance.remainingDays);
    final quota = _formatDays(balance.annualQuotaDays);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Sisa $remaining hari dari $quota hari',
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 2),
        Text(
          'Tahun ${balance.year} • Terpakai ${_formatDays(balance.usedDays)} hari',
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(color: Colors.black54),
        ),
      ],
    );
  }

  String _formatDays(double value) {
    if (value == value.roundToDouble()) return value.toStringAsFixed(0);
    return value.toStringAsFixed(2);
  }
}

class _FaceProfileCard extends StatelessWidget {
  const _FaceProfileCard({required this.faceAsync, required this.onOpen});

  final AsyncValue<FaceProfile?> faceAsync;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.face_retouching_natural_outlined,
                color: Colors.black45,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Profil Wajah',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Colors.black87,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          faceAsync.when(
            data: (profile) => _summary(context, profile),
            loading: () => const Text(
              'Memuat status...',
              style: TextStyle(fontWeight: FontWeight.w600),
            ),
            error: (_, _) => const Text(
              'Belum tersedia',
              style: TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Status ACTIVE berarti foto dan persetujuan tercatat. '
            'Pencocokan wajah belum aktif.',
            style: TextStyle(color: Colors.black54, fontSize: 12, height: 1.4),
          ),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: onOpen,
              icon: const Icon(Icons.arrow_forward),
              label: const Text('Kelola enrollment'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _summary(BuildContext context, FaceProfile? profile) {
    final label = switch (profile?.enrollmentStatus) {
      FaceEnrollmentStatus.active => 'Terdaftar',
      FaceEnrollmentStatus.reset => 'Direset',
      _ => 'Belum enroll',
    };
    return Text(
      label,
      style: Theme.of(
        context,
      ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: Colors.black45),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(color: Colors.black87),
              ),
              const SizedBox(height: 3),
              Text(
                value,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
