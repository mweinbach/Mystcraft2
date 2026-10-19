#!/usr/bin/env python3
"""Generate assets and recipes for the dart gun and its ammunition."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft/recipes'
UP = ROOT / 'upstream/src/main/resources/assets/jurassicraft/textures/items'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


for texture in ('dart_base', 'dart_overlay', 'dart_gun'):
    dest = ASSETS / f'textures/items/{texture}.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP / f'{texture}.png', dest)
for item in ('dart_tranquilizer', 'dart_poison_cycasin', 'dart_poison_executioner_concoction', 'dart_tipped_potion'):
    write(ASSETS / f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {
        'layer0': 'jurassicraft:items/dart_base', 'layer1': 'jurassicraft:items/dart_overlay'}})
write(ASSETS / 'models/item/dart_gun.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/dart_gun'}})
write(DATA / 'dart_tranquilizer.json', {'type': 'minecraft:crafting_shaped', 'pattern': [' F', 'N '],
    'key': {'F': {'item': 'minecraft:flint'}, 'N': {'tag': 'forge:nuggets/iron'}},
    'result': {'item': 'jurassicraft:dart_tranquilizer', 'count': 6}})
for recipe, source, result in (
    ('dart_poison', 'dart_tranquilizer', 'dart_poison_cycasin'),
    ('dart_lethal', 'dart_poison_cycasin', 'dart_poison_executioner_concoction'),
):
    write(DATA / f'{recipe}.json', {'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'item': f'jurassicraft:{source}'}, {'item': 'minecraft:spider_eye'}],
        'result': {'item': f'jurassicraft:{result}'}})
write(DATA / 'dart_tipped_potion.json', {'type': 'jurassicraft:dart_tipped_potion'})
# The upstream gun was creative-only; the port supplies a survival recipe.
write(DATA / 'dart_gun.json', {'type': 'minecraft:crafting_shaped', 'pattern': [' II', 'LCI', ' I '],
    'key': {'I': {'tag': 'forge:ingots/iron'}, 'L': {'item': 'minecraft:lever'},
            'C': {'item': 'jurassicraft:basic_circuit'}}, 'result': {'item': 'jurassicraft:dart_gun'}})
write(ROOT / 'tools/lang/darts.json', {'item.jurassicraft.dart_tranquilizer': 'Tranquilizer Dart',
    'item.jurassicraft.dart_poison_cycasin': 'Poison Dart',
    'item.jurassicraft.dart_poison_executioner_concoction': 'Lethal Dart',
    'item.jurassicraft.dart_tipped_potion': 'Potion Dart',
    'item.jurassicraft.dart_gun': 'Dart Gun', 'entity.jurassicraft.dart': 'Dart',
    'tooltip.jurassicraft.dart_gun': 'Use to load darts, then use again to fire.'})
