#!/usr/bin/env python3
"""Convert the cleaning station's Tabula geometry and copy its resources."""
import json
from pathlib import Path
import shutil
from convert_dna_machine_models import convert, MODELS

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT / "upstream/src/main/resources/assets/jurassicraft"
ASSETS = ROOT / "src/main/resources/assets/jurassicraft"
DATA = ROOT / "src/main/resources/data/jurassicraft"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


convert("cleaning_station", *MODELS["cleaning_station"])
write(ASSETS / "models/item/cleaning_station.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "jurassicraft:items/cleaning_station"}})
write(ASSETS / "blockstates/cleaning_station.json", {"variants": {
    "facing=north": {"model": "jurassicraft:block/cleaning_station"},
    "facing=east": {"model": "jurassicraft:block/cleaning_station", "y": 90},
    "facing=south": {"model": "jurassicraft:block/cleaning_station", "y": 180},
    "facing=west": {"model": "jurassicraft:block/cleaning_station", "y": 270},
}})
write(DATA / "recipes/cleaning_station.json", {
    "type": "minecraft:crafting_shaped", "pattern": ["AAA", "BCB", "DED"],
    "key": {"A": {"item": "minecraft:iron_ingot"}, "B": {"item": "minecraft:redstone"},
            "C": {"item": "minecraft:glass_pane"}, "D": {"item": "minecraft:iron_block"}, "E": {"item": "minecraft:bucket"}},
    "result": {"item": "jurassicraft:cleaning_station"},
})
write(DATA / "loot_tables/blocks/cleaning_station.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jurassicraft:cleaning_station"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}],
})
for kind in ("blocks", "items", "gui"):
    dest = ASSETS / "textures" / kind / "cleaning_station.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP / "textures" / kind / "cleaning_station.png", dest)
