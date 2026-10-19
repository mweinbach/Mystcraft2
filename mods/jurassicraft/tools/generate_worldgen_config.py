#!/usr/bin/env python3
"""Generate the category filter and separate natural trackway feature."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


write(DATA / 'forge/biome_modifier/worldgen_filter.json', {
    'type': 'jurassicraft:worldgen_filter',
})
write(DATA / 'worldgen/configured_feature/fossil_trackway.json', {
    'type': 'jurassicraft:fossil_trackway', 'config': {},
})
write(DATA / 'worldgen/placed_feature/fossil_trackway.json', {
    'feature': 'jurassicraft:fossil_trackway',
    'placement': [
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
         'min_inclusive': {'absolute': 30}, 'max_inclusive': {'absolute': 49}}},
        {'type': 'minecraft:biome'},
    ],
})
write(DATA / 'forge/biome_modifier/fossil_trackway.json', {
    'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld',
    'features': 'jurassicraft:fossil_trackway', 'step': 'underground_ores',
})
