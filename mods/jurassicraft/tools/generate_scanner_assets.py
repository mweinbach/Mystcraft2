#!/usr/bin/env python3
"""Generate the standalone debug scanner resources using its upstream artwork."""
from pathlib import Path
import json
import shutil

root = Path(__file__).resolve().parents[1]
assets = root / "src/main/resources/assets/jurassicraft"
data = root / "src/main/resources/data/jurassicraft/recipes"

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf8")

texture = assets / "textures/items/dino_scanner.png"
texture.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(root / "upstream/src/main/resources/assets/jurassicraft/textures/items/dino_scanner.png", texture)
write(assets / "models/item/dino_scanner.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "jurassicraft:items/dino_scanner"}
})
write(data / "dino_scanner.json", {
    "type": "minecraft:crafting_shaped", "pattern": [" I ", "CAC", " R "],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "C": {"item": "jurassicraft:basic_circuit"},
            "A": {"item": "jurassicraft:dna_analyzer"}, "R": {"item": "minecraft:redstone"}},
    "result": {"item": "jurassicraft:dino_scanner"}
})
write(root / "tools/scanner/en_us.fragment.json", {
    "item.jurassicraft.dino_scanner": "Debug Dinosaur Scanner",
    "tooltip.jurassicraft.dino_scanner.scan": "Use on a dinosaur to inspect and copy its diagnostics.",
    "tooltip.jurassicraft.dino_scanner.sneak": "Sneak-use reduces its energy by up to 5,000.",
    "message.jurassicraft.dino_scanner.header": "%s diagnostics",
    "message.jurassicraft.dino_scanner.health": "Health: %s / %s",
    "message.jurassicraft.dino_scanner.growth": "Growth: %s%%, %s",
    "message.jurassicraft.dino_scanner.male": "male",
    "message.jurassicraft.dino_scanner.female": "female",
    "message.jurassicraft.dino_scanner.energy": "Energy: %s / %s",
    "message.jurassicraft.dino_scanner.water": "Water: %s / %s",
    "message.jurassicraft.dino_scanner.dna": "DNA: %s%%, genome %s",
    "message.jurassicraft.dino_scanner.drained": "Debug action: energy reduced by up to %s.",
    "message.jurassicraft.dino_scanner.copy": "[Copy diagnostics]",
    "message.jurassicraft.dino_scanner.copy_hover": "Copy this diagnostic report to the clipboard"
})
