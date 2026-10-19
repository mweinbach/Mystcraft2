#!/usr/bin/env python3
"""Port building resources from the checked-in JurassiCraft assets."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def copy(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

def recipe(name, pattern, key, count):
    write(DATA / 'recipes' / (name + '.json'), {'type': 'minecraft:crafting_shaped',
          'pattern': pattern, 'key': key, 'result': {'item': 'jurassicraft:' + name, 'count': count}})

def loot(name, silk):
    pool = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jurassicraft:' + name}]}
    if silk:
        pool['conditions'] = [{'condition': 'minecraft:match_tool', 'predicate': {'enchantments': [
            {'enchantment': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}]
    write(DATA / 'loot_tables/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [pool]})

for family in ['clear_glass', 'reinforced_glass']:
    # The original 64 models encode frame connections in every direction.
    state = json.loads((UP / 'blockstates' / (family + '.json')).read_text())
    for variant in state['variants'].values():
        variant['model'] = variant['model'].replace('jurassicraft:', 'jurassicraft:block/', 1)
    write(ASSETS / 'blockstates' / (family + '.json'), state)
    for source in (UP / 'models/block' / family).glob('*.json'):
        model = json.loads(source.read_text())
        model['render_type'] = 'minecraft:translucent'
        write(ASSETS / 'models/block' / family / source.name, model)
    for source in (UP / 'textures/blocks' / family).glob('*.png'):
        copy(source, ASSETS / 'textures/blocks' / family / source.name)
    write(ASSETS / 'models/item' / (family + '.json'), {'parent': 'jurassicraft:block/' + family + '/' + family})
    base = 'minecraft:glass' if family == 'clear_glass' else 'jurassicraft:clear_glass'
    recipe(family, ['AAA', 'ABA', 'AAA'], {'A': {'item': base}, 'B': {'item': 'minecraft:iron_ingot'}}, 8)
    loot(family, True)

    pane = family + '_pane'
    # Vanilla pane geometry supplies collision, connections and waterlogging.
    variants = [('post', None, None, 0), ('side', 'north', True, 0), ('side', 'east', True, 90),
                ('side_alt', 'south', True, 0), ('side_alt', 'west', True, 90),
                ('noside', 'north', False, 0), ('noside_alt', 'east', False, 0),
                ('noside_alt', 'south', False, 90), ('noside', 'west', False, 270)]
    parts = []
    for suffix, direction, value, rotation in variants:
        apply = {'model': 'jurassicraft:block/' + pane + '_' + suffix}
        if rotation:
            apply['y'] = rotation
        part = {'apply': apply}
        if direction:
            part['when'] = {direction: str(value).lower()}
        parts.append(part)
    write(ASSETS / 'blockstates' / (pane + '.json'), {'multipart': parts})
    for suffix in ['post', 'side', 'side_alt', 'noside', 'noside_alt']:
        write(ASSETS / 'models/block' / (pane + '_' + suffix + '.json'), {
            'parent': 'minecraft:block/template_glass_pane_' + suffix, 'render_type': 'minecraft:translucent',
            'textures': {'pane': 'jurassicraft:blocks/' + family + '/' + family,
                         'edge': 'jurassicraft:blocks/' + family + '/' + family + '_frame'}})
    write(ASSETS / 'models/item' / (pane + '.json'), {'parent': 'minecraft:item/generated',
          'textures': {'layer0': 'jurassicraft:blocks/' + family + '/' + family}})
    recipe(pane, ['GGG', 'GGG'], {'G': {'item': 'jurassicraft:' + family}}, 16)
    loot(pane, False)

lang = {
    'block.jurassicraft.clear_glass': 'Clear Glass',
    'block.jurassicraft.clear_glass_pane': 'Clear Glass Pane',
    'block.jurassicraft.reinforced_glass': 'Reinforced Glass',
    'block.jurassicraft.reinforced_glass_pane': 'Reinforced Glass Pane',
    'item.jurassicraft.attraction_sign': 'Attraction Sign',
    'item.jurassicraft.paddock_sign': 'Paddock Sign',
    'item.jurassicraft.sign_with_artwork': '%s: %s',
}

attractions = ['aquarium', 'aquarium_coral', 'aviary', 'aviary_plants', 'gallimimus_valley',
               'gallimimus_valley_plants', 'gentle_giants', 'gentle_giants_plants', 'raptor_paddock',
               'raptor_paddock_plants', 'safari', 'safari_plants', 'triceratops_territorium', 'garden',
               'garden_plants', 'triceratops_territorium_plants', 'tyrannosaurus_kingdom', 'tyrannosaurus_kingdom_plants']
paddocks = ['velociraptor', 'coelacanth', 'microraptor', 'brachiosaurus', 'mussaurus', 'dilophosaurus',
            'gallimimus', 'parasaurolophus', 'triceratops', 'tyrannosaurus']
for art in attractions:
    for suffix in ['', '_popout']:
        copy(UP / 'textures/attraction_sign' / (art + suffix + '.png'), ASSETS / 'textures/attraction_sign' / (art + suffix + '.png'))
    icon = 'attraction_sign_' + art
    copy(UP / 'textures/items' / (icon + '.png'), ASSETS / 'textures/items' / (icon + '.png'))
    write(ASSETS / 'models/item' / (icon + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/' + icon}})
    lang['jurassicraft.sign.' + art] = art.replace('_', ' ').title()
for art in paddocks:
    copy(UP / 'textures/paddock' / (art + '.png'), ASSETS / 'textures/paddock' / (art + '.png'))
    lang['jurassicraft.sign.' + art] = art.replace('_', ' ').title()
copy(UP / 'textures/items/paddock_sign.png', ASSETS / 'textures/items/paddock_sign.png')
write(ASSETS / 'models/item/paddock_sign.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/paddock_sign'}})
write(ASSETS / 'models/item/attraction_sign.json', {'parent': 'jurassicraft:item/attraction_sign_aquarium',
      'overrides': [{'predicate': {'jurassicraft:sign_artwork': i}, 'model': 'jurassicraft:item/attraction_sign_' + art}
                    for i, art in enumerate(attractions) if i]})
recipe('attraction_sign', ['AAA', 'BBB', 'AAA'], {'A': {'item': 'minecraft:iron_ingot'}, 'B': {'tag': 'minecraft:signs'}}, 1)
recipe('paddock_sign', ['AAA', 'BBB', 'AAA'], {'A': {'tag': 'forge:nuggets/iron'}, 'B': {'tag': 'minecraft:signs'}}, 1)
write(DATA / 'recipes/attraction_sign_cycle.json', {'type': 'jurassicraft:attraction_sign_cycle'})
write(ROOT / 'tools/lang/decor_en_us.json', lang)
