#!/usr/bin/env bash
# Separate API26 emulator: only three new B5 tests; never rerun all legacy suites.
set -euo pipefail
SDK="$ANDROID_HOME"
[[ -n "$SDK" && -n "$RUNNER_TEMP" ]] || { echo "Android SDK or Runner temp missing"; exit 1; }
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"
export ANDROID_AVD_HOME="$RUNNER_TEMP/xuaney-avd"
mkdir -p "$ANDROID_AVD_HOME" ci-out/logs ci-out/reports
sdkmanager 'emulator' 'system-images;android-26;google_apis;x86_64'
echo no | avdmanager create avd --force --name xuaney-b5-api26 \
  --package 'system-images;android-26;google_apis;x86_64' --device pixel_2
[[ -e /dev/kvm ]] || { echo "KVM required"; exit 1; }
sudo chown "$(id -un)" /dev/kvm
emulator -avd xuaney-b5-api26 -no-window -no-audio -no-boot-anim \
  -no-snapshot -gpu swiftshader_indirect -memory 2048 > ci-out/logs/emulator-api26.log 2>&1 &
PID=$!
trap 'adb emu kill >/dev/null 2>&1 || true; kill "$PID" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 120); do
    [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
    sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 26 ]]
bash tools/run_gate.sh b5-api26-core ./gradlew --no-daemon --console=plain --no-parallel \
  :core:data:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.lihuihnu.xuaneynote.data.B5Zip4jCompatibilityTest
python3 tools/collect_device_reports.py api26
echo "PASS B5.1: actual Android API26 device ran the 3 isolated AES format tests"
