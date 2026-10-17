#!/usr/bin/env python3
"""Reconstruct the exact signed XuaneyNote debug APK. Python 3, stdlib only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import urllib.request
import zipfile

OWNER_REPO = "lihuihnu/XuaneyNote"
DATA_COMMIT = "39a0d83a3908c99446bb5b96a4d7b9eb261f4b96"
RAW_BASE = f"https://raw.githubusercontent.com/{OWNER_REPO}/{DATA_COMMIT}/"
EXPECTED_SHA256 = "9def70c30bed6953a02151ef217aa6935f9948094298c78fc186bd823038f170"
EXPECTED_SIGNER_SHA256 = "c8e2c35d3f7dc5f8867485baca46eb2a9f0efff6671b72f0b6357b7104c72ee4"
SOURCE_SHA = "19532a929475e9ce5e9f7fb2589654fb67f101d1"
FILENAME = "XuaneyNote-0.1.0-pr-a-19532a929475-debug.apk"
EXPECTED_SIZE = 11023697


def data(path, base):
    local = base / path
    if local.is_file():
        return local.read_bytes()
    request = urllib.request.Request(
        RAW_BASE + path, headers={"User-Agent": "XuaneyNote-verified-test-apk/1"}
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read(2 * 1024 * 1024)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--output-dir", type=Path, default=Path.cwd())
    args = ap.parse_args()
    folder = Path(__file__).resolve().parent
    output = args.output_dir.expanduser().resolve()
    if not output.is_dir():
        ap.error("--output-dir must be an existing directory")
    destination = output / FILENAME
    if destination.exists():
        ap.error("Refusing to overwrite an existing APK: " + str(destination))
    manifest = json.loads(data("manifest.json", folder))
    if (manifest.get("apk_sha256") != EXPECTED_SHA256
        or manifest.get("apk_size") != EXPECTED_SIZE
        or manifest.get("apk_filename") != FILENAME
        or manifest.get("source_sha") != SOURCE_SHA
        or manifest.get("signer_certificate_sha256") != EXPECTED_SIGNER_SHA256
        or manifest.get("run_id") != "38995733428"
        or manifest.get("application_id") != "io.github.lihuihnu.xuaneynote.debug"):
        raise ValueError("Unexpected APK manifest identity")
    parts = manifest.get("parts", [])
    if len(parts) != 11:
        raise ValueError("Expected eleven ordered APK parts")
    handle = tempfile.NamedTemporaryFile(
        mode="wb", prefix=".xuaney-", suffix=".partial", dir=output, delete=False
    )
    tmp = Path(handle.name)
    whole_hash = hashlib.sha256()
    total = 0
    try:
        with handle:
            for i, part in enumerate(parts):
                expected_path = f"parts/apk.part-{i:02}"
                if part.get("path") != expected_path:
                    raise ValueError("Unexpected part order")
                segment = data(expected_path, folder)
                if (len(segment) != part["size"]
                    or hashlib.sha256(segment).hexdigest() != part["sha256"]):
                    raise ValueError("Truncated or corrupted APK chunk: " + expected_path)
                handle.write(segment)
                whole_hash.update(segment)
                total += len(segment)
                print(f"Verified {i + 1}/{len(parts)} parts: {expected_path}", flush=True)
        if total != EXPECTED_SIZE or whole_hash.hexdigest() != EXPECTED_SHA256:
            raise ValueError("Combined APK length/hash mismatch")
        with zipfile.ZipFile(tmp) as archive:
            files = set(archive.namelist())
            if not {"AndroidManifest.xml", "classes.dex"} <= files:
                raise ValueError("Missing Android APK manifest or dex")
            bad = archive.testzip()
            if bad is not None:
                raise ValueError("ZIP CRC failed: " + bad)
        # Exclusive create avoids replacing an existing user download.
        with os.fdopen(os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as dst:
            with tmp.open("rb") as src:
                shutil.copyfileobj(src, dst, length=1024 * 1024)
        if hashlib.sha256(destination.read_bytes()).hexdigest() != EXPECTED_SHA256:
            destination.unlink(missing_ok=True)
            raise ValueError("Final download SHA-256 mismatch")
        print(f"APK ready: {destination}")
        print(f"SHA-256: {EXPECTED_SHA256}")
        print(f"CI signing certificate SHA-256: {EXPECTED_SIGNER_SHA256}")
        print("Warning: ephemeral debug certificate; test data only; not a production release.")
    finally:
        tmp.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
