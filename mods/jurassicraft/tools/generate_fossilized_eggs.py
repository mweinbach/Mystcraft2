#!/usr/bin/env python3
"""Copy the three upstream fossilized egg textures and write 1.19.2 models.

Translations live in tools/lang/fossilized_eggs.json for the project's language
merge. This script deliberately does not modify the shared language file.
"""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/jurassicraft"
UPSTREAM_TEXTURES = ROOT / "upstream/src/main/resources/assets/jurassicraft/textures/items"

for index in range(1, 4):
    name = "fossilized_egg" if index == 1 else f"fossilized_egg_{index}"
    texture = f"fossilized_egg_{index}"
    destination = ASSETS / f"textures/items/{texture}.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UPSTREAM_TEXTURES / f"{texture}.png", destination)
    model = ASSETS / f"models/item/{name}.json"
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"jurassicraft:items/{texture}"},
    }, indent=2) + "\n", encoding="utf-8")
