#!/usr/bin/env python3
"""Standard-library-only adversarial regression tests; fake ZIP isn't an Android app."""
import contextlib
import io
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

from trusted_apk_transport import build_bundle, digest, unpack_lines, parse_badging, parse_signer

SOURCE = 'a' * 40
TESTED = 'b' * 40
SIGNER = ('Verified\nVerified using v1 scheme (JAR signing): false\n'
          'Verified using v2 scheme (APK Signature Scheme v2): true\n'
          'Verified using v3 scheme (APK Signature Scheme v3): true\n'
          'Number of signers: 1\n'
          'Signer #1 certificate SHA-256 digest: ' + 'c' * 64 + '\n')
BADGING = ("package: name='io.github.lihuihnu.xuaneynote.debug' versionCode='2' "
           "versionName='0.2.0-pr-b-debug' compileSdkVersion='36'\n"
           "sdkVersion:'26'\ntargetSdkVersion:'36'\n")


def synthetic_zip():
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED) as apk:
        apk.writestr('AndroidManifest.xml', 'a testing stub')
        apk.writestr('classes.dex', b'dex\n035\x00' + os.urandom(110_000))
    return stream.getvalue()


def emit_lines(bundle):
    m, compressed_sha, chunks = bundle
    import json
    return ([f'2026-10-17T01:23:00Z XUANEY_TRUSTED_APK_BEGIN {compressed_sha} {len(chunks)}',
             'XUANEY_TRUSTED_APK_META ' + json.dumps(m, separators=(',', ':'))]
            + [f'XUANEY_TRUSTED_APK_CHUNK {i} {part}' for i, part in enumerate(chunks)]
            + ['XUANEY_TRUSTED_APK_END'])


class TransportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fake_apk = synthetic_zip()
        cls.bundle = build_bundle(cls.fake_apk, SIGNER, BADGING, SOURCE, TESTED, '12345', '1')
        cls.lines = emit_lines(cls.bundle)

    def test_roundtrip_and_sha256(self):
        manifest, restored = unpack_lines(self.lines, SOURCE, TESTED, '12345', '1')
        self.assertEqual(restored, self.fake_apk)
        self.assertEqual(manifest['apk_sha256'], digest(self.fake_apk))
        self.assertEqual(manifest['application_id'], 'io.github.lihuihnu.xuaneynote.debug')
        self.assertEqual(manifest['verified_signature_schemes'], ['v2', 'v3'])

    def test_reject_wrong_run_or_source(self):
        for source, run in [(TESTED, '12345'), (SOURCE, '999')]:
            with self.assertRaises(ValueError):
                unpack_lines(self.lines, source, TESTED, run, '1')

    def test_reject_missing_chunk(self):
        with self.assertRaises(ValueError):
            unpack_lines([x for x in self.lines if '_CHUNK 0 ' not in x], SOURCE, TESTED, '12345', '1')

    def test_reject_duplicate_chunk(self):
        with self.assertRaises(ValueError):
            unpack_lines(self.lines + [self.lines[2]], SOURCE, TESTED, '12345', '1')

    def test_reject_tamper_chunk(self):
        altered = self.lines.copy()
        altered[3] = altered[3][:-1] + ('A' if altered[3][-1] != 'A' else 'B')
        with self.assertRaises(ValueError):
            unpack_lines(altered, SOURCE, TESTED, '12345', '1')

    def test_reject_forged_metadata(self):
        import json
        altered = self.lines.copy()
        m = json.loads(altered[1].split('_META ', 1)[1])
        m['application_id'] = 'evil.other.package'
        altered[1] = 'XUANEY_TRUSTED_APK_META ' + json.dumps(m)
        with self.assertRaises(ValueError):
            unpack_lines(altered, SOURCE, TESTED, '12345', '1')

    def test_reject_truncated_envelope(self):
        with self.assertRaises(ValueError):
            unpack_lines(self.lines[:-1], SOURCE, TESTED, '12345', '1')

    def test_reject_unsigned_package(self):
        with self.assertRaises(ValueError):
            parse_signer(SIGNER.replace('Number of signers: 1', 'Number of signers: 0'))
        with self.assertRaises(ValueError):
            parse_signer(SIGNER.replace('v2): true', 'v2): false').replace('v3): true','v3): false'))

    def test_reject_package_identity_mismatch(self):
        with self.assertRaises(ValueError):
            parse_badging(BADGING.replace('.debug', '.release'))

    def test_zip_rejected(self):
        with self.assertRaises(ValueError):
            build_bundle(b'not a valid APK'*10000, SIGNER, BADGING, SOURCE, TESTED, '12345', '1')

    def test_reject_mismatched_end_index(self):
        with self.assertRaises(ValueError):
            unpack_lines(self.lines + ['XUANEY_TRUSTED_APK_END'], SOURCE, TESTED, '12345', '1')


if __name__ == '__main__':
    unittest.main(verbosity=2)
