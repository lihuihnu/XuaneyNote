#!/usr/bin/env bash
# B5.1 isolated JVM / independent 7-Zip interoperability; synthetic data/password only.
set -euo pipefail
export LANG=C.UTF-8
ROOT="$(pwd)"
[[ -n "$RUNNER_TEMP" ]] || { echo "RUNNER_TEMP missing"; exit 1; }
WORK="$(mktemp -d "$RUNNER_TEMP/xuaney-b5-XXXXXX")"
trap 'rm -rf -- "$WORK"' EXIT
mkdir -p "$WORK/classes" "$ROOT/ci-out/logs"

# Test-only candidate, no production Gradle dependency. Check Maven Central's published digest over TLS.
URL="https://repo.maven.apache.org/maven2/net/lingala/zip4j/zip4j/2.11.6/zip4j-2.11.6.jar"
curl --fail --location --silent --show-error --retry 2 --max-time 120 "$URL" --output "$WORK/zip4j.jar"
curl --fail --location --silent --show-error --retry 2 --max-time 45 "$URL.sha1" --output "$WORK/zip4j.jar.sha1"
EXPECTED="$(tr -d '[:space:]' < "$WORK/zip4j.jar.sha1" | tr '[:upper:]' '[:lower:]')"
ACTUAL="$(sha1sum "$WORK/zip4j.jar" | cut -d' ' -f1)"
[[ "$EXPECTED" =~ ^[a-f0-9]{40}$ && "$ACTUAL" == "$EXPECTED" ]]
sha256sum "$WORK/zip4j.jar" > "$ROOT/ci-out/logs/b5-zip4j-sha256.log"
echo "Zip4j candidate SHA-256: $(cut -d' ' -f1 < "$ROOT/ci-out/logs/b5-zip4j-sha256.log")"
echo "Candidate SHA-256 must be independently frozen before production admission."

SEVEN_Z="$(command -v 7z || command -v 7zz || true)"
if [[ -z "$SEVEN_Z" ]]; then
    sudo apt-get update -qq
    sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq 7zip \
        || sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq p7zip-full
    SEVEN_Z="$(command -v 7z || command -v 7zz || true)"
fi
[[ -n "$SEVEN_Z" ]] || { echo "Independent 7-Zip unavailable"; exit 1; }
"$SEVEN_Z" i > "$WORK/7z-capabilities.log"
head -12 "$WORK/7z-capabilities.log" > "$ROOT/ci-out/logs/b5-7z-version.log"

javac --release 8 -encoding UTF-8 -cp "$WORK/zip4j.jar" -d "$WORK/classes" tools/b5/Zip4jInteropProbe.java
CLASS="io.github.lihuihnu.xuaneynote.data.b5.Zip4jInteropProbe"
probe() { java -cp "$WORK/classes:$WORK/zip4j.jar" "$CLASS" "$@"; }

python3 - "$WORK/payload.xuaney.zip" <<'PY'
import sys
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile(sys.argv[1], "w", compression=ZIP_DEFLATED) as out:
    out.writestr("manifest.json", '{"format":"XuaneyNote.synthetic-test","version":0}')
    out.writestr("notes.json", '{"synthetic_only":true,"text":"UTF8 中文 🧪"}')
PY

# Public, deliberately synthetic passphrase; NEVER use actual user secrets in CI.
PASSWORD="Synthetic-2026-中文🧪-long-compatibility-password"
export XUANEY_B5_PROBE_PASSWORD="$PASSWORD"
probe write-aes "$WORK/payload.xuaney.zip" "$WORK/from-zip4j.zip"
probe verify "$WORK/from-zip4j.zip" "$WORK/payload.xuaney.zip"
"$SEVEN_Z" t -y "-p$PASSWORD" "$WORK/from-zip4j.zip" > "$ROOT/ci-out/logs/b5-7z.log"
mkdir "$WORK/from-java-extracted"
"$SEVEN_Z" x -y "-p$PASSWORD" "-o$WORK/from-java-extracted" "$WORK/from-zip4j.zip" >> "$ROOT/ci-out/logs/b5-7z.log"
cmp "$WORK/payload.xuaney.zip" "$WORK/from-java-extracted/payload.xuaney.zip"

( cd "$WORK" && "$SEVEN_Z" a -y -tzip -mem=AES256 "-p$PASSWORD" from-7z.zip payload.xuaney.zip ) >> "$ROOT/ci-out/logs/b5-7z.log"
probe verify "$WORK/from-7z.zip" "$WORK/payload.xuaney.zip"

# Require exceptions for wrong password, mutated AE-2 MAC, truncation and weak formats.
XUANEY_B5_PROBE_PASSWORD="Wrong-long-synthetic-2026" probe reject "$WORK/from-zip4j.zip"
probe tamper-mac "$WORK/from-zip4j.zip" "$WORK/changed-mac.zip"
probe reject "$WORK/changed-mac.zip"
python3 - "$WORK/from-zip4j.zip" "$WORK/truncated.zip" <<'PY'
import sys
from pathlib import Path
data = Path(sys.argv[1]).read_bytes()
assert len(data) > 64
Path(sys.argv[2]).write_bytes(data[:-12])
PY
probe reject "$WORK/truncated.zip"
probe write-plain "$WORK/payload.xuaney.zip" "$WORK/plain.zip"
probe reject "$WORK/plain.zip"
probe write-aes128 "$WORK/payload.xuaney.zip" "$WORK/aes128.zip"
probe reject "$WORK/aes128.zip"
probe write-legacy "$WORK/payload.xuaney.zip" "$WORK/zipcrypto.zip"
probe reject "$WORK/zipcrypto.zip"
probe write-two "$WORK/payload.xuaney.zip" "$WORK/multiple.zip"
probe reject "$WORK/multiple.zip"
echo "PASS B5.1: bidirectional 7-Zip, Unicode, wrong password, MAC, truncation and weak-format rejects"
