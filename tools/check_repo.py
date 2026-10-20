#!/usr/bin/env python3
"""Fast source/permission/workflow checks; standard library only, no SDK downloads."""
from pathlib import Path
import hashlib
import json
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
wrapper = root / 'gradle/wrapper/gradle-wrapper.jar'
assert wrapper.is_file(), 'Official Gradle Wrapper missing'
assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f', 'Gradle Wrapper JAR digest mismatch'
properties = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text(encoding='utf-8')
assert 'gradle-8.13-bin.zip' in properties
assert 'distributionSha256Sum=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78' in properties
schema = root / 'core/data/schemas/io.github.lihuihnu.xuaneynote.data.NoteDatabase/1.json'
assert schema.is_file(), 'Frozen Room v1 schema missing'
schema_json = json.loads(schema.read_text(encoding='utf-8'))
assert schema_json['database']['version'] == 1 and len(schema_json['database']['identityHash']) == 32
assert hashlib.sha256(schema.read_bytes()).hexdigest() == '6c8b9e2ea45c170c1b81df901e748377fa109e7a0a74f96c1b17e2e144ae5e99', 'Frozen Room schema changed'

schema2 = root / 'core/data/schemas/io.github.lihuihnu.xuaneynote.data.NoteDatabase/2.json'
assert schema2.is_file(), 'Real KSP-generated Room v2 schema missing'
schema2_json = json.loads(schema2.read_text(encoding='utf-8'))
assert schema2_json['database']['version'] == 2
assert [f['columnName'] for f in schema2_json['database']['entities'][0]['fields']] == [
    'id', 'title', 'body', 'createdAt', 'updatedAt', 'revision', 'bodyFormat', 'bodyVersion', 'deletedAt'
], 'Unexpected v1 to v2 column set'
assert hashlib.sha256(schema2.read_bytes()).hexdigest() == '9ba603f08bc9e08eb5eee97cb6600c9aaa2fd90540cf16722184d5a9389ed445', 'Frozen real Room v2 schema changed'


schema3 = root / 'core/data/schemas/io.github.lihuihnu.xuaneynote.data.NoteDatabase/3.json'
assert schema3.is_file(), 'Actual KSP Room v3 schema missing'
db3 = json.loads(schema3.read_text(encoding='utf-8'))['database']
assert db3['version'] == 3
assert [table['tableName'] for table in db3['entities']] == [
    'notes', 'attachments', 'note_attachments'
], 'Unexpected Room v3 entities'
assert [f['columnName'] for f in db3['entities'][0]['fields']] == [
    'id', 'title', 'body', 'createdAt', 'updatedAt', 'revision',
    'bodyFormat', 'bodyVersion', 'deletedAt'
], 'Room v3 changed historical note columns'
assert sorted(foreign_key['onDelete'] for foreign_key
    in db3['entities'][2]['foreignKeys']) == ['CASCADE', 'RESTRICT']
assert {index['name'] for index in db3['entities'][1]['indices']} == {
    'index_attachments_relativePath', 'index_attachments_sha256'
}
assert hashlib.sha256(schema3.read_bytes()).hexdigest() == (
    'dc57dc1df55e8d0ae29dfebaec166c868a2076b8c869e394578638c7bc7602ed'
), 'Frozen real Room v3 schema changed'

# 仅 Debug 构建可以测试图片编辑，Release 必须始终由 BuildConfig.DEBUG 关闸。
image_ui = (root / 'app/src/main/kotlin/io/github/lihuihnu/xuaneynote/ui/NoteImages.kt').read_text()
assert 'val USER_ENTRY_ENABLED = BuildConfig.DEBUG' in image_ui, 'Image edit gate must remain Debug-only'
assert 'buildConfig = true' in (root / 'app/build.gradle.kts').read_text(), 'BuildConfig generation required'
viewmodel = (root / 'app/src/main/kotlin/io/github/lihuihnu/xuaneynote/NotesViewModel.kt').read_text()
assert 'data.attachments.permanentlyDeleteTrashed(id)' in viewmodel, 'File-safe trash deletion required'
assert 'fun addImage(noteId: String, uri: Uri)' in viewmodel, 'Bind Picker return to original note'
# B5.2: reviewed Zip4j 2.11.6 may now be used only by an internal backend.
b5data = (root / 'core/data/build.gradle.kts').read_text()
assert versions['versions']['zip4j'] == '2.11.6'
assert re.search(r'(?m)^\s*implementation\(libs\.zip4j\)', b5data), 'B5 backend library required'
assert 'androidTestImplementation(libs.zip4j)' not in b5data, 'No redundant test dependency'
app_gradle = (root / 'app/build.gradle.kts').read_text()
assert not re.search(r'(?m)^\s*implementation\(libs\.zip4j\)', app_gradle), 'No separate app dependency'
assert 'sourceSets.getByName("androidTest").java.srcDir("$rootDir/tools/b5")' in b5data
b5_service = (root / 'core/data/src/main/kotlin/io/github/lihuihnu/xuaneynote/data/EncryptedBundleTransferService.kt').read_text()
assert all(text in b5_service for text in (
    'AesKeyStrength.KEY_STRENGTH_256',
    'header.encryptionMethod == EncryptionMethod.AES',
    'entries.size == 1',
    'original.inspect(FileInputStream(inner))',
    'original.restore(verified)',
    'MAX_OUTER',
    'cleanupOrphans()'
)), 'B5 fail-closed backend contracts missing'
# B5.3: SAF only reports success after close + reopening the same URI and
# fully re-validating AES+inner B4; password must never enter saved state.
vm = (root / 'app/src/main/kotlin/io/github/lihuihnu/xuaneynote/NotesViewModel.kt').read_text()
assert 'fun prepareEncryptedBundleExport(password: CharArray' in vm
assert 'fun exportEncryptedBundle(uri: Uri?)' in vm
assert 'fun inspectEncryptedBundle(uri: Uri, password: CharArray)' in vm
assert 'verified.snapshot == recorded' in vm
assert 'pendingEncryptedExportPassword' in vm and 'clearPendingEncryptedExport()' in vm
assert 'data.encryptedBundles.inspect(input, secret)' in vm
pwd_ui = (root / 'app/src/main/kotlin/io/github/lihuihnu/xuaneynote/ui/EncryptedPasswordDialog.kt').read_text()
assert 'PasswordVisualTransformation()' in pwd_ui and 'SecureFlagPolicy.SecureOn' in pwd_ui
assert 'rememberSaveable' not in pwd_ui and 'SavedStateHandle' not in pwd_ui
app_ui = (root / 'app/src/main/kotlin/io/github/lihuihnu/xuaneynote/ui/NoteApp.kt').read_text()
assert 'pendingEncryptedImportUri by remember {' in app_ui
assert 'exportEncryptedBundle.launch(' in app_ui
assert 'vm.exportEncryptedBundle(uri)' in app_ui
assert 'importEncryptedBundle.launch(' in app_ui
assert 'fun inspectBundle(uri: Uri) = inspectBundleInternal(uri, null)' in vm
assert '5f1eb1e9d67cfee200a695e4079751b49e73a226b154193e13f7c602efbe72fb' in (root / 'tools/b5/run_interop.sh').read_text(), 'Pinned candidate hash missing'
# API26 must run before the larger API35 full device suite to protect
# resource headroom when the official Runner installs a second SDK image.
assert workflow.index('B5.1 Android API26 crypto-only device gate') < workflow.index(
    'Phone, tablet window, Room backup and real process restart')
for avd_script in ('tools/b5/api26_gate.sh', 'tools/device_gate.sh'):
    avd_code = (root / avd_script).read_text()
    assert not re.search(r'(?m)^\s*echo no\s*\|\s*avdmanager', avd_code)
    assert '<<< "no"' in avd_code, 'AVD prompt must use a here-string'
assert 'b5-api26-avd-create.log' in (root / 'tools/b5/api26_gate.sh').read_text()
print('PASS source/privacy / frozen Room schemas / Debug image gate / B5.3 SAF + AES full readback / deterministic AVD startup / read-only CI')
if '--merged' in sys.argv:
    manifests = list((root / 'app/build/intermediates').glob('**/AndroidManifest.xml'))
    assert any('release' in str(p).lower() for p in manifests), 'No release merged manifest'
    for path in manifests:
        parsed = ET.parse(path).getroot()
        perms = {item.get(android+'name') for item in parsed.findall('uses-permission')}
        assert not ({'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE'} & perms), path
    print(f'PASS merged Manifest network-permission check ({len(manifests)} manifests)')
