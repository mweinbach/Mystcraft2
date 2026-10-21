#!/usr/bin/env python3
"""Point upstream Mekanism's Forge API references at the port's loader-neutral mirror.

Every ``net.minecraftforge.<name>`` reference in the shared sources becomes ``ports.mekanism.lib.<name>``. The
mirror (``src/main/java/ports/mekanism/lib``) reimplements the subset of the Forge API shapes Mekanism uses, over
both loaders, so upstream code keeps its logic and mostly only its imports change. Run once after importing an
upstream revision, before the hand edits recorded in UPSTREAM.md. Idempotent.
"""
from pathlib import Path
import re
import sys

MOD = Path(__file__).resolve().parents[1]
ROOTS = [MOD / 'src/main/java/mekanism', MOD / 'src/test/java']
FORGE = re.compile(r'\bnet\.minecraftforge\.')


def main():
    changed = 0
    for root in ROOTS:
        for path in sorted(root.rglob('*.java')):
            text = path.read_text(encoding='utf-8')
            rewritten = FORGE.sub('ports.mekanism.lib.', text)
            if rewritten != text:
                path.write_text(rewritten, encoding='utf-8')
                changed += 1
    print(f'rewrote {changed} files')
    return 0


if __name__ == '__main__':
    sys.exit(main())
