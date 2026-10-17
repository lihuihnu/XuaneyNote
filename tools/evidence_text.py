#!/usr/bin/env python3
"""小型可读副本：便于连接器逐文件取回 schema/报告；不代替完整证据封装或远端核验。"""
import base64
import hashlib
import json
from pathlib import Path, PurePosixPath

FILE_LIMIT = 16 * 1024
TOTAL_LIMIT = 64 * 1024


def text_records(payload):
    selected, omitted = [], []
    used = 0
    # 优先保留 schema、工具链文本和测试报告，不让重复日志挤掉合并所需的基线。
    priority = {'schemas': 0, 'bootstrap': 1, 'reports': 2, 'logs': 3}
    for item in sorted(payload['files'], key=lambda row: (priority.get(PurePosixPath(row['path']).parts[0], 9), row['path'])):
        path = PurePosixPath(item['path'])
        if path.is_absolute() or '..' in path.parts or '\\' in item['path']:
            raise ValueError('Unsafe evidence path')
        if path.parts[0] not in priority or path.suffix not in {'.json', '.xml', '.log', '.md', '.txt', '.sha256'}:
            continue
        data = base64.b64decode(item['base64'], validate=True)
        if len(data) != item['size'] or hashlib.sha256(data).hexdigest() != item['sha256']:
            raise ValueError('Evidence text checksum mismatch')
        record = {key: value for key, value in item.items() if key != 'base64'}
        if len(data) > FILE_LIMIT:
            omitted.append({'path': item['path'], 'reason': 'readable-file-budget'})
            continue
        try:
            record['text'] = data.decode('utf-8', errors='strict')
        except UnicodeDecodeError:
            omitted.append({'path': item['path'], 'reason': 'not-complete-utf8'})
            continue
        # JSON 转义换行/控制字符，日志内容不会成为 Actions workflow command。
        line = 'XUANEY_TEXT_FILE ' + json.dumps(record, ensure_ascii=True, separators=(',', ':'))
        size = len(line.encode('utf-8'))
        if used + size > TOTAL_LIMIT:
            omitted.append({'path': item['path'], 'reason': 'readable-total-budget'})
            continue
        selected.append(line)
        used += size
    return selected, omitted


def main():
    from evidence import collect
    payload = collect(Path(__file__).resolve().parents[1])
    lines, omitted = text_records(payload)
    metadata = {'format': 'XuaneyNote.readable-evidence-v1', 'manifest': payload['manifest'],
                'record_count': len(lines), 'readable_omissions': omitted,
                'coverage': 'supplemental-subset-not-full-envelope'}
    print('XUANEY_TEXT_META ' + json.dumps(metadata, ensure_ascii=True, separators=(',', ':')))
    for line in lines:
        print(line)
    digest = hashlib.sha256(('\n'.join(lines)+'\n').encode('utf-8')).hexdigest()
    print('XUANEY_TEXT_END ' + digest)


if __name__ == '__main__':
    main()
