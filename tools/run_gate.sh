#!/usr/bin/env bash
set -euo pipefail
NAME="$1"; shift
[[ "$NAME" =~ ^[a-z0-9-]+$ ]] || exit 2
mkdir -p ci-out/logs ci-out/status
START="$(date +%s)"
set +e
"$@" 2>&1 | tee "ci-out/logs/$NAME.log"
STATUS=${PIPESTATUS[0]}
set -e
python3 - "$NAME" "$STATUS" "$START" "$@" <<'STATUS'
import json, pathlib, sys, time
name, code, start, *command = sys.argv[1:]
pathlib.Path(f'ci-out/status/{name}.json').write_text(json.dumps({
    'gate': name, 'exit_code': int(code), 'duration_seconds': int(time.time())-int(start),
    'command': command, 'conclusion': 'success' if code == '0' else 'failure'
}, ensure_ascii=False, indent=2)+'\n')
STATUS
exit "$STATUS"
