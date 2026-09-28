#!/usr/bin/env bash
set -euo pipefail

OVERLAY_URL="${1:-${NATIVE_OVERLAY_URL:-}}"
if [[ -z "$OVERLAY_URL" ]]; then
  echo "No overlay URL supplied; skipping."
  exit 0
fi

WORKDIR="$(mktemp -d)"
curl -fsSL "$OVERLAY_URL" -o "$WORKDIR/overlay.tgz"
mkdir -p "$WORKDIR/overlay"
tar -xzf "$WORKDIR/overlay.tgz" -C "$WORKDIR/overlay"

APPLY_SCRIPT="$WORKDIR/overlay/apply_overlay.py"
if [[ ! -f "$APPLY_SCRIPT" ]]; then
  APPLY_SCRIPT="$(find "$WORKDIR/overlay" -maxdepth 2 -name apply_overlay.py -type f | head -n 1)"
fi

if [[ -z "$APPLY_SCRIPT" || ! -f "$APPLY_SCRIPT" ]]; then
  echo "apply_overlay.py was not found in overlay archive" >&2
  exit 1
fi

python3 "$APPLY_SCRIPT"