#!/usr/bin/env python3
import base64
import hashlib
import json
import unittest
from evidence_text import FILE_LIMIT, TOTAL_LIMIT, text_records


def row(path, data):
    return {'path': path, 'base64': base64.b64encode(data).decode(),
            'size': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'truncated': False}


class EvidenceTextTest(unittest.TestCase):
    def test_roundtrip_preserves_bytes_and_commands_are_escaped(self):
        data = '中文\r\n::error::not a command\n'.encode()
        lines, omitted = text_records({'files': [row('schemas/example/1.json', data)]})
        self.assertFalse(omitted)
        self.assertEqual(1, len(lines))
        self.assertNotIn('\n', lines[0])
        decoded = json.loads(lines[0].split(' ', 1)[1])
        self.assertEqual(data, decoded['text'].encode())

    def test_binary_is_excluded_and_schema_has_priority(self):
        files = [row('bootstrap/gradle-wrapper.jar', b'jar'), row('logs/build.log', b'log'),
                 row('schemas/example/1.json', b'{}')]
        lines, omitted = text_records({'files': files})
        self.assertEqual(2, len(lines))
        self.assertIn('schemas/example/1.json', lines[0])
        self.assertFalse(omitted)

    def test_budget_and_utf8_omissions_are_explicit(self):
        files = [row('logs/large.log', b'x'*(FILE_LIMIT+1)), row('logs/bad.log', b'\xff')]
        files += [row(f'logs/small-{i}.log', b'x'*(FILE_LIMIT-1)) for i in range(8)]
        lines, omitted = text_records({'files': files})
        self.assertLessEqual(sum(len(line.encode()) for line in lines), TOTAL_LIMIT)
        self.assertEqual({'readable-file-budget', 'readable-total-budget', 'not-complete-utf8'},
                         {item['reason'] for item in omitted})

    def test_corrupt_or_unsafe_record_is_rejected(self):
        bad = row('logs/file.log', b'payload')
        bad['sha256'] = '0'*64
        with self.assertRaises(ValueError): text_records({'files': [bad]})
        with self.assertRaises(ValueError): text_records({'files': [row('../bad.log', b'payload')]})


if __name__ == '__main__':
    unittest.main(verbosity=2)
