#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TASK_APK="$PROJECT_DIR/PocketShark-Android-v0.2.0-debug.apk"

if [[ ! -f "$TASK_APK" ]]; then
  echo "APK not found. Run ./build.sh first."
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed. On Kali run: sudo apt install adb"
  exit 1
fi

TASK_DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2 == "device" {count++} END {print count+0}')"
if [[ "$TASK_DEVICE_COUNT" -eq 0 ]]; then
  echo "No authorised Android device found. Unlock the phone, enable USB debugging, and accept the prompt."
  adb devices -l
  exit 1
fi

adb install -r "$TASK_APK"
echo "PocketShark is installed. Open it from the phone's app drawer."
