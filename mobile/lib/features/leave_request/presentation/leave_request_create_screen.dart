import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/utils/date_formatter.dart';
import '../data/leave_request_repository.dart';
import '../domain/leave_request.dart';

class LeaveRequestCreateScreen extends ConsumerStatefulWidget {
  const LeaveRequestCreateScreen({super.key});

  @override
  ConsumerState<LeaveRequestCreateScreen> createState() =>
      _LeaveRequestCreateScreenState();
}

class _LeaveRequestCreateScreenState
    extends ConsumerState<LeaveRequestCreateScreen> {
  final _formKey = GlobalKey<FormState>();
  final _reasonController = TextEditingController();

  LeaveRequestType _type = LeaveRequestType.permission;
  DateTime _startDate = DateTime.now();
  DateTime _endDate = DateTime.now();
  TimeOfDay? _clockInTime;
  TimeOfDay? _clockOutTime;
  bool _isSubmitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isCorrection = _type == LeaveRequestType.attendanceCorrection;

    return Scaffold(
      appBar: AppBar(title: const Text('Buat pengajuan')),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(
                'Jenis pengajuan',
                style: Theme.of(context).textTheme.labelLarge,
              ),
              const SizedBox(height: 6),
              DropdownButtonFormField<LeaveRequestType>(
                initialValue: _type,
                items: LeaveRequestType.values
                    .map(
                      (type) => DropdownMenuItem(
                        value: type,
                        child: Text(type.label),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value == null) return;
                  setState(() => _type = value);
                },
                decoration: const InputDecoration(border: OutlineInputBorder()),
              ),
              const SizedBox(height: 16),
              _DateField(
                label: 'Tanggal mulai',
                value: _startDate,
                onChanged: (value) => setState(() {
                  _startDate = value;
                  if (_endDate.isBefore(value)) _endDate = value;
                }),
              ),
              const SizedBox(height: 12),
              _DateField(
                label: 'Tanggal selesai',
                value: _endDate,
                onChanged: (value) => setState(() => _endDate = value),
              ),
              if (isCorrection) ...[
                const SizedBox(height: 16),
                _TimeField(
                  label: 'Clock-in (opsional)',
                  value: _clockInTime,
                  onChanged: (value) => setState(() => _clockInTime = value),
                ),
                const SizedBox(height: 12),
                _TimeField(
                  label: 'Clock-out (opsional)',
                  value: _clockOutTime,
                  onChanged: (value) => setState(() => _clockOutTime = value),
                ),
              ],
              const SizedBox(height: 16),
              Text('Alasan', style: Theme.of(context).textTheme.labelLarge),
              const SizedBox(height: 6),
              TextFormField(
                controller: _reasonController,
                minLines: 3,
                maxLines: 5,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  hintText: 'Tulis alasan pengajuan',
                ),
                validator: (value) {
                  if (isCorrection) return null;
                  if (value == null || value.trim().isEmpty) {
                    return 'Alasan wajib diisi.';
                  }
                  return null;
                },
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 12),
                Text(
                  _errorMessage!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ],
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: _isSubmitting ? null : _submit,
                icon: const Icon(Icons.send),
                label: Text(_isSubmitting ? 'Mengirim...' : 'Kirim pengajuan'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_endDate.isBefore(_startDate)) {
      setState(
        () => _errorMessage =
            'Tanggal selesai tidak boleh sebelum tanggal mulai.',
      );
      return;
    }
    if (_type == LeaveRequestType.attendanceCorrection &&
        _clockInTime == null &&
        _clockOutTime == null) {
      setState(
        () => _errorMessage =
            'Isi minimal salah satu jam clock-in atau clock-out.',
      );
      return;
    }

    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });

    try {
      final payload = CreateLeaveRequestPayload(
        requestType: _type,
        startDate: DateFormatter.apiDate(_startDate),
        endDate: DateFormatter.apiDate(_endDate),
        reason: _reasonController.text,
        requestedClockInTime: _clockInTime != null
            ? DateTime(
                _startDate.year,
                _startDate.month,
                _startDate.day,
                _clockInTime!.hour,
                _clockInTime!.minute,
              )
            : null,
        requestedClockOutTime: _clockOutTime != null
            ? DateTime(
                _startDate.year,
                _startDate.month,
                _startDate.day,
                _clockOutTime!.hour,
                _clockOutTime!.minute,
              )
            : null,
      );
      await ref.read(leaveRequestRepositoryProvider).create(payload);
      ref.invalidate(leaveRequestListProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Pengajuan terkirim.')));
      context.pop();
    } on ApiException catch (error) {
      setState(() => _errorMessage = error.friendlyMessage);
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final DateTime value;
  final ValueChanged<DateTime> onChanged;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () async {
        final picked = await showDatePicker(
          context: context,
          initialDate: value,
          firstDate: DateTime.now().subtract(const Duration(days: 365)),
          lastDate: DateTime.now().add(const Duration(days: 365)),
        );
        if (picked != null) onChanged(picked);
      },
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        child: Text(DateFormatter.apiDate(value)),
      ),
    );
  }
}

class _TimeField extends StatelessWidget {
  const _TimeField({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final TimeOfDay? value;
  final ValueChanged<TimeOfDay?> onChanged;

  @override
  Widget build(BuildContext context) {
    final label0 = value == null ? 'Pilih waktu' : value!.format(context);
    return Row(
      children: [
        Expanded(
          child: InkWell(
            onTap: () async {
              final picked = await showTimePicker(
                context: context,
                initialTime: value ?? const TimeOfDay(hour: 8, minute: 0),
              );
              if (picked != null) onChanged(picked);
            },
            child: InputDecorator(
              decoration: InputDecoration(
                labelText: label,
                border: const OutlineInputBorder(),
              ),
              child: Text(label0),
            ),
          ),
        ),
        if (value != null)
          IconButton(
            tooltip: 'Hapus',
            onPressed: () => onChanged(null),
            icon: const Icon(Icons.close),
          ),
      ],
    );
  }
}
