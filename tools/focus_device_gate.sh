#!/usr/bin/env bash
# C0 only: one API35 emulator, one focused Compose test on phone + tablet windows.
set -euo pipefail
SDK="${ANDROID_HOME:?Official runner Android SDK required}"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"
export ANDROID_AVD_HOME="${RUNNER_TEMP:?}/xuaney-c0-avd"
mkdir -p "$ANDROID_AVD_HOME" ci-out/logs
sdkmanager 'emulator' 'system-images;android-35;google_apis;x86_64'
[[ -e /dev/kvm ]] || { echo "KVM missing"; exit 1; }
sudo chown "$(id -un)" /dev/kvm
avdmanager create avd --force --name xuaney-c0 --package 'system-images;android-35;google_apis;x86_64' --device pixel_2 <<< "no" > ci-out/logs/c0-avd-create.log 2>&1
emulator -avd xuaney-c0 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 > ci-out/logs/c0-emulator.log 2>&1 &
PID=$!
trap 'adb emu kill >/dev/null 2>&1 || true; kill "$PID" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 120); do
  [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
for layout in phone tablet; do
  if [[ "$layout" == phone ]]; then
    adb shell wm size 1080x1920
    adb shell wm density 420
  else
    adb shell wm size 1600x2560
    adb shell wm density 240
  fi
  sleep 2
  bash tools/run_gate.sh "c0-$layout" ./gradlew --no-daemon --console=plain --no-parallel \
    :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=io.github.lihuihnu.xuaneynote.FocusTimerUiTest
done
adb shell getprop ro.build.fingerprint > ci-out/logs/c0-device-fingerprint.log
