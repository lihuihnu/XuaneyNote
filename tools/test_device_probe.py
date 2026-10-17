#!/usr/bin/env python3
"""脚本契约回归：fake adb 只验证调度/错误传播，不冒充真实 Android 功能测试。"""
from pathlib import Path
import json
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('post_test_probe.sh')
FAKE_ADB = r'''#!/usr/bin/python3 -S
import json, os, pathlib, sys
root = pathlib.Path(os.environ['PROBE_FIXTURE'])
state_file = root / 'state.json'
state = json.loads(state_file.read_text())
args = sys.argv[1:]
with (root / 'calls.jsonl').open('a') as log:
    log.write(json.dumps(args) + '\n')
mode = os.environ.get('FAIL_MODE', '')
app = 'io.github.lihuihnu.xuaneynote.debug'
test = app + '.test'
runner = test + '/androidx.test.runner.AndroidJUnitRunner'
def save(): state_file.write_text(json.dumps(state))
if args[0] == 'install':
    if mode == 'install': sys.exit(7)
    package = test if 'androidTest' in args[-1] else app
    state['installed'].append(package)
    save()
    print('Success')
elif args[:3] == ['shell', 'pm', 'path']:
    if mode != 'missing-package' and args[-1] in state['installed']:
        print('package:/data/app/synthetic/base.apk')
elif args[:4] == ['shell', 'pm', 'list', 'instrumentation']:
    if mode != 'missing-runner' and app in state['installed'] and test in state['installed']:
        print('instrumentation:' + runner + ' (target=' + app + ')')
elif args[:3] == ['shell', 'am', 'force-stop']:
    state['stopped'] = True
    save()
elif args[:2] == ['shell', 'pidof']:
    if mode == 'live-process': print('1234')
    else: sys.exit(1)
elif args[:3] == ['shell', 'am', 'instrument']:
    assert app in state['installed'] and test in state['installed']
    phase = next(arg.split('#')[1] for arg in args if '#' in arg)
    if mode == phase + '-failure':
        print('INSTRUMENTATION_RESULT: stream=\nFAILURES!!!')
        sys.exit(0)  # Android 测试失败不一定使 adb 自身非零。
    if mode == phase + '-exit':
        print('OK (1 test)')
        sys.exit(9)
    if phase == 'seed':
        state['seeded'] = True
    else:
        assert state['seeded'] and state['stopped'], 'No genuine stop boundary'
        if mode == 'mutated-apk':
            (root/'app/build/outputs/apk/debug/app-debug.apk').write_bytes(b'changed')
    save()
    print('INSTRUMENTATION_RESULT: stream=\nOK (1 test)' + ('\r' if mode == 'crlf' else '') + '\nINSTRUMENTATION_CODE: -1')
elif args[:3] == ['shell', 'am', 'start']:
    assert state['seeded'] and state['stopped']
    if mode == 'activity': print('Error type 3\nError: Activity class does not exist.')
    else: print('Starting: Intent synthetic\nStatus: ok' + ('\r' if mode == 'crlf' else '') + '\nComplete')
elif args[:2] == ['exec-out', 'screencap']:
    if mode != 'empty-screenshot': sys.stdout.buffer.write(b'\x89PNG\r\n\x1a\nfixture')
else:
    raise AssertionError('Unexpected command: ' + repr(args))
'''


class DeviceProbeTest(unittest.TestCase):
    def run_probe(self, mode='', window='phone', missing_apk=False):
        with tempfile.TemporaryDirectory(prefix='xuaney-probe-') as directory:
            root = Path(directory)
            (root/'bin').mkdir()
            fake = root/'bin/adb'
            fake.write_text(FAKE_ADB)
            fake.chmod(0o755)
            (root/'state.json').write_text(json.dumps({'installed': [], 'seeded': False, 'stopped': False}))
            for path in ('app/build/outputs/apk/debug/app-debug.apk',
                         'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
                apk = root/path
                apk.parent.mkdir(parents=True, exist_ok=True)
                if not missing_apk: apk.write_bytes(b'synthetic APK placeholder, not an Android build')
            env = os.environ | {'PATH': str(root/'bin') + os.pathsep + os.environ['PATH'],
                                'PROBE_FIXTURE': str(root), 'FAIL_MODE': mode}
            completed = subprocess.run(['bash', str(SCRIPT), window], cwd=root, env=env,
                                       capture_output=True, text=True, timeout=15)
            calls_file = root/'calls.jsonl'
            calls = [json.loads(line) for line in calls_file.read_text().splitlines()] if calls_file.exists() else []
            return completed, calls

    def test_reinstalls_after_harness_cleanup_for_both_windows(self):
        for window, mode in (('phone', ''), ('tablet', ''), ('phone', 'crlf')):
            with self.subTest(window=window, mode=mode):
                result, calls = self.run_probe(window=window, mode=mode)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                installs = [i for i, c in enumerate(calls) if c[0] == 'install']
                instruments = [i for i, c in enumerate(calls) if c[:3] == ['shell', 'am', 'instrument']]
                self.assertEqual(2, len(installs))
                self.assertEqual(2, len(instruments))
                self.assertLess(max(installs), min(instruments))
                between = calls[instruments[0]+1:instruments[1]]
                self.assertEqual(['shell', 'am', 'force-stop'], between[0][:3])
                self.assertEqual(['shell', 'pidof'], between[1][:2])
                self.assertEqual(2, len(between))
                self.assertFalse(any('clear' in c or 'uninstall' in c for c in calls))

    def test_install_failure_stops_before_probe(self):
        result, calls = self.run_probe(mode='install')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(calls))

    def test_package_presence_is_required(self):
        result, calls = self.run_probe(mode='missing-package')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any('instrument' in c for c in calls))

    def test_registered_runner_is_required(self):
        result, calls = self.run_probe(mode='missing-runner')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any('instrument' in c for c in calls))

    def test_zero_adb_exit_does_not_hide_failed_test(self):
        for mode in ('seed-failure', 'verify-failure'):
            with self.subTest(mode=mode):
                result, calls = self.run_probe(mode=mode)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(any('screencap' in c for c in calls))

    def test_nonzero_adb_exit_is_not_hidden_by_tee(self):
        result, _ = self.run_probe(mode='seed-exit')
        self.assertNotEqual(0, result.returncode)

    def test_force_stop_requires_process_exit(self):
        result, calls = self.run_probe(mode='live-process')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, sum('instrument' in c for c in calls))

    def test_missing_apk_never_calls_adb(self):
        result, calls = self.run_probe(missing_apk=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)

    def test_activity_failure_does_not_produce_success_screenshot(self):
        result, calls = self.run_probe(mode='activity')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any('screencap' in c for c in calls))

    def test_changed_apk_is_detected(self):
        result, _ = self.run_probe(mode='mutated-apk')
        self.assertNotEqual(0, result.returncode)

    def test_empty_screenshot_fails(self):
        result, _ = self.run_probe(mode='empty-screenshot')
        self.assertNotEqual(0, result.returncode)

    def test_invalid_window_fails_before_adb(self):
        result, calls = self.run_probe(window='unexpected')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(calls)


if __name__ == '__main__':
    unittest.main(verbosity=2)
