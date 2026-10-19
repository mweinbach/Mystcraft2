#!/usr/bin/env python3
"""Convert original goat geometry, textures, sounds and recipes."""
import json
import shutil
from pathlib import Path
from import_dinosaur_models import model, roots, geometry, transforms

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def write(path, obj, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=None if compact else 2) + '\n')


def copy(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)


for kind in ('billy', 'nanny', 'kid'):
    name = 'goat_' + kind
    source = OLD / 'models/entities' / name / 'adult'
    animations = json.loads((source / (name + '_adult.json')).read_text())['poses']
    idle_name = animations['IDLE']['variants']['0'][0]['pose']
    idle = model(source / (idle_name + '.tbl'))
    result = {'textureWidth': idle['textureWidth'], 'textureHeight': idle['textureHeight'],
              'scale': idle.get('scale', [1, 1, 1]), 'cubes': [geometry(c) for c in roots(idle)], 'animations': {}}
    ids = set()
    for cube in roots(idle):
        values = {}
        transforms(cube, values)
        ids.update(values)
    for animation in ('IDLE', 'WALKING', 'RUNNING', 'EATING', 'SPEAK', 'PECKING'):
        frames = animations.get(animation, {}).get('variants', {}).get('0', [])
        if not frames:
            continue
        converted = []
        for frame in frames:
            values = {}
            for cube in roots(model(source / (frame['pose'] + '.tbl'))):
                transforms(cube, values)
            converted.append({'time': frame['time'], 'parts': {key: value for key, value in values.items() if key in ids}})
        result['animations'][animation] = converted
    write(ASSETS / 'models/entity_port' / (name + '.json'), result, True)
    for variant in ('jurassic_world', 'jurassic_park', 'jpog'):
        filename = f'{kind}_{variant}.png'
        copy(OLD / 'textures/entities/goat' / filename, ASSETS / 'textures/entities/goat' / filename)

for item in ('goat_raw', 'goat_cooked'):
    copy(OLD / 'textures/items' / (item + '.png'), ASSETS / 'textures/items' / (item + '.png'))
    write(ASSETS / 'models/item' / (item + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/' + item}})
write(ASSETS / 'models/item/goat_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})

for kind in ('smelting', 'smoking', 'campfire_cooking'):
    write(DATA / 'recipes' / ('goat_cooked_' + kind + '.json'), {'type': 'minecraft:' + kind,
          'ingredient': {'item': 'jurassicraft:goat_raw'}, 'result': 'jurassicraft:goat_cooked',
          'experience': 0.5, 'cookingtime': 100 if kind == 'smoking' else 600 if kind == 'campfire_cooking' else 200})

fire = {'condition': 'minecraft:entity_properties', 'entity': 'this', 'predicate': {'flags': {'is_on_fire': True}}}
def item(name, *functions):
    return {'type': 'minecraft:item', 'name': name, 'functions': list(functions)}
count = {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}}
write(DATA / 'loot_tables/entities/goat.json', {'type': 'minecraft:entity', 'pools': [
    {'rolls': 1, 'entries': [item('minecraft:leather', count)]},
    {'rolls': 1, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': .5}],
     'entries': [item('minecraft:white_wool'), item('minecraft:brown_wool')]},
    {'rolls': 1, 'entries': [item('jurassicraft:goat_raw', count,
                                 {'function': 'minecraft:furnace_smelt', 'conditions': [fire]})]},
]})
write(DATA / 'forge/biome_modifier/goat_spawns.json', {'type': 'jurassicraft:goat_spawns'})
write(DATA / 'tags/items/goat_food.json', {'replace': False, 'values': ['#jurassicraft:feeder_plants',
       'minecraft:carrot', 'minecraft:potato', 'minecraft:beetroot', 'minecraft:apple', 'minecraft:melon_slice']})

sounds = {}
for name, glob in [('goat_living', 'goat_living_*.ogg'), ('goat_hurt', 'goat_hurt_*.ogg'), ('goat_death', 'goat_death.ogg')]:
    files = sorted((OLD / 'sounds/entity/goat').glob(glob))
    sounds[name] = {'sounds': []}
    for source in files:
        copy(source, ASSETS / 'sounds/entity/goat' / source.name)
        sounds[name]['sounds'].append('jurassicraft:entity/goat/' + source.stem)
write(ROOT / 'tools/sounds/goat.json', sounds)
write(ROOT / 'tools/lang/goat.json', {'entity.jurassicraft.goat': 'Goat', 'item.jurassicraft.goat_spawn_egg': 'Goat Spawn Egg',
      'item.jurassicraft.goat_raw': 'Raw Goat', 'item.jurassicraft.goat_cooked': 'Cooked Goat'})
