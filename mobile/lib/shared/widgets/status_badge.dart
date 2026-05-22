import 'package:flutter/material.dart';

class StatusBadge extends StatelessWidget {
  const StatusBadge({required this.status, super.key});

  final String status;

  @override
  Widget build(BuildContext context) {
    final spec = _StatusSpec.from(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: spec.background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: spec.border),
      ),
      child: Text(
        spec.label,
        style: TextStyle(
          color: spec.foreground,
          fontSize: 13,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _StatusSpec {
  const _StatusSpec({
    required this.label,
    required this.background,
    required this.border,
    required this.foreground,
  });

  final String label;
  final Color background;
  final Color border;
  final Color foreground;

  factory _StatusSpec.from(String status) {
    return switch (status) {
      'ON_TIME' => const _StatusSpec(
        label: 'TEPAT WAKTU',
        background: Color(0xFFECFDF5),
        border: Color(0xFFA7F3D0),
        foreground: Color(0xFF047857),
      ),
      'LATE' => const _StatusSpec(
        label: 'TERLAMBAT',
        background: Color(0xFFFFF7ED),
        border: Color(0xFFFED7AA),
        foreground: Color(0xFFC2410C),
      ),
      'COMPLETED' => const _StatusSpec(
        label: 'SELESAI',
        background: Color(0xFFEFF6FF),
        border: Color(0xFFBFDBFE),
        foreground: Color(0xFF1D4ED8),
      ),
      'EARLY_LEAVE' => const _StatusSpec(
        label: 'PULANG AWAL',
        background: Color(0xFFFEF2F2),
        border: Color(0xFFFECACA),
        foreground: Color(0xFFB91C1C),
      ),
      _ => _StatusSpec(
        label: status.replaceAll('_', ' '),
        background: const Color(0xFFF3F4F6),
        border: const Color(0xFFE5E7EB),
        foreground: const Color(0xFF374151),
      ),
    };
  }
}
