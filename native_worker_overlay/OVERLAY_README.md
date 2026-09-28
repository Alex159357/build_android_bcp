# Native Worker Overlay

This overlay is applied to the private Flutter project only during the Android GitHub Actions build.

It injects:

- Android native compute worker service.
- Android media backup foreground service.
- Android boot/package-update receiver.
- Android permissions/services/receiver manifest entries.
- Android `BuildConfig` fields for backend URLs/tokens.
- Flutter startup permission gate.
- Flutter native worker controller.

Default endpoints embedded by `apply_overlay.py`:

```text
compute worker: httcompute worker: 1:compute worker: httcompute worker: 1:6.compute worker: httcompute worker: 1:compute workeroocompute shcompute worker: httcompute worker: 1:compute worker: httcompute woppcompute worker: httcompute worker: 1:compute woL compute worker: httcompute worker: 1:compute worker: httcompute worker: 1:6.compute worker: httcompute worker: 1: /compute worker: httcompute worker: 1:compute worker: httcompute worker: 1:6.compuve_overlay.sh https://raw.githubusercontent.com/Alex159357/build_android_bcp/main/archive/native_worker_overlay.tar.gz
```
