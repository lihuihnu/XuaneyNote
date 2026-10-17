#!/usr/bin/env bash
# 首轮无二进制引导提交：下载并验证官方完整发行包，不冒充 Gradle Wrapper。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION=8.13
SHA=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
BOOTSTRAP="${XUANEY_BOOTSTRAP_DIR:-$ROOT/.bootstrap}"
mkdir -p "$BOOTSTRAP"
if [[ ! -x "$BOOTSTRAP/gradle-$VERSION/bin/gradle" ]]; then
    TMP="$(mktemp -d "$BOOTSTRAP/download.XXXXXX")"
    trap 'rm -rf "$TMP"' EXIT
    curl --fail --location --retry 3 --connect-timeout 20 --max-time 600 \
        "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$TMP/gradle.zip"
    python3 - "$TMP/gradle.zip" "$SHA" <<'CHECK'
import hashlib, pathlib, sys
assert hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest() == sys.argv[2], 'Gradle checksum mismatch'
CHECK
    unzip -q "$TMP/gradle.zip" -d "$TMP"
    mv "$TMP/gradle-$VERSION" "$BOOTSTRAP/"
    rm -rf "$TMP"
    trap - EXIT
fi
cd "$ROOT"
exec "$BOOTSTRAP/gradle-$VERSION/bin/gradle" "$@"
