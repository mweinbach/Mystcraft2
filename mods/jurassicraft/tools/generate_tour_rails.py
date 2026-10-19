#!/usr/bin/env python3
"""Import the original tour-track meshes/textures and write 1.19 recipes and assets."""
import json
import shutil
import struct
import zlib
from pathlib import Path
from import_dinosaur_models import model, roots, geometry

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'
FAMILIES = ('straight', 'corner', 'slope', 'diagonal', 'diagonal_turn_left', 'diagonal_turn_right')
NAMES = ('tour_rail', 'tour_rail_slow', 'tour_rail_medium', 'tour_rail_fast')


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def marker(path, rgb):
    # A transparent 16x16 speed marker layered over the vanilla rail item icon.
    rows = bytearray()
    for y in range(16):
        rows.append(0)
        for x in range(16):
            rows.extend((*rgb, 255) if 6 <= x <= 9 and 6 <= y <= 9 else (0, 0, 0, 0))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(bytes(rows))) + chunk(b'IEND', b''))


def main():
    for family in FAMILIES:
        name = 'tour_rail_' + family
        source = model(OLD / 'models/block' / (name + '.tbl'))
        output = {'textureWidth': source['textureWidth'], 'textureHeight': source['textureHeight'],
                  'scale': source.get('scale', [1, 1, 1]), 'cubes': [geometry(c) for c in roots(source)]}
        write_json(ASSETS / 'models/entity_port' / (name + '.json'), output)
        for suffix in ('', '_stripe'):
            path = ASSETS / 'textures/blocks' / (name + suffix + '.png')
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(OLD / 'textures/blocks' / path.name, path)
    for i, name in enumerate(NAMES):
        write_json(ASSETS / 'blockstates' / (name + '.json'), {'variants': {'': {'model': 'minecraft:block/air'}}})
        textures = {'layer0': 'minecraft:block/rail'}
        if i:
            textures['layer1'] = 'jurassicraft:item_port/' + name + '_marker'
            marker(ASSETS / 'textures/item_port' / (name + '_marker.png'), ((168, 0, 0), (255, 118, 0), (0, 168, 0))[i - 1])
        write_json(ASSETS / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': textures})
        write_json(DATA / 'loot_tables/blocks' / (name + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jurassicraft:' + name}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write_json(DATA / 'recipes/tour_rails.json', {
        'type': 'minecraft:crafting_shaped', 'group': 'jurassicraft:tour_rail',
        'pattern': ['BRB', 'TRT', 'BRB'], 'key': {'B': {'item': 'minecraft:iron_bars'},
        'T': {'tag': 'forge:dusts/redstone'}, 'R': {'item': 'minecraft:rail'}},
        'result': {'item': 'jurassicraft:tour_rail', 'count': 16}})
    for i, target in enumerate((*NAMES[1:], NAMES[0])):
        name = ('tour_rails_slow', 'tour_rails_medium', 'tour_rails_fast', 'tour_rails_loop')[i]
        write_json(DATA / 'recipes' / (name + '.json'), {
            'type': 'minecraft:crafting_shapeless', 'group': 'jurassicraft:tour_rails_loop',
            'ingredients': [{'item': 'jurassicraft:' + NAMES[i]}], 'result': {'item': 'jurassicraft:' + target}})
    write_json(ROOT / 'tools/lang/tour_rails.json', {
        'block.jurassicraft.tour_rail': 'Tour Vehicle Track',
        'block.jurassicraft.tour_rail_slow': 'Slow Tour Vehicle Track',
        'block.jurassicraft.tour_rail_medium': 'Medium Tour Vehicle Track',
        'block.jurassicraft.tour_rail_fast': 'Fast Tour Vehicle Track'})


if __name__ == '__main__':
    main()
