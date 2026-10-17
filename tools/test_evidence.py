#!/usr/bin/env python3
import base64
import copy
from pathlib import Path
import tempfile
import unittest
from evidence import decode_log, digest, envelope, unpack


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.identity = {'source_sha': 'a'*40, 'tested_sha': 'b'*40, 'run_id': '123', 'run_attempt': '1'}
        raw = b'PASS synthetic fixture\n'
        self.payload = {'protocol': 1, 'manifest': self.identity, 'files': [{
            'path': 'logs/synthetic.log', 'base64': base64.b64encode(raw).decode(),
            'size': len(raw), 'sha256': digest(raw)}]}

    def test_success_roundtrip(self):
        self.assertEqual(self.payload, decode_log(envelope(self.payload), self.identity))

    def test_expected_failure_is_preserved_not_changed_to_pass(self):
        payload = copy.deepcopy(self.payload)
        payload['manifest']['gates'] = [{'gate': 'controlled-failure', 'exit_code': 17, 'conclusion': 'failure'}]
        decoded = decode_log(envelope(payload), self.identity)
        self.assertEqual(17, decoded['manifest']['gates'][0]['exit_code'])

    def test_wrong_source_rejected(self):
        with self.assertRaises(ValueError):
            decode_log(envelope(self.payload), self.identity | {'source_sha': 'c'*40})

    def test_truncation_rejected(self):
        with self.assertRaises(ValueError):
            decode_log(envelope(self.payload).replace('XUANEY_EVIDENCE_END', ''), self.identity)

    def test_path_traversal_rejected(self):
        for path in ('../escape.log', '/tmp/escape.log', 'logs/../../escape.log', 'logs\\escape.log'):
            payload = copy.deepcopy(self.payload)
            payload['files'][0]['path'] = path
            with self.assertRaises(ValueError):
                decode_log(envelope(payload), self.identity)

    def test_duplicate_and_checksum_rejected(self):
        payload = copy.deepcopy(self.payload)
        payload['files'].append(payload['files'][0])
        with self.assertRaises(ValueError):
            decode_log(envelope(payload), self.identity)
        payload = copy.deepcopy(self.payload)
        payload['files'][0]['sha256'] = '0'*64
        with self.assertRaises(ValueError):
            decode_log(envelope(payload), self.identity)

    def test_does_not_overwrite_existing_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'evidence'
            unpack(self.payload, target)
            self.assertTrue((target / 'checksums.sha256').is_file())
            with self.assertRaises(ValueError):
                unpack(self.payload, target)


if __name__ == '__main__':
    unittest.main(verbosity=2)
