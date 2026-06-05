import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/api/api_exception.dart';
import '../../../shared/widgets/app_card.dart';
import '../data/face_profile_repository.dart';
import '../domain/face_profile.dart';

class FaceEnrollmentScreen extends ConsumerStatefulWidget {
  const FaceEnrollmentScreen({super.key});

  @override
  ConsumerState<FaceEnrollmentScreen> createState() =>
      _FaceEnrollmentScreenState();
}

class _FaceEnrollmentScreenState extends ConsumerState<FaceEnrollmentScreen> {
  bool _consent = false;
  bool _isSubmitting = false;
  String? _message;
  Uint8List? _previewBytes;
  String? _pendingBase64;

  Future<void> _pickImage(ImageSource source) async {
    final picker = ImagePicker();
    try {
      final picked = await picker.pickImage(
        source: source,
        imageQuality: 75,
        maxWidth: 1024,
      );
      if (picked == null) return;
      final bytes = await picked.readAsBytes();
      setState(() {
        _previewBytes = bytes;
        _pendingBase64 = base64Encode(bytes);
        _message = null;
      });
    } catch (error) {
      setState(() {
        _message = 'Tidak dapat memilih foto: $error';
      });
    }
  }

  Future<void> _submit() async {
    if (!_consent) {
      setState(() => _message = 'Centang persetujuan terlebih dahulu.');
      return;
    }
    final payload = _pendingBase64;
    if (payload == null) {
      setState(() => _message = 'Pilih foto terlebih dahulu.');
      return;
    }
    setState(() {
      _isSubmitting = true;
      _message = null;
    });
    try {
      await ref
          .read(faceProfileRepositoryProvider)
          .enrollMine(imageBase64: payload, consentGiven: true);
      ref.invalidate(myFaceProfileProvider);
      if (!mounted) return;
      setState(() {
        _message = 'Foto enrollment tersimpan.';
        _previewBytes = null;
        _pendingBase64 = null;
      });
    } on ApiException catch (error) {
      setState(() => _message = error.friendlyMessage);
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final profileAsync = ref.watch(myFaceProfileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Daftar Wajah')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Status Enrollment',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  profileAsync.when(
                    data: (profile) => _StatusBlock(profile: profile),
                    loading: () => const Text('Memuat status...'),
                    error: (error, _) => Text(
                      error is ApiException
                          ? error.friendlyMessage
                          : 'Tidak dapat memuat status.',
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Status ACTIVE berarti foto enrollment dan persetujuan tercatat. '
                    'Versi ini belum melakukan pencocokan wajah, deteksi keaslian, '
                    'maupun anti-spoofing. Absensi tidak diblokir oleh status enrollment.',
                    style: TextStyle(color: Colors.black54, height: 1.4),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Foto Enrollment',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 12),
                  if (_previewBytes != null)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Image.memory(
                        _previewBytes!,
                        height: 220,
                        fit: BoxFit.cover,
                      ),
                    )
                  else
                    Container(
                      height: 160,
                      decoration: BoxDecoration(
                        color: Colors.black12,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Center(
                        child: Text(
                          'Belum ada foto dipilih.',
                          style: TextStyle(color: Colors.black54),
                        ),
                      ),
                    ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _isSubmitting
                              ? null
                              : () => _pickImage(ImageSource.camera),
                          icon: const Icon(Icons.photo_camera_outlined),
                          label: const Text('Kamera'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _isSubmitting
                              ? null
                              : () => _pickImage(ImageSource.gallery),
                          icon: const Icon(Icons.photo_library_outlined),
                          label: const Text('Galeri'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  CheckboxListTile(
                    value: _consent,
                    onChanged: _isSubmitting
                        ? null
                        : (value) => setState(() => _consent = value ?? false),
                    title: const Text(
                      'Saya menyetujui penggunaan foto wajah saya untuk keperluan absensi',
                    ),
                    subtitle: const Text(
                      'Foto disimpan di server Hadivo. Anda dapat meminta admin untuk reset enrollment kapan saja.',
                    ),
                    controlAffinity: ListTileControlAffinity.leading,
                    contentPadding: EdgeInsets.zero,
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _isSubmitting ? null : _submit,
                      icon: const Icon(Icons.check),
                      label: Text(
                        _isSubmitting ? 'Mengirim...' : 'Kirim Enrollment',
                      ),
                    ),
                  ),
                  if (_message != null) ...[
                    const SizedBox(height: 10),
                    Text(
                      _message!,
                      style: const TextStyle(color: Colors.black87),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusBlock extends StatelessWidget {
  const _StatusBlock({required this.profile});

  final FaceProfile? profile;

  @override
  Widget build(BuildContext context) {
    final p = profile;
    if (p == null) {
      return const Text('Belum ada profil wajah.');
    }
    final color = switch (p.enrollmentStatus) {
      FaceEnrollmentStatus.active => Colors.green.shade700,
      FaceEnrollmentStatus.reset => Colors.amber.shade800,
      FaceEnrollmentStatus.pending => Colors.black54,
    };
    final label = switch (p.enrollmentStatus) {
      FaceEnrollmentStatus.active => 'Terdaftar',
      FaceEnrollmentStatus.reset => 'Direset',
      FaceEnrollmentStatus.pending => 'Belum enroll',
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                label,
                style: TextStyle(color: color, fontWeight: FontWeight.w700),
              ),
            ),
            const SizedBox(width: 8),
            if (p.imageStored)
              const Text(
                'Foto tersimpan',
                style: TextStyle(color: Colors.black54),
              ),
          ],
        ),
        if (p.message.isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(p.message, style: const TextStyle(color: Colors.black54)),
        ],
      ],
    );
  }
}
