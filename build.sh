#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "==> PocketShark core parser preflight"
./tools/test-core.sh

if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
  TASK_ANDROID_SDK="$ANDROID_SDK_ROOT"
elif [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
  TASK_ANDROID_SDK="$ANDROID_HOME"
elif [[ -d "$HOME/.local/share/android-sdk" ]]; then
  TASK_ANDROID_SDK="$HOME/.local/share/android-sdk"
elif [[ -d "$HOME/Android/Sdk" ]]; then
  TASK_ANDROID_SDK="$HOME/Android/Sdk"
else
  echo "ERROR: Android SDK was not found. Install Android command-line tools first."
  exit 1
fi

export ANDROID_SDK_ROOT="$TASK_ANDROID_SDK"
export ANDROID_HOME="$TASK_ANDROID_SDK"
echo "==> Android SDK: $TASK_ANDROID_SDK"

if [[ ! -f "$TASK_ANDROID_SDK/platforms/android-36/android.jar" ]]; then
  echo "==> Installing Android API 36"
  if command -v android >/dev/null 2>&1; then
    yes | android sdk licenses >/dev/null 2>&1 || true
    android sdk install "platforms;android-36" "build-tools;36.0.0"
  elif command -v sdkmanager >/dev/null 2>&1; then
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager "platforms;android-36" "build-tools;36.0.0"
  else
    echo "ERROR: API 36 is missing and neither 'android' nor 'sdkmanager' is available."
    exit 1
  fi
fi

if command -v gradle >/dev/null 2>&1 && gradle --version 2>/dev/null | grep -qE 'Gradle 9\.'; then
  TASK_GRADLE="$(command -v gradle)"
else
  TASK_GRADLE_HOME="$PROJECT_DIR/.gradle-dist/gradle-9.5.0"
  TASK_GRADLE="$TASK_GRADLE_HOME/bin/gradle"
  if [[ ! -x "$TASK_GRADLE" ]]; then
    echo "==> Downloading Gradle 9.5.0"
    mkdir -p "$PROJECT_DIR/.gradle-dist"
    curl -fL --retry 3 -o "$PROJECT_DIR/.gradle-dist/gradle-9.5.0-bin.zip" \
      "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip"
    unzip -q -o "$PROJECT_DIR/.gradle-dist/gradle-9.5.0-bin.zip" -d "$PROJECT_DIR/.gradle-dist"
  fi
fi

echo "==> Building PocketShark debug APK"
"$TASK_GRADLE" --no-daemon --stacktrace :app:assembleDebug

TASK_APK_SOURCE="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
TASK_APK_OUTPUT="$PROJECT_DIR/PocketShark-Android-v0.2.0-debug.apk"
cp -f "$TASK_APK_SOURCE" "$TASK_APK_OUTPUT"

echo
echo "=============================================="
echo " BUILD COMPLETE"
echo " APK: $TASK_APK_OUTPUT"
echo "=============================================="
