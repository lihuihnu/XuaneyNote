#!/usr/bin/env python3
"""Fast source/permission/workflow checks; standard library only, no SDK downloads."""
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
for name, version in versions['versions'].items():
    assert '+' not in version and 'SNAPSHOT' not in version, f'Unpinned dependency: {name}'
workflow = (root / '.github/workflows/ci.yml').read_text()
# job 级 env 在分配 Runner 前求值，不能使用 runner/steps/job/env 上下文。
job_header = workflow.split('    steps:', 1)[0]
assert not re.search(r'\$\{\{\s*(runner|steps|job|env)\.', job_header), 'Runner context is only valid inside execution steps'
assert 'runs-on: ubuntu-24.04' in workflow
assert 'contents: read' in workflow and 'contents: write' not in workflow
for forbidden in ('pull_request_target:', 'upload-artifact@', 'download-artifact@', 'self-hosted', 'schedule:', 'push:'):
    assert forbidden not in workflow, forbidden
for action in re.findall(r'uses:\s*([^\s#]+)', workflow):
    assert re.fullmatch(r'[\w./-]+@[0-9a-f]{40}', action), f'Unpinned action: {action}'
for file in root.rglob('*'):
    if not file.is_file() or any(part in {'.git', 'build', '.gradle', '.bootstrap', 'ci-out', '__pycache__'} for part in file.parts):
        continue
    assert file.suffix not in {'.apk', '.aab', '.jks', '.keystore'}, f'Forbidden tracked resource: {file}'
    if file.suffix == '.xml':
        ET.parse(file)
    if file.suffix in {'.kt', '.kts', '.xml', '.py', '.sh', '.toml', '.md', '.yml'}:
        file.read_text(encoding='utf-8')
        assert file.stat().st_size < 2 * 1024 * 1024
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'PR-A must not request permissions'
android = '{http://schemas.android.com/apk/res/android}'
assert manifest.find('application').get(android+'allowBackup') == 'false'
assert 'fallbackToDestructiveMigration' not in (root / 'core/data/src/main/kotlin/io/github/lihuihnu/xuaneynote/data/NoteDatabase.kt').read_text()
print('PASS source XML / pinned versions / read-only official CI / no artifact / no runtime permissions / no destructive migration')
if '--merged' in sys.argv:
    manifests = list((root / 'app/build/intermediates').glob('**/AndroidManifest.xml'))
    assert any('release' in str(p).lower() for p in manifests), 'No release merged manifest'
    for path in manifests:
        parsed = ET.parse(path).getroot()
        perms = {item.get(android+'name') for item in parsed.findall('uses-permission')}
        assert not ({'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'} & perms), path
    print(f'PASS merged Manifest network-permission check ({len(manifests)} manifests)')
