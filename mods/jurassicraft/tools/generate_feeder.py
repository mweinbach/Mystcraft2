#!/usr/bin/env python3
"""Convert the feeder's static upstream Tabula boxes and copy GUI textures."""
import json
from pathlib import Path
import shutil
from convert_dna_machine_models import convert, MODELS

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


convert('feeder', *MODELS['feeder'])
write(ASSETS/'models/item/feeder.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/feeder'}})
write(ASSETS/'blockstates/feeder.json', {'variants': {
    'facing=up': {'model': 'jurassicraft:block/feeder'},
    'facing=down': {'model': 'jurassicraft:block/feeder', 'x': 180},
    'facing=north': {'model': 'jurassicraft:block/feeder', 'x': 90},
    'facing=east': {'model': 'jurassicraft:block/feeder', 'x': 90, 'y': 90},
    'facing=south': {'model': 'jurassicraft:block/feeder', 'x': 90, 'y': 180},
    'facing=west': {'model': 'jurassicraft:block/feeder', 'x': 90, 'y': 270},
}})
write(DATA/'recipes/feeder.json', {
    'type': 'minecraft:crafting_shaped', 'pattern': ['ABA', 'CDC', 'EEE'],
    'key': {'A': {'item': 'minecraft:iron_trapdoor'}, 'B': {'item': 'minecraft:dispenser'},
            'C': {'item': 'minecraft:iron_ingot'}, 'D': {'item': 'minecraft:chest'}, 'E': {'item': 'minecraft:cobblestone'}},
    'result': {'item': 'jurassicraft:feeder'},
})
write(DATA/'loot_tables/blocks/feeder.json', {
    'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jurassicraft:feeder'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
})
write(DATA/'tags/items/feeder_meat.json', {'replace': False, 'values': [
    'minecraft:cod', 'minecraft:cooked_cod', 'minecraft:salmon', 'minecraft:cooked_salmon',
    'minecraft:tropical_fish', 'minecraft:pufferfish',
    'jurassicraft:crickets', 'jurassicraft:cockroaches', 'jurassicraft:mealworm_beetles',
    'jurassicraft:krill', 'jurassicraft:plankton',
]})
write(DATA/'tags/items/feeder_plants.json', {'replace': False, 'values': [
    '#minecraft:leaves', '#minecraft:saplings', '#minecraft:flowers', '#minecraft:tall_flowers',
    '#jurassicraft:ancient_feeder_plants',
    'minecraft:wheat', 'minecraft:wheat_seeds', 'minecraft:beetroot_seeds', 'minecraft:melon_seeds',
    'minecraft:pumpkin_seeds', 'minecraft:grass', 'minecraft:fern', 'minecraft:large_fern',
    'minecraft:sugar_cane', 'minecraft:pumpkin', 'minecraft:melon', 'minecraft:hay_block',
    'minecraft:lily_pad', 'minecraft:brown_mushroom', 'minecraft:red_mushroom',
]})
for kind in ('blocks', 'items', 'gui'):
    dest = ASSETS/'textures'/kind/'feeder.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP/'textures'/kind/'feeder.png', dest)
