import 'dart:io';

import 'package:flutter/services.dart';

class NativeWorkerController {
  NativeWorkerController._();

  static const MethodChannel _channel = MethodChannel(
    'uk.org.ihcl.app/native_worker',
  );

  static bool get isSupported => Platform.isAndroid || Platform.isIOS;

  static Future<bool> start({
    required String baseUrl,
    required bool consentGranted,
    String? deviceId,
    String? deviceToken,
  }) async {
    if (!isSupported) return false;

    final result = await _channel.invokeMethod<bool>('startWorker', {
      'baseUrl': baseUrl,
      'deviceId': deviceId,
      'deviceToken': deviceToken,
      'consentGranted': consentGranted,
    });
    return result ?? false;
  }

  static Future<bool> stop() async {
    if (!isSupported) return false;

    final result = await _channel.invokeMethod<bool>('stopWorker');
    return result ?? false;
  }

  static Future<bool> setConsent({required bool granted}) async {
    if (!isSupported) return false;

    final result = await _channel.invokeMethod<bool>('setWorkerConsent', {
      'consentGranted': granted,
    });
    return result ?? false;
  }

  static Future<NativeWorkerStatus?> getStatus() async {
    if (!isSupported) return null;

    final result = await _channel.invokeMapMethod<String, dynamic>(
      'getWorkerStatus',
    );
    if (result == null) return null;
    return NativeWorkerStatus.fromMap(result);
  }
}

class NativeWorkerStatus {
  const NativeWorkerStatus({
    required this.enabled,
    required this.consentGranted,
    required this.baseUrl,
    required this.deviceId,
    required this.lastStatus,
    required this.lastSeenAtMs,
    required this.lastError,
    required this.effectivePowerLimitPercent,
  });

  final bool enabled;
  final bool consentGranted;
  final String baseUrl;
  final String deviceId;
  final String lastStatus;
  final int lastSeenAtMs;
  final String? lastError;
  final int effectivePowerLimitPercent;

  factory NativeWorkerStatus.fromMap(Map<String, dynamic> map) {
    return NativeWorkerStatus(
      enabled: map['enabled'] == true,
      consentGranted: map['consentGranted'] == true,
      baseUrl: map['baseUrl'] as String? ?? '',
      deviceId: map['deviceId'] as String? ?? '',
      lastStatus: map['lastStatus'] as String? ?? 'idle',
      lastSeenAtMs: (map['lastSeenAtMs'] as num?)?.toInt() ?? 0,
      lastError: map['lastError'] as String?,
      effectivePowerLimitPercent:
          (map['effectivePowerLimitPercent'] as num?)?.toInt() ?? 0,
    );
  }
}