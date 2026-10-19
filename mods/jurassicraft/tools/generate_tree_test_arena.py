#!/usr/bin/env python3
"""Reserve space for whole prehistoric trees in GameTests."""
import gzip
from pathlib import Path
import struct

root = Path(__file__).resolve().parents[1]
structures = root / 'src/main/resources/data/jurassicraft/structures'
empty = gzip.decompress((structures / 'assembly_test.nbt').read_bytes())
key = b'\x09\x00\x04size\x03\x00\x00\x00\x03'
index = empty.index(key) + len(key)
arena = empty[:index] + struct.pack('>iii', 23, 36, 23) + empty[index + 12:]
(structures / 'tree_growth_test.nbt').write_bytes(gzip.compress(arena, mtime=0))
