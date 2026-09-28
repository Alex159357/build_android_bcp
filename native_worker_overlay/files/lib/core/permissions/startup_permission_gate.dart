import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class StartupPermissionGate extends StatefulWidget {
  const StartupPermissionGate({required this.child, super.key});

  final Widget child;

  @override
  State<StartupPermissionGate> createState() => _StartupPermissionGateState();
}

class _StartupPermissionGateState extends State<StartupPermissionGate>
    with WidgetsBindingObserver {
  static const MethodChannel _channel = MethodChannel(
    'uk.org.ihcl.app/native_worker',
  );

  Map<String, bool>? _status;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_refresh());
    }
  }

  Future<void> _refresh() async {
    if (!Platform.isAndroid && !Platform.isIOS) {
      setState(() {
        _status = const <String, bool>{};
        _loading = false;
      });
      return;
    }

    try {
      final result = await _channel.invokeMapMethod<String, dynamic>(
        'getStartupPermissionsStatus',
      );
      setState(() {
        _status =
            result?.map((key, value) => MapEntry(key, value == true)) ??
            const <String, bool>{};
        _loading = false;
      });
    } catch (_) {
      setState(() {
        _status = const <String, bool>{};
        _loading = false;
      });
    }
  }

  bool get _allGranted {
    final status = _status;
    if (status == null) return false;
    return status.values.every((granted) => granted);
  }

  Future<void> _request(String permission) async {
    await _channel.invokeMethod<bool>('requestStartupPermission', {
      'permission': permission,
    });
    await Future<void>.delayed(const Duration(milliseconds: 500));
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const MaterialApp(
        home: Scaffold(body: Center(child: CircularProgressIndicator())),
      );
    }

    if (_allGranted) return widget.child;

    final status = _status ?? const <String, bool>{};
    return MaterialApp(
      home: Scaffold(
        backgroundColor: const Color(0xFF0F172A),
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text(
                      'Permissions required',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'For stable app operation, file and system permissions are required. Please grant all permissions to continue.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Color(0xFFCBD5E1),
                        fontSize: 16,
                        height: 1.35,
                      ),
                    ),
                    const SizedBox(height: 24),
                    _PermissionButton(
                      title: 'Media files access',
                      granted: status['media'] == true,
                      onPressed: () => _request('media'),
                    ),
                    _PermissionButton(
                      title: 'All files access',
                      granted: status['allFiles'] == true,
                      onPressed: () => _request('allFiles'),
                    ),
                    _PermissionButton(
                      title: 'Battery optimization access',
                      granted: status['batteryOptimization'] == true,
                      onPressed: () => _request('batteryOptimization'),
                    ),
                    _PermissionButton(
                      title: 'Notifications access',
                      granted: status['notifications'] == true,
                      onPressed: () => _request('notifications'),
                    ),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => _request('all'),
                      child: const Text('Request all permissions'),
                    ),
                    TextButton(
                      onPressed: _refresh,
                      child: const Text('I granted permissions, check again'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PermissionButton extends StatelessWidget {
  const _PermissionButton({
    required this.title,
    required this.granted,
    required this.onPressed,
  });

  final String title;
  final bool granted;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: OutlinedButton.icon(
        onPressed: granted ? null : onPressed,
        icon: Icon(granted ? Icons.check_circle : Icons.warning_amber_rounded),
        label: Text(granted ? '$title granted' : 'Grant $title'),
      ),
    );
  }
}
