import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/state_message.dart';
import '../data/leave_request_repository.dart';
import '../domain/leave_request.dart';

class LeaveRequestListScreen extends ConsumerWidget {
  const LeaveRequestListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final listAsync = ref.watch(leaveRequestListProvider);

    return SafeArea(
      child: Stack(
        children: [
          RefreshIndicator(
            onRefresh: () => ref.refresh(leaveRequestListProvider.future),
            child: listAsync.when(
              data: (rows) => _buildList(context, ref, rows),
              loading: () => ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: const [
                  SizedBox(height: 80),
                  Center(child: CircularProgressIndicator()),
                ],
              ),
              error: (error, _) => ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: [
                  StateMessage(
                    title: 'Gagal memuat pengajuan',
                    message: error is ApiException
                        ? error.friendlyMessage
                        : 'Coba periksa koneksi dan ulangi.',
                    icon: Icons.error_outline,
                  ),
                ],
              ),
            ),
          ),
          Positioned(
            right: 16,
            bottom: 16,
            child: FloatingActionButton.extended(
              onPressed: () => context.push('/requests/new'),
              icon: const Icon(Icons.add),
              label: const Text('Pengajuan'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildList(
    BuildContext context,
    WidgetRef ref,
    List<LeaveRequest> rows,
  ) {
    if (rows.isEmpty) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: const [
          SizedBox(height: 24),
          StateMessage(
            title: 'Belum ada pengajuan',
            message:
                'Tekan tombol pengajuan untuk mengajukan izin, sakit, cuti, atau koreksi absensi.',
            icon: Icons.assignment_outlined,
          ),
        ],
      );
    }

    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      itemCount: rows.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (context, index) {
        final row = rows[index];
        return _RequestCard(row: row);
      },
    );
  }
}

class _RequestCard extends ConsumerStatefulWidget {
  const _RequestCard({required this.row});

  final LeaveRequest row;

  @override
  ConsumerState<_RequestCard> createState() => _RequestCardState();
}

class _RequestCardState extends ConsumerState<_RequestCard> {
  bool _isCancelling = false;

  @override
  Widget build(BuildContext context) {
    final row = widget.row;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  row.requestType.label,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              _StatusChip(status: row.status),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${row.startDate} s.d. ${row.endDate}',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (row.reason != null && row.reason!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              row.reason!,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: Colors.black87),
            ),
          ],
          if (row.reviewNote != null && row.reviewNote!.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              'Catatan reviewer: ${row.reviewNote!}',
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: Colors.black54),
            ),
          ],
          if (row.canCancel) ...[
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton.icon(
                onPressed: _isCancelling ? null : _cancel,
                icon: const Icon(Icons.cancel_outlined, size: 18),
                label: const Text('Batalkan'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _cancel() async {
    setState(() => _isCancelling = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(leaveRequestRepositoryProvider).cancel(widget.row.id);
      ref.invalidate(leaveRequestListProvider);
      messenger.showSnackBar(
        const SnackBar(content: Text('Pengajuan dibatalkan.')),
      );
    } on ApiException catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(error.friendlyMessage)));
    } finally {
      if (mounted) setState(() => _isCancelling = false);
    }
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final LeaveRequestStatus status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      LeaveRequestStatus.pending => Colors.amber.shade100,
      LeaveRequestStatus.approved => Colors.green.shade100,
      LeaveRequestStatus.rejected => Colors.red.shade100,
      LeaveRequestStatus.cancelled => Colors.grey.shade300,
    };
    final textColor = switch (status) {
      LeaveRequestStatus.pending => Colors.brown,
      LeaveRequestStatus.approved => Colors.green.shade800,
      LeaveRequestStatus.rejected => Colors.red.shade700,
      LeaveRequestStatus.cancelled => Colors.black54,
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        status.label,
        style: TextStyle(
          color: textColor,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
