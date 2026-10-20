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
JAR_HASH="$(cut -d' ' -f1 < "$ROOT/ci-out/logs/b5-zip4j-sha256.log")"
# Frozen from the original official B5.1 run 39090047535, not guessed from the filename.
PINNED_ZIP4J_SHA256="5f1eb1e9d67cfee200a695e4079751b49e73a226b154193e13f7c602efbe72fb"
[[ "$JAR_HASH" == "$PINNED_ZIP4J_SHA256" ]] || {
    echo "Zip4j 2.11.6 binary changed unexpectedly; reject this artifact."
    exit 1
}
echo "Zip4j 2.11.6 SHA-256 pinned: $JAR_HASH"

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

# Separate cryptographic format interoperability from ZIP non-ASCII password interoperability.
# 7-Zip does not consistently support non-ASCII ZIP passwords across platforms.
# Unicode MUST pass an INDEPENDENT implementation (pyzipper), not merely Zip4j self-decrypt.
UNICODE_PASSWORD="Synthetic-2026-中文🧪-long-compatibility-password"
ASCII_PASSWORD="OnlyASCII-Synthetic-2026-Long-Password"
export XUANEY_B5_PROBE_PASSWORD="$UNICODE_PASSWORD"
probe write-aes "$WORK/payload.xuaney.zip" "$WORK/from-zip4j-unicode.zip"
probe verify "$WORK/from-zip4j-unicode.zip" "$WORK/payload.xuaney.zip"

# Test-only Python dependency, isolated under RUNNER_TEMP; never part of the Android APK.
python3 -m pip install --quiet --disable-pip-version-check --no-input --no-cache-dir \
    --only-binary=:all: --target "$WORK/py-lib" \
    "pyzipper==0.4.0" "pycryptodomex==3.23.0" \
    > "$ROOT/ci-out/logs/b5-pyzipper-install.log" 2>&1
PYTHONPATH="$WORK/py-lib" python3 - "$WORK/from-zip4j-unicode.zip" \
    "$WORK/payload.xuaney.zip" "$WORK/from-pyzipper-unicode.zip" <<'PY'
import sys
from pathlib import Path
import pyzipper

encrypted_from_java, original, encrypted_from_python = map(Path, sys.argv[1:])
password = "Synthetic-2026-中文🧪-long-compatibility-password".encode("utf-8")
with pyzipper.AESZipFile(str(encrypted_from_java), "r") as archive:
    names = archive.namelist()
    assert names == ["payload.xuaney.zip"], names
    assert archive.getinfo(names[0]).wz_aes_strength == 3, "Not AES-256"
    archive.setpassword(password)
    assert archive.read(names[0]) == original.read_bytes(), "Zip4j -> pyzipper mismatch"
with pyzipper.AESZipFile(str(encrypted_from_python), "w",
        compression=pyzipper.ZIP_DEFLATED, encryption=pyzipper.WZ_AES) as archive:
    archive.setpassword(password)
    archive.setencryption(pyzipper.WZ_AES, nbits=256)
    archive.write(str(original), arcname="payload.xuaney.zip")
print("PASS B5.1: independent pyzipper UTF-8 Chinese/Emoji AES-256")
PY
probe verify "$WORK/from-pyzipper-unicode.zip" "$WORK/payload.xuaney.zip"

# Third implementation: 7-Zip MUST pass bidirectional interop for a portable ASCII
# password. It is not permissible to claim non-ASCII 7-Zip interoperability from this.
export XUANEY_B5_PROBE_PASSWORD="$ASCII_PASSWORD"
probe write-aes "$WORK/payload.xuaney.zip" "$WORK/from-zip4j-ascii.zip"
"$SEVEN_Z" t -y "-p$ASCII_PASSWORD" "$WORK/from-zip4j-ascii.zip" > "$ROOT/ci-out/logs/b5-7z.log"
mkdir "$WORK/from-java-extracted"
"$SEVEN_Z" x -y "-p$ASCII_PASSWORD" "-o$WORK/from-java-extracted" \
    "$WORK/from-zip4j-ascii.zip" >> "$ROOT/ci-out/logs/b5-7z.log"
cmp "$WORK/payload.xuaney.zip" "$WORK/from-java-extracted/payload.xuaney.zip"
( cd "$WORK" && "$SEVEN_Z" a -y -tzip -mem=AES256 "-p$ASCII_PASSWORD" \
    from-7z-ascii.zip payload.xuaney.zip ) >> "$ROOT/ci-out/logs/b5-7z.log"
probe verify "$WORK/from-7z-ascii.zip" "$WORK/payload.xuaney.zip"

# Report (never suppress in documentation) 7-Zip's non-ASCII ZIP password limitation.
# Only optional compatibility observation: the Unicode safety proof is the independent
# pyzipper test above. The mandatory 7-Zip gate is strict and uses ASCII.
if "$SEVEN_Z" t -y "-p$UNICODE_PASSWORD" "$WORK/from-zip4j-unicode.zip" \
      > "$ROOT/ci-out/logs/b5-7z-unicode-observation.log" 2>&1; then
    echo "OBSERVED: 7-Zip accepts UTF-8 ZIP password on this Runner; do not generalize."
else
    echo "KNOWN LIMITATION: this 7-Zip rejects non-ASCII ZIP passwords; external tools may differ."
    echo "Do not claim same-password non-ASCII portability across all ZIP software."
fi
export XUANEY_B5_PROBE_PASSWORD="$UNICODE_PASSWORD"

# Require exceptions for wrong password, mutated AE-2 MAC, truncation and weak formats.
XUANEY_B5_PROBE_PASSWORD="Wrong-long-synthetic-2026" probe reject "$WORK/from-zip4j-unicode.zip"
probe tamper-mac "$WORK/from-zip4j-unicode.zip" "$WORK/changed-mac.zip"
probe reject "$WORK/changed-mac.zip"
python3 - "$WORK/from-zip4j-unicode.zip" "$WORK/truncated.zip" <<'PY'
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
echo "PASS B5.1: ASCII 7-Zip + UTF-8 pyzipper bidirectional AES-256, wrong password, MAC, truncation and weak-format rejects"
echo "LIMITATION: 7-Zip with non-ASCII passwords is a separately observed compatibility property, not a passed cross-platform gate."
