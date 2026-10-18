#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

name = sys.argv[1]
assert name in {'phone', 'tablet', 'api26'}
root = Path(__file__).resolve().parents[1]
for module in ('app', 'core/data'):
    source = root / module / 'build/outputs/androidTest-results'
    for path in source.glob('**/*.xml'):
        target = root / 'ci-out/reports' / name / module / path.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
