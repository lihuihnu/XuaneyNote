#!/usr/bin/env python3
"""Bounded data-only evidence transport; this program never pushes or runs a log."""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
import zlib

LIMIT = 8 * 1024 * 1024
FILE_LIMIT = 512 * 1024
TOKEN = re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)')


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_path(name: str) -> bool:
    path = PurePosixPath(name)
    return bool(name) and not path.is_absolute() and str(path) == name and not ({'.', '..'} & set(path.parts)) and '\\' not in name


def envelope(payload: dict) -> str:
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()
    if len(raw) > LIMIT:
        raise ValueError('Evidence exceeds transport budget')
    encoded = base64.b64encode(zlib.compress(raw, 9)).decode()
    chunks = [encoded[i:i+2048] for i in range(0, len(encoded), 2048)]
    lines = [f'XUANEY_EVIDENCE_BEGIN {digest(raw)} {len(chunks)}']
    lines += [f'XUANEY_EVIDENCE_CHUNK {i} {part}' for i, part in enumerate(chunks)]
    return '\n'.join(lines + ['XUANEY_EVIDENCE_END']) + '\n'


def decode_log(text: str, expected: dict) -> dict:
    starts = re.findall(r'XUANEY_EVIDENCE_BEGIN ([0-9a-f]{64}) ([0-9]+)', text)
    if len(starts) != 1 or text.count('XUANEY_EVIDENCE_END') != 1:
        raise ValueError('Missing, duplicate or incomplete evidence envelope')
    checksum, count = starts[0]
    parts = re.findall(r'XUANEY_EVIDENCE_CHUNK ([0-9]+) ([A-Za-z0-9+/=]+)', text)
    if len(parts) != int(count) or [int(i) for i, _ in parts] != list(range(int(count))):
        raise ValueError('Missing, duplicate or reordered chunks')
    encoded = ''.join(part for _, part in parts)
    if len(encoded) > LIMIT * 2:
        raise ValueError('Compressed payload too large')
    decoder = zlib.decompressobj()
    raw = decoder.decompress(base64.b64decode(encoded, validate=True), LIMIT+1)
    if len(raw) > LIMIT or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail or digest(raw) != checksum:
        raise ValueError('Invalid compression, size or digest')
    payload = json.loads(raw)
    if payload.get('protocol') != 1:
        raise ValueError('Unknown evidence protocol')
    for key, value in expected.items():
        if str(payload['manifest'].get(key)) != str(value):
            raise ValueError(f'Evidence identity mismatch: {key}')
    seen = set()
    total = 0
    for item in payload['files']:
        name = item['path']
        if not safe_path(name) or name in seen or PurePosixPath(name).suffix not in {'.json', '.xml', '.log', '.md', '.png', '.sha256', '.txt', '.jar'}:
            raise ValueError('Unsafe or duplicate evidence filename')
        if PurePosixPath(name).parts[0] not in {'logs', 'reports', 'schemas', 'screenshots', 'bootstrap'}:
            raise ValueError('Unexpected evidence directory')
        seen.add(name)
        data = base64.b64decode(item['base64'], validate=True)
        total += len(data)
        if len(data) != item['size'] or digest(data) != item['sha256'] or len(data) > FILE_LIMIT or total > LIMIT:
            raise ValueError('Evidence file checksum/size mismatch')
        if name.endswith('.jar') and (name != 'bootstrap/gradle-wrapper.jar' or digest(data) != '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'):
            raise ValueError('Unverified Wrapper binary')
        if TOKEN.search(data):
            raise ValueError('Possible credential in evidence')
    return payload


def collect(root: Path) -> dict:
    out = root / 'ci-out'
    gates = [json.loads(p.read_text()) for p in sorted((out/'status').glob('*.json'))]
    manifest = {
        'repository': os.environ.get('GITHUB_REPOSITORY', 'lihuihnu/XuaneyNote'),
        'source_sha': os.environ.get('SOURCE_SHA', 'local-uncommitted'),
        'tested_sha': os.environ.get('TESTED_SHA', 'local-uncommitted'),
        'run_id': os.environ.get('GITHUB_RUN_ID', 'local'),
        'run_attempt': os.environ.get('GITHUB_RUN_ATTEMPT', '1'),
        'runner': os.environ.get('RUNNER_NAME', 'local'),
        'runner_image': os.environ.get('ImageVersion', 'not-recorded'),
        'job_status_at_collection': os.environ.get('CI_JOB_STATUS', 'not-a-runner-job'),
        'gates': gates,
        'archive_status': 'transported-not-yet-repository-verified',
        'real_device_acceptance': 'pending',
    }
    files, omissions = [], []
    candidates = []
    for path in (out/'logs').glob('*.log'):
        candidates.append((f'logs/{path.name}', path, 64*1024))
    for path in (out/'reports').glob('**/*.xml'):
        candidates.append(('reports/'+path.relative_to(out/'reports').as_posix(), path, FILE_LIMIT))
    for path in (root/'core/model/build/test-results').glob('**/*.xml'):
        candidates.append(('reports/model/'+path.relative_to(root/'core/model/build/test-results').as_posix(), path, FILE_LIMIT))
    for path in (root/'core/data/schemas').glob('**/*.json'):
        candidates.append(('schemas/'+path.relative_to(root/'core/data/schemas').as_posix(), path, FILE_LIMIT))
    for path in (out/'screenshots').glob('*.png'):
        candidates.append(('screenshots/'+path.name, path, FILE_LIMIT))
    for module in ('app', 'core/data'):
        folder = root/module/'build/outputs/androidTest-results'
        for path in folder.glob('**/*.xml'):
            candidates.append(('reports/last-device/'+module+'/'+path.relative_to(folder).as_posix(), path, FILE_LIMIT))
    for name, relative in [('gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.jar'), ('gradlew.txt', 'gradlew'), ('gradlew.bat.txt', 'gradlew.bat'), ('gradle-wrapper.properties.txt', 'gradle/wrapper/gradle-wrapper.properties')]:
        path = root/relative
        if path.is_file():
            candidates.append(('bootstrap/'+name, path, FILE_LIMIT))
    manifest['temporary_apks'] = [{'path': path.relative_to(root).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest(path.read_bytes()), 'delivery': 'not-published'} for path in (root/'app/build/outputs/apk').glob('**/*.apk')]
    for name, path, limit in candidates:
        if path.is_symlink():
            raise ValueError('Symlinks cannot enter evidence')
        original = path.read_bytes()
        if TOKEN.search(original):
            raise ValueError('Possible credential found; refuse publication')
        if len(original) > limit and path.suffix != '.log':
            omissions.append({'path': name, 'reason': 'size-budget', 'original_size': len(original), 'sha256': digest(original)})
            continue
        data = original[-limit:]
        files.append({'path': name, 'size': len(data), 'sha256': digest(data), 'base64': base64.b64encode(data).decode(),
            'original_size': len(original), 'original_sha256': digest(original), 'truncated': len(original) > limit})
    manifest['omissions'] = omissions
    return {'protocol': 1, 'manifest': manifest, 'files': files}


def unpack(payload: dict, destination: Path) -> None:
    if destination.exists():
        raise ValueError('Never overwrite an existing evidence directory')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix='.xuaney-evidence-', dir=destination.parent))
    try:
        for item in payload['files']:
            path = temporary / item['path']
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(base64.b64decode(item['base64'], validate=True))
        (temporary/'manifest.json').write_text(json.dumps(payload['manifest'], ensure_ascii=False, indent=2)+'\n')
        (temporary/'results.json').write_text(json.dumps({'gates': payload['manifest'].get('gates', []),
            'files': [{k: v for k, v in item.items() if k != 'base64'} for item in payload['files']]}, ensure_ascii=False, indent=2)+'\n')
        (temporary/'summary.md').write_text('# XuaneyNote CI evidence\n\nData transport verified; repository read-back and real-device acceptance remain separate gates.\n')
        paths = sorted(p for p in temporary.rglob('*') if p.is_file())
        (temporary/'checksums.sha256').write_text(''.join(f'{digest(p.read_bytes())}  {p.relative_to(temporary).as_posix()}\n' for p in paths))
        temporary.rename(destination)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def main() -> None:
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('emit')
    extract = commands.add_parser('unpack')
    extract.add_argument('log', type=Path)
    extract.add_argument('destination', type=Path)
    for key in ('source-sha', 'tested-sha', 'run-id', 'run-attempt'):
        extract.add_argument('--'+key, required=True)
    args = parser.parse_args()
    if args.command == 'emit':
        payload = collect(Path(__file__).resolve().parents[1])
        text = envelope(payload)
        decode_log(text, {key: payload['manifest'][key] for key in ('source_sha', 'tested_sha', 'run_id', 'run_attempt')})
        print(text, end='')
    else:
        expected = {key: getattr(args, key) for key in ('source_sha', 'tested_sha', 'run_id', 'run_attempt')}
        unpack(decode_log(args.log.read_text(), expected), args.destination)


if __name__ == '__main__':
    main()
