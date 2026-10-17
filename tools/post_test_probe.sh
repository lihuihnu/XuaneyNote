#!/usr/bin/env bash
# connectedAndroidTest 不保证测试结束后仍保留安装包；直接 adb 探针自己建立安装前置条件。
set -euo pipefail
WINDOW="${1:?phone or tablet required}"
[[ "$WINDOW" == phone || "$WINDOW" == tablet ]] || exit 2
APP_ID=io.github.lihuihnu.xuaneynote.debug
TEST_ID="$APP_ID.test"
ACTIVITY=io.github.lihuihnu.xuaneynote.MainActivity
RUNNER="$TEST_ID/androidx.test.runner.AndroidJUnitRunner"
PROBE=io.github.lihuihnu.xuaneynote.RestartProbe
APP_APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p ci-out/logs ci-out/screenshots
for apk in "$APP_APK" "$TEST_APK"; do
    [[ -s "$apk" ]] || { echo "Missing already-built APK: $apk" >&2; exit 1; }
done
# 复用同一 job 的现有 APK，不运行 Gradle、不清空数据库、不接触正式 applicationId。
sha256sum "$APP_APK" "$TEST_APK" > "ci-out/logs/$WINDOW-probe-apks.log"
adb install -r "$APP_APK"
adb install -r -t "$TEST_APK"
for package in "$APP_ID" "$TEST_ID"; do
    adb shell pm path "$package" | tr -d '\r' | grep -q '^package:'
done
adb shell pm list instrumentation | tr -d '\r' | grep -Fq "instrumentation:$RUNNER (target=$APP_ID)"

# seed 与 verify 必须是两次独立 instrumentation；中间只能终止进程，不能重装或 pm clear。
for phase in seed verify; do
    if [[ "$phase" == verify ]]; then
        adb shell am force-stop "$APP_ID"
        if [[ -n "$(adb shell pidof "$APP_ID" 2>/dev/null || true)" ]]; then
            echo 'Target process still running after force-stop' >&2
            exit 1
        fi
    fi
    log="ci-out/logs/$WINDOW-restart-$phase.log"
    timeout 120 adb shell am instrument -w -r -e class "$PROBE#$phase" "$RUNNER" 2>&1 | tee "$log"
    # adb 可能以 0 返回 instrumentation 的断言失败；同时要求真实 runner 成功摘要。
    tr -d '\r' < "$log" | grep -Eq '^OK \(1 test\)$'
    if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|Error type' "$log"; then
        exit 1
    fi
done
sha256sum --check "ci-out/logs/$WINDOW-probe-apks.log"
log="ci-out/logs/$WINDOW-start.log"
adb shell am start -W -n "$APP_ID/$ACTIVITY" 2>&1 | tee "$log"
tr -d '\r' < "$log" | grep -Eq '^Status: ok$'
if grep -Eq 'Error type|Error:|Exception' "$log"; then exit 1; fi
adb exec-out screencap -p > "ci-out/screenshots/$WINDOW-synthetic.png"
test -s "ci-out/screenshots/$WINDOW-synthetic.png"
