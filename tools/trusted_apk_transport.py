#!/usr/bin/env python3
"""Read-only, opt-in transfer of a verified debug APK through GitHub job logs.

The GitHub Actions job MUST remain contents:read. Only a trusted maintainer,
after verifying run/source/tested SHA and signer identity, may publish a download.
This is not a public release or a substitute for device acceptance.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import zipfile
import zlib

PREFIX = "XUANEY_TRUSTED_APK_"
PROTOCOL = "XuaneyNote.test-apk-handoff-v1"
CHUNK_CHARS = 4096
MAX_APK_BYTES = 24 * 1024 * 1024
MAX_COMPRESSED_BYTES = 24 * 1024 * 1024
MAX_LOG_BYTES = 48 * 1024 * 1024
EXPECTED_PACKAGE = "io.github.lihuihnu.xuaneynote.debug"
EXPECTED_VERSION_CODE = "1"
EXPECTED_VERSION_NAME = "0.1.0-pr-a-debug"
EXPECTED_MIN_SDK = "26"
EXPECTED_TARGET_SDK = "36"
EXPECTED_SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()



def commit_sha(raw: str) -> str:
    if not re.fullmatch(r"[0-9a-fA-F]{40}", raw):
        raise ValueError("Expected a full 40-character Git commit SHA")
    return raw.lower()


def parse_signer(text: str) -> tuple[str, list[str]]:
    """Require a verified APK with exactly one signer and signature scheme v2+."""
    if re.search(r"(?m)^DOES NOT VERIFY\b|^ERROR\b", text):
        raise ValueError("Unverified APK or apksigner error")
    count = re.search(r"(?m)^Number of signers:\s*(\d+)\s*$", text)
    if count is None or int(count.group(1)) != 1:
        raise ValueError("APK must have exactly one signing certificate")
    schemes = []
    for no in (2, 3, 4):
        if re.search(rf"(?m)^Verified using v{no}(?:\.\d+)? scheme.*:\s*true\s*$", text):
            schemes.append(f"v{no}")
    if not schemes:
        raise ValueError("APK must verify with v2, v3, or v4 signing")
    cert = re.search(r"(?m)^Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9: ]+)\s*$", text)
    if cert is None:
        raise ValueError("No signer certificate SHA-256")
    key = cert.group(1).replace(":", "").replace(" ", "").lower()
    if not EXPECTED_SHA256.fullmatch(key):
        raise ValueError("Invalid signer fingerprint")
    return key, schemes


def parse_badging(text: str) -> dict:
    m = re.search(r"(?m)^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'", text)
    if m is None:
        raise ValueError("aapt did not report package ID and version")
    package, code, version = m.groups()
    min_sdk = re.search(r"(?m)^sdkVersion:'([^']+)'", text)
    target_sdk = re.search(r"(?m)^targetSdkVersion:'([^']+)'", text)
    if ((package, code, version) != (EXPECTED_PACKAGE, EXPECTED_VERSION_CODE, EXPECTED_VERSION_NAME)
            or min_sdk is None or target_sdk is None
            or min_sdk.group(1) != EXPECTED_MIN_SDK or target_sdk.group(1) != EXPECTED_TARGET_SDK):
        raise ValueError("Unexpected APK package/version/SDK; test package only")
    return {"application_id": package, "version_code": int(code), "version_name": version,
            "min_sdk": int(min_sdk.group(1)), "target_sdk": int(target_sdk.group(1))}


def validate_zip(raw: bytes) -> None:
    if not raw.startswith(b"PK\x03\x04"):
        raise ValueError("APK missing ZIP signature")
    with zipfile.ZipFile(io.BytesIO(raw)) as package:
        names = set(package.namelist())
        if "AndroidManifest.xml" not in names or "classes.dex" not in names:
            raise ValueError("APK missing Android manifest or executable dex")
        if package.testzip() is not None:
            raise ValueError("APK ZIP CRC failed")


def build_bundle(apk: bytes, signer_report: str, badging_report: str,
                 source_sha: str, tested_sha: str, run_id: str, run_attempt: str) -> tuple[dict, str, list[str]]:
    if not (100_000 <= len(apk) <= MAX_APK_BYTES):
        raise ValueError("APK outside permitted 100 KB–24 MiB size range")
    validate_zip(apk)
    signer, schemes = parse_signer(signer_report)
    badging = parse_badging(badging_report)
    source_sha, tested_sha = commit_sha(source_sha), commit_sha(tested_sha)
    if not run_id.isdecimal() or not run_attempt.isdecimal() or int(run_id) < 1 or int(run_attempt) < 1:
        raise ValueError("Invalid GitHub run identity")
    compressed = zlib.compress(apk, level=6)
    if len(compressed) > MAX_COMPRESSED_BYTES:
        raise ValueError("Compressed APK too large")
    encoded = base64.b64encode(compressed).decode("ascii")
    parts = [encoded[i:i + CHUNK_CHARS] for i in range(0, len(encoded), CHUNK_CHARS)]
    if not 1 <= len(parts) <= 12_000:
        raise ValueError("Transport chunk count out of range")
    manifest = {
        "protocol": PROTOCOL,
        "source_sha": source_sha,
        "tested_sha": tested_sha,
        "run_id": str(int(run_id)),
        "run_attempt": str(int(run_attempt)),
        "apk_filename": f"XuaneyNote-0.1.0-pr-a-{source_sha[:12]}-debug.apk",
        "apk_size": len(apk),
        "apk_sha256": digest(apk),
        "compression": "zlib",
        "compressed_size": len(compressed),
        "compressed_sha256": digest(compressed),
        "chunks": len(parts),
        "signer_certificate_sha256": signer,
        "verified_signature_schemes": schemes,
        "signing_policy": "ephemeral-runner-debug-key; not stable for upgrades; test data only",
        "distribution": "maintainer verification required; no Actions Artifact; no release at CI stage",
        **badging,
    }
    return manifest, digest(compressed), parts


def emit(args: argparse.Namespace) -> None:
    package = args.apk.read_bytes()
    manifest, compressed_sha, chunks = build_bundle(
        package, args.signer_report.read_text(encoding="utf-8"),
        args.badging_report.read_text(encoding="utf-8"),
        args.source_sha, args.tested_sha, args.run_id, args.run_attempt,
    )
    # These markers are intentionally public, contain no credentials or key material.
    print(f"{PREFIX}BEGIN {compressed_sha} {len(chunks)}")
    print(f"{PREFIX}META {json.dumps(manifest, ensure_ascii=True, separators=(',', ':'), sort_keys=True)}")
    for index, chunk in enumerate(chunks):
        print(f"{PREFIX}CHUNK {index} {chunk}")
    print(f"{PREFIX}END")
    print(f"APK_TEST_HANDOFF_SHA256 {manifest['apk_sha256']}")


def unpack_lines(lines: list[str], source_sha: str, tested_sha: str,
                 run_id: str, run_attempt: str) -> tuple[dict, bytes]:
    meta = None
    begin = None
    end = 0
    chunks: dict[int, str] = {}
    for line in lines:
        # GitHub Actions job logs normally prefix each original output line with timestamp.
        line = line.strip()
        loc = line.find(PREFIX)
        if loc < 0:
            continue
        item = line[loc + len(PREFIX):]
        if item.startswith("BEGIN "):
            if begin is not None:
                raise ValueError("Duplicate transport header")
            match = re.fullmatch(r"BEGIN ([0-9a-f]{64}) ([0-9]+)", item)
            if not match:
                raise ValueError("Invalid transport header")
            begin = (match.group(1), int(match.group(2)))
        elif item.startswith("META "):
            if meta is not None:
                raise ValueError("Duplicate manifest")
            meta = json.loads(item[5:])
        elif item.startswith("CHUNK "):
            match = re.fullmatch(r"CHUNK ([0-9]+) ([A-Za-z0-9+/=]+)", item)
            if not match:
                raise ValueError("Invalid chunk")
            index = int(match.group(1))
            if index in chunks:
                raise ValueError("Duplicate chunk index")
            if len(match.group(2)) > CHUNK_CHARS:
                raise ValueError("Chunk exceeds safe bound")
            chunks[index] = match.group(2)
        elif item == "END":
            end += 1
    if begin is None or meta is None or end != 1:
        raise ValueError("Missing header/manifest/ending")
    if not isinstance(meta, dict) or meta.get("protocol") != PROTOCOL:
        raise ValueError("Unexpected protocol")
    for key, expected in (("source_sha", commit_sha(source_sha)), ("tested_sha", commit_sha(tested_sha)),
                          ("run_id", str(int(run_id))), ("run_attempt", str(int(run_attempt)))):
        if meta.get(key) != expected:
            raise ValueError(f"Incorrect {key} in transport")
    expected_chunks = begin[1]
    if (expected_chunks != meta.get("chunks") or not (1 <= expected_chunks <= 12000)
            or len(chunks) != expected_chunks or any(i not in chunks for i in range(expected_chunks))):
        raise ValueError("Incomplete or disordered transport")
    compressed = base64.b64decode("".join(chunks[i] for i in range(expected_chunks)), validate=True)
    if (len(compressed) > MAX_COMPRESSED_BYTES or len(compressed) != meta.get("compressed_size")
            or digest(compressed) != begin[0] or digest(compressed) != meta.get("compressed_sha256")):
        raise ValueError("Compressed transport checksum/size mismatch")
    decompressor = zlib.decompressobj()
    raw = decompressor.decompress(compressed, MAX_APK_BYTES + 1)
    if len(raw) > MAX_APK_BYTES or not decompressor.eof or decompressor.unconsumed_tail or decompressor.unused_data:
        raise ValueError("Truncated/oversized/corrupt transport")
    raw += decompressor.flush()
    if len(raw) != meta.get("apk_size") or digest(raw) != meta.get("apk_sha256"):
        raise ValueError("APK payload checksum/size mismatch")
    if (meta.get("application_id") != EXPECTED_PACKAGE or meta.get("version_name") != EXPECTED_VERSION_NAME
            or meta.get("version_code") != 1 or meta.get("min_sdk") != 26 or meta.get("target_sdk") != 36
            or not isinstance(meta.get("verified_signature_schemes"), list)
            or not set(meta.get("verified_signature_schemes", [])) & {"v2", "v3", "v4"}
            or not EXPECTED_SHA256.fullmatch(str(meta.get("signer_certificate_sha256", "")))):
        raise ValueError("Unexpected signing or version manifest")
    if not re.fullmatch(r"XuaneyNote-0\.1\.0-pr-a-[a-f0-9]{12}-debug\.apk", str(meta.get("apk_filename", ""))):
        raise ValueError("Unsafe destination filename")
    validate_zip(raw)
    return meta, raw


def unpack(args: argparse.Namespace) -> None:
    if args.job_log.stat().st_size > MAX_LOG_BYTES:
        raise ValueError("GitHub log is too large")
    lines = args.job_log.read_text(encoding="utf-8-sig").splitlines()
    manifest, raw = unpack_lines(lines, args.source_sha, args.tested_sha, args.run_id, args.run_attempt)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    path = args.output_dir / manifest["apk_filename"]
    # O_EXCL prevents overwriting any previously delivered build or user file.
    with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as dst:
        dst.write(raw)
    (args.output_dir / f"{path.name}.manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Verified APK: {path} SHA-256: {manifest['apk_sha256']}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    p = subs.add_parser("emit", help="Emit verified APK into read-only CI job logs")
    p.add_argument("--apk", type=Path, required=True)
    p.add_argument("--signer-report", type=Path, required=True)
    p.add_argument("--badging-report", type=Path, required=True)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--tested-sha", required=True)
    p.add_argument("--run-id", required=True)
    p.add_argument("--run-attempt", required=True)
    p = subs.add_parser("unpack", help="Verify job log transport and extract APK")
    p.add_argument("job_log", type=Path)
    p.add_argument("output_dir", type=Path)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--tested-sha", required=True)
    p.add_argument("--run-id", required=True)
    p.add_argument("--run-attempt", required=True)
    args = parser.parse_args(argv)
    if args.command == "emit":
        emit(args)
    else:
        unpack(args)


if __name__ == "__main__":
    main()
