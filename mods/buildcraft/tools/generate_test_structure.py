#!/usr/bin/env python3
"""Writes the GameTest arena shared by the Forge and Fabric test runs: a stone floor under open air.

Forge loads the binary ``data/buildcraft/structures/arena.nbt``; Fabric's GameTest API only reads
SNBT from ``data/buildcraft/gametest/structures/arena.snbt``. Both describe the same blocks. Run
after changing the size:

    python3 mods/buildcraft/tools/generate_test_structure.py
"""

from __future__ import annotations

import gzip
import struct
from pathlib import Path

SIZE = (9, 8, 9)
DATA_VERSION = 3120  # Minecraft 1.19.2
SRC = Path(__file__).resolve().parents[1] / "src"
OUTPUT = SRC / "gametestCommon" / "resources" / "data" / "buildcraft" / "structures" / "arena.nbt"
SNBT_OUTPUT = SRC / "gametestFabric" / "resources" / "data" / "buildcraft" / "gametest" / "structures" / "arena.snbt"

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


def block(x: int, y: int) -> str:
    return "minecraft:stone" if y == 0 else "minecraft:air"


def snbt() -> str:
    data = [f'{{pos: [{x}, {y}, {z}], state: "{block(x, y)}"}}'
            for x in range(SIZE[0]) for y in range(SIZE[1]) for z in range(SIZE[2])]
    return ("{\n"
            f"    DataVersion: {DATA_VERSION},\n"
            f"    size: [{SIZE[0]}, {SIZE[1]}, {SIZE[2]}],\n"
            "    data: [\n        " + ",\n        ".join(data) + "\n    ],\n"
            "    entities: [],\n"
            '    palette: [\n        "minecraft:air",\n        "minecraft:stone"\n    ]\n'
            "}\n")


def main() -> None:
    palette = [compound([named(TAG_STRING, "Name", string("minecraft:air"))]),
               compound([named(TAG_STRING, "Name", string("minecraft:stone"))])]
    blocks = []
    for x in range(SIZE[0]):
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                blocks.append(compound([
                    named(TAG_LIST, "pos", int_list([x, y, z])),
                    named(TAG_INT, "state", struct.pack(">i", 1 if block(x, y) == "minecraft:stone" else 0)),
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
    SNBT_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    SNBT_OUTPUT.write_text(snbt(), encoding="utf-8")


if __name__ == "__main__":
    main()
