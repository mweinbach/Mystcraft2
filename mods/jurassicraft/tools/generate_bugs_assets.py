#!/usr/bin/env python3
"""Port original crate, insect and aquatic culture assets to 1.19 resource paths."""
from pathlib import Path
import json
import shutil

root = Path(__file__).resolve().parents[1]
legacy = root / 'upstream/src/main/resources/assets/jurassicraft'
res = root / 'src/main/resources'
assets = res / 'assets/jurassicraft'
data = res / 'data/jurassicraft'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)

for item in ['crickets', 'cockroaches', 'mealworm_beetles', 'fine_net', 'plankton', 'krill']:
    copy(legacy / f'textures/items/{item}.png', assets / f'textures/item/{item}.png')
    write(assets / f'models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'jurassicraft:item/{item}'}})
for texture in ['bug_crate_top', 'bug_crate_bottom', 'bug_crate_side', 'plankton_swarm', 'krill_swarm']:
    copy(legacy / f'textures/blocks/{texture}.png', assets / f'textures/block/{texture}.png')
copy(legacy / 'textures/gui/bug_crate.png', assets / 'textures/gui/bug_crate.png')
write(assets / 'blockstates/bug_crate.json', {'variants': {'': {'model': 'jurassicraft:block/bug_crate'}}})
write(assets / 'models/block/bug_crate.json', {'parent': 'minecraft:block/cube', 'textures': {
    'up': 'jurassicraft:block/bug_crate_top', 'down': 'jurassicraft:block/bug_crate_bottom',
    'north': 'jurassicraft:block/bug_crate_side', 'south': 'jurassicraft:block/bug_crate_side',
    'east': 'jurassicraft:block/bug_crate_side', 'west': 'jurassicraft:block/bug_crate_side',
    'particle': 'jurassicraft:block/bug_crate_side'}})
write(assets / 'models/item/bug_crate.json', {'parent': 'jurassicraft:block/bug_crate'})
for kind in ['plankton', 'krill']:
    name = kind + '_swarm'
    parent = json.loads((legacy / 'models/block/swarm.json').read_text())
    parent['textures']['all'] = f'jurassicraft:block/{name}'
    write(assets / f'blockstates/{name}.json', {'variants': {'': {'model': f'jurassicraft:block/{name}'}}})
    write(assets / f'models/block/{name}.json', parent)
    write(data / f'loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'jurassicraft:{kind}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
write(data / 'loot_tables/blocks/bug_crate.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jurassicraft:bug_crate'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
for name, slab in [('bug_crate', 'minecraft:smooth_stone_slab'), ('bug_crate_red_sandstone', 'minecraft:red_sandstone_slab')]:
    write(data / f'recipes/{name}.json', {'type': 'minecraft:crafting_shaped', 'group': 'jurassicraft:bug_crate', 'pattern': ['AAA','BCB','DDD'], 'key': {'A': {'tag':'forge:nuggets/iron'}, 'B': {'tag':'minecraft:planks'}, 'C': {'item':'minecraft:chest'}, 'D': {'item':slab}}, 'result': {'item':'jurassicraft:bug_crate'}})
write(data / 'recipes/fine_net.json', {'type': 'minecraft:crafting_shaped', 'pattern': ['ABC',' BB','B A'], 'key': {'A': {'item':'minecraft:string'}, 'B': {'item':'minecraft:stick'}, 'C': {'tag':'minecraft:wool'}}, 'result': {'item':'jurassicraft:fine_net'}})
write(root / 'tools/lang/bugs.json', {'block.jurassicraft.bug_crate':'Bug Crate', 'block.jurassicraft.plankton_swarm':'Plankton Swarm', 'block.jurassicraft.krill_swarm':'Krill Swarm', 'item.jurassicraft.crickets':'Crickets', 'item.jurassicraft.cockroaches':'Cockroaches', 'item.jurassicraft.mealworm_beetles':'Mealworm Beetles', 'item.jurassicraft.fine_net':'Fine Net', 'item.jurassicraft.plankton':'Plankton', 'item.jurassicraft.krill':'Krill', 'container.jurassicraft.bug_crate':'Bug Crate'})
