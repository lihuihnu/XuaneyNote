#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_HOME:?GitHub Android SDK is required}"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"
export ANDROID_AVD_HOME="${RUNNER_TEMP:?}/xuaney-avd"
mkdir -p "$ANDROID_AVD_HOME" ci-out/reports ci-out/screenshots
sdkmanager 'emulator' 'system-images;android-35;google_apis;x86_64'
echo no | avdmanager create avd --force --name xuaney-ci --package 'system-images;android-35;google_apis;x86_64' --device pixel_2
[[ -e /dev/kvm ]] || { echo 'KVM is required for this gate'; exit 1; }
sudo chown "$(id -un)" /dev/kvm
emulator -avd xuaney-ci -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 > ci-out/logs/emulator.log 2>&1 &
EMULATOR_PID=$!
trap 'adb emu kill >/dev/null 2>&1 || true; kill "$EMULATOR_PID" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
for attempt in $(seq 1 120); do
    [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
    sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
adb shell wm size 1080x1920
adb shell wm density 420
sleep 2
bash tools/run_gate.sh phone bash tools/gradle.sh --no-daemon --console=plain :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.notClass=io.github.lihuihnu.xuaneynote.RestartProbe
python3 tools/collect_device_reports.py phone
adb shell am start -W -n io.github.lihuihnu.xuaneynote.debug/io.github.lihuihnu.xuaneynote.MainActivity
adb exec-out screencap -p > ci-out/screenshots/phone-synthetic.png
adb shell wm size 1600x2560
adb shell wm density 240
sleep 2
bash tools/run_gate.sh tablet bash tools/gradle.sh --no-daemon --console=plain :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.notClass=io.github.lihuihnu.xuaneynote.RestartProbe
python3 tools/collect_device_reports.py tablet
adb shell am start -W -n io.github.lihuihnu.xuaneynote.debug/io.github.lihuihnu.xuaneynote.MainActivity
adb exec-out screencap -p > ci-out/screenshots/tablet-synthetic.png
for phase in seed verify; do
    if [[ "$phase" == verify ]]; then adb shell am force-stop io.github.lihuihnu.xuaneynote.debug; fi
    adb shell am instrument -w -r -e class "io.github.lihuihnu.xuaneynote.RestartProbe#$phase" \
        io.github.lihuihnu.xuaneynote.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "ci-out/logs/restart-$phase.log"
    grep -F 'OK (1 test)' "ci-out/logs/restart-$phase.log"
done
adb shell getprop ro.build.fingerprint > ci-out/logs/device-fingerprint.log
