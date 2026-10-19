#!/usr/bin/env python3
"""Merge independently generated sound definitions into the mod manifest."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def merge():
    merged = {}
    for fragment in sorted((ROOT / 'tools/sounds').glob('*.json')):
        for event, spec in json.loads(fragment.read_text(encoding='utf-8')).items():
            if event in merged and merged[event] != spec:
                raise ValueError(f'Conflicting sound event {event} in {fragment}')
            merged[event] = spec
    (ROOT / 'src/main/resources/assets/jurassicraft/sounds.json').write_text(
        json.dumps(merged, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    merge()
