#!/usr/bin/env python3
"""Offline reconstruction of exactly the signed CI PR-B test APK. Python 3 standard library."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import zipfile
import zlib

APK_NAME = "XuaneyNote-0.2.0-pr-b-236c322852b9-debug.apk"
APK_SHA = "d6acc01cf8998a2edcc264ca3cb6ca914821527a5283b47a518d278a5fefb16b"
COMPRESSED_SHA = "45a97a5a5aaffc5c767068c79789d97a988bb2791b488fd1557307a4acf675e6"
CERT_SHA = "c72085e4e877783438930fceb829b6d2d103bf04c5724b05f1e9065ca8256d73"
SOURCE = "236c322852b914a748dbd9f5b2a7b64e558ec0bf"
TESTED = "22a29b630d85e12f477ae799b57922950215c851"
APK_SIZE = 11187385
COMPRESSED_SIZE = 10667924


def check_manifest(m: dict) -> list[dict]:
    expected = {
        "protocol": "XuaneyNote.test-apk-handoff-v1",
        "run_id": "39043407435",
        "run_attempt": "1",
        "source_sha": SOURCE,
        "tested_sha": TESTED,
        "apk_filename": APK_NAME,
        "apk_size": APK_SIZE,
        "apk_sha256": APK_SHA,
        "compressed_size": COMPRESSED_SIZE,
        "compressed_sha256": COMPRESSED_SHA,
        "signer_certificate_sha256": CERT_SHA,
        "application_id": "io.github.lihuihnu.xuaneynote.debug",
        "version_code": 2,
        "version_name": "0.2.0-pr-b-debug",
        "min_sdk": 26,
        "target_sdk": 36,
        "compression": "zlib",
        "part_count": 11,
    }
    for key, value in expected.items():
        if m.get(key) != value:
            raise ValueError("APK identity mismatch: " + key)
    parts = m.get("parts")
    if not isinstance(parts, list) or len(parts) != 11:
        raise ValueError("Expected eleven ordered binary parts")
    if sum(p["size"] for p in parts) != COMPRESSED_SIZE:
        raise ValueError("Compressed byte budget mismatch")
    for index, part in enumerate(parts):
        if part["path"] != f"parts/apk.zlib.part-{index:02d}":
            raise ValueError("Unexpected part filename or order")
        if len(part["sha256"]) != 64 or not all(c in "0123456789abcdef" for c in part["sha256"]):
            raise ValueError("Invalid part checksum")
        if not (1 <= part["size"] <= 983040):
            raise ValueError("Oversized or empty part")
    return parts


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent)
    args = parser.parse_args()
    home = Path(__file__).resolve().parent
    target_dir = args.output_dir.resolve()
    if not target_dir.is_dir():
        parser.error("Output directory does not exist")
    destination = target_dir / APK_NAME
    if destination.exists():
        parser.error("Refusing to overwrite an APK: " + str(destination))

    manifest = json.loads((home / "manifest.json").read_text(encoding="utf-8"))
    parts = check_manifest(manifest)
    fd, filename = tempfile.mkstemp(prefix=".xuaney-pr-b-", suffix=".partial", dir=target_dir)
    temp = Path(filename)
    total_compressed = 0
    total_decompressed = 0
    compressed_sha = hashlib.sha256()
    full_sha = hashlib.sha256()
    decompressor = zlib.decompressobj()
    try:
        with os.fdopen(fd, "wb") as output:
            for number, entry in enumerate(parts):
                path = home / entry["path"]
                part_sha = hashlib.sha256()
                count = 0
                with path.open("rb") as source:
                    while True:
                        block = source.read(65536)
                        if not block:
                            break
                        count += len(block)
                        if count > entry["size"]:
                            raise ValueError("Part exceeded expected size: " + str(path))
                        part_sha.update(block)
                        compressed_sha.update(block)
                        total_compressed += len(block)
                        chunk = decompressor.decompress(block, APK_SIZE + 1 - total_decompressed)
                        total_decompressed += len(chunk)
                        if total_decompressed > APK_SIZE or decompressor.unconsumed_tail or decompressor.unused_data:
                            raise ValueError("Malformed, trailing or oversized compressed APK")
                        output.write(chunk)
                        full_sha.update(chunk)
                if count != entry["size"] or part_sha.hexdigest() != entry["sha256"]:
                    raise ValueError(f"Corrupted APK zlib part {number + 1}/11: {path}")
                print(f"Verified compressed part {number + 1}/11", flush=True)
            final = decompressor.flush()
            total_decompressed += len(final)
            if final:
                output.write(final)
                full_sha.update(final)
            output.flush()
            os.fsync(output.fileno())
        if (total_compressed != COMPRESSED_SIZE or compressed_sha.hexdigest() != COMPRESSED_SHA
                or not decompressor.eof or decompressor.unused_data or decompressor.unconsumed_tail
                or total_decompressed != APK_SIZE or full_sha.hexdigest() != APK_SHA):
            raise ValueError("Final signed APK length, zlib integrity or SHA-256 mismatch")
        with zipfile.ZipFile(temp, "r") as archive:
            names = set(archive.namelist())
            if not {"AndroidManifest.xml", "classes.dex"} <= names:
                raise ValueError("APK missing required Android entries")
            if archive.testzip() is not None:
                raise ValueError("APK ZIP CRC failed")
        with os.fdopen(os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as dst:
            with temp.open("rb") as src:
                shutil.copyfileobj(src, dst, length=1024 * 1024)
            dst.flush()
            os.fsync(dst.fileno())
        if hashlib.sha256(destination.read_bytes()).hexdigest() != APK_SHA:
            destination.unlink(missing_ok=True)
            raise ValueError("Final output verification failed")
        print("Verified APK:", destination)
        print("SHA-256:", APK_SHA)
        print("Original CI debug signing certificate SHA-256:", CERT_SHA)
        print("IMPORTANT: ephemeral debug signing, test notes only; not an upgradeable release.")
    finally:
        temp.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAILED:", error, file=sys.stderr)
        sys.exit(1)
