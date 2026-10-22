#!/usr/bin/env python3
"""Generate the separate plant fossil and petrified tree worldgen data."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


for feature in ('petrified_tree', 'coal_plant_fossils'):
    write(DATA / 'worldgen/configured_feature' / (feature + '.json'),
          {'type': 'jurassicraft:' + feature, 'config': {}})
    write(DATA / 'worldgen/placed_feature' / (feature + '.json'), {
        'feature': 'jurassicraft:' + feature,
        'placement': [
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'},
            {'type': 'minecraft:biome'},
        ],
    })
    write(DATA / 'forge/biome_modifier' / (feature + '.json'), {
        'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld',
        # Scan after the ore stage so other mods' normal ore features have run too.
        'features': 'jurassicraft:' + feature,
        'step': 'top_layer_modification' if feature == 'coal_plant_fossils' else 'underground_ores',
    })

write(DATA / 'tags/blocks/petrified_tree_replaceable.json', {
    'replace': False,
    'values': ['#minecraft:base_stone_overworld', '#minecraft:dirt', '#minecraft:sand',
               '#minecraft:terracotta', 'minecraft:gravel', 'minecraft:clay',
               'minecraft:sandstone', 'minecraft:red_sandstone'],
})
