#!/usr/bin/env python3
"""Writes the GameTest arena shared by the Forge and Fabric test runs: a stone floor under open air.

Vanilla loads ``data/<namespace>/structures/<name>.nbt`` on both loaders, so one binary template
serves both. Run after changing the size:

    python3 mods/buildcraft/tools/generate_test_structure.py
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

SIZE = (9, 8, 9)
DATA_VERSION = 3120  # Minecraft 1.19.2
OUTPUT = Path(__file__).resolve().parents[1] / "src" / "gametestCommon" / "resources" / "data" / "buildcraft" / "structures" / "arena.nbt"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes([tag]) + string(name) + payload


def compound(entries: list[bytes]) -> bytes:
    return b"".join(entries) + bytes([TAG_END])


def int_list(values: list[int]) -> bytes:
    return bytes([TAG_INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(values: list[bytes]) -> bytes:
    if not values:
        return bytes([TAG_END]) + struct.pack(">i", 0)
    return bytes([TAG_COMPOUND]) + struct.pack(">i", len(values)) + b"".join(values)


def main() -> None:
    palette = [compound([named(TAG_STRING, "Name", string("minecraft:air"))]),
               compound([named(TAG_STRING, "Name", string("minecraft:stone"))])]
    blocks = []
    for x in range(SIZE[0]):
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                blocks.append(compound([
                    named(TAG_LIST, "pos", int_list([x, y, z])),
                    named(TAG_INT, "state", struct.pack(">i", 1 if y == 0 else 0)),
                ]))
    root = compound([
        named(TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION)),
        named(TAG_LIST, "size", int_list(list(SIZE))),
        named(TAG_LIST, "palette", compound_list(palette)),
        named(TAG_LIST, "blocks", compound_list(blocks)),
        named(TAG_LIST, "entities", compound_list([])),
    ])
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(OUTPUT, "wb", mtime=0) as out:
        out.write(named(TAG_COMPOUND, "", root))


if __name__ == "__main__":
    main()
