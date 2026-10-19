#!/usr/bin/env python3
"""Port original vehicle model geometry, item textures, and component recipes."""
import json
import gzip
import shutil
import struct
from pathlib import Path
from import_dinosaur_models import geometry, model, roots

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT/'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT/'src/main/resources/assets/jurassicraft'
DATA = ROOT/'src/main/resources/data/jurassicraft'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def copy(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)


def named_geometry(c):
    result = geometry(c)
    # Keep Tabula names for the original wheel, door, and rotor pivots.
    if c.get('name'):
        result['name'] = c['name']
    result['children'] = [named_geometry(child) for child in c.get('children', [])]
    return result


def vehicle(name):
    source = model(OLD/f'models/entities/{name}/{name}.tbl')
    output = {'textureWidth': source['textureWidth'], 'textureHeight': source['textureHeight'],
              'scale': source.get('scale', [1,1,1]), 'cubes': [named_geometry(c) for c in roots(source)]}
    write(ASSETS/f'models/entity_port/{name}.json', output)
    copy(OLD/f'textures/entities/{name}/{name}.png', ASSETS/f'textures/entities/{name}/{name}.png')


vehicles = ('jeep_wrangler', 'ford_explorer', 'helicopter')
items = vehicles + ('car_chassis', 'car_seats', 'car_windscreen', 'engine_system', 'car_tire', 'unfinished_car')
for name in vehicles:
    vehicle(name)
for texture in (OLD/'textures/entities/helicopter/hud').glob('*.png'):
    if texture.name != 'tmp.png':
        copy(texture, ASSETS/f'textures/entities/helicopter/hud/{texture.name}')
for name in items:
    copy(OLD/f'textures/items/{name}.png', ASSETS/f'textures/items/{name}.png')
    write(ASSETS/f'models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jurassicraft:items/{name}'}})

# The 1.12 dye and wool metadata values name distinct modern items.
dyes = {0:'black_dye', 1:'red_dye', 2:'green_dye', 7:'light_gray_dye', 10:'lime_dye', 11:'yellow_dye'}
for name in ('car_chassis', 'car_seats', 'car_windscreen', 'engine_system', 'engine_system_1', 'car_tire',
             'unfinished_car', 'unfinished_car_1', 'jeep_wrangler', 'jeep_wrangler_1',
             'ford_explorer', 'ford_explorer_1', 'helicopter'):
    recipe = json.loads((OLD/f'recipes/{name}.json').read_text())
    for ingredient in recipe['key'].values():
        if ingredient['item'] == 'minecraft:dye': ingredient['item'] = 'minecraft:' + dyes[ingredient['data']]
        if ingredient['item'] == 'minecraft:wool': ingredient['item'] = 'minecraft:white_wool'
        ingredient.pop('data', None)
    result_variant = recipe['result'].pop('data', None)
    if recipe['result']['item'] == 'jurassicraft:vehicle_item':
        recipe['result']['item'] = 'jurassicraft:' + {0:'ford_explorer', 1:'jeep_wrangler', 2:'helicopter'}[result_variant]
    write(DATA/f'recipes/{name}.json', recipe)
write(ROOT/'tools/lang/vehicles.json', {
    'entity.jurassicraft.jeep_wrangler': 'Jeep Wrangler',
    'item.jurassicraft.jeep_wrangler': 'Jeep Wrangler',
    'entity.jurassicraft.ford_explorer': 'Ford Explorer',
    'item.jurassicraft.ford_explorer': 'Ford Explorer',
    'entity.jurassicraft.helicopter': 'Transport Helicopter',
    'item.jurassicraft.helicopter': 'Transport Helicopter',
    'item.jurassicraft.car_chassis': 'Car Chassis',
    'item.jurassicraft.car_seats': 'Car Seats',
    'item.jurassicraft.car_windscreen': 'Car Windscreen',
    'item.jurassicraft.engine_system': 'Engine System',
    'item.jurassicraft.car_tire': 'Car Tire',
    'item.jurassicraft.unfinished_car': 'Unfinished Car',
    'tooltip.jurassicraft.vehicle_drive': 'Mount to drive with the movement keys; sneak to dismount',
    'tooltip.jurassicraft.helicopter_altitude': 'Jump to ascend; sprint to descend',
})

# Empty 15x6x15 test arena, derived from the existing empty structure template.
empty = gzip.decompress((DATA/'structures/assembly_test.nbt').read_bytes())
key = b'\x09\x00\x04size\x03\x00\x00\x00\x03'
index = empty.index(key) + len(key)
arena = empty[:index] + struct.pack('>iii', 15, 6, 15) + empty[index + 12:]
dest = DATA/'structures/vehicle_test.nbt'
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_bytes(gzip.compress(arena, mtime=0))

# Shared sounds.json is assembled from fragments by the port generator.
for sound in ('car_move', 'helicopter_warning'):
    copy(OLD/f'sounds/{sound}.ogg', ASSETS/f'sounds/{sound}.ogg')
write(ROOT/'tools/sounds/vehicles.json', {
    'car_move': {'sounds': ['jurassicraft:car_move']},
    'helicopter_warning': {'sounds': ['jurassicraft:helicopter_warning']},
})
