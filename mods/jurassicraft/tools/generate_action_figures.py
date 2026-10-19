#!/usr/bin/env python3
"""Copy the original action-figure icons and generate their crafting data."""
import json
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/jurassicraft'
data = root / 'src/main/resources/data/jurassicraft'
upstream = root / 'upstream/src/main/resources/assets/jurassicraft'
figures = ['brachiosaurus', 'coelacanth', 'dilophosaurus', 'gallimimus', 'microraptor', 'mussaurus',
           'parasaurolophus', 'triceratops', 'tyrannosaurus', 'velociraptor']

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

for species in figures:
    source = upstream / 'textures/items/action_figure' / (species + '.png')
    target = assets / 'textures/items/action_figure' / (species + '.png')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
    write(assets / 'models/item/action_figure' / (species + '.json'), {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/action_figure/' + species}})
write(assets / 'models/item/action_figure.json', {
    'parent': 'jurassicraft:item/action_figure/brachiosaurus',
    'overrides': [{'predicate': {'jurassicraft:figure_species': index}, 'model': 'jurassicraft:item/action_figure/' + species}
                  for index, species in enumerate(figures) if index]})
# The block is rendered entirely by its block-entity renderer.
write(assets / 'blockstates/action_figure.json', {'variants': {'': {'model': 'jurassicraft:block/action_figure'}}})
write(assets / 'models/block/action_figure.json', {'textures': {'particle': 'minecraft:block/oak_planks'}, 'elements': []})
write(data / 'recipes/action_figure.json', {
    'type': 'minecraft:crafting_shaped', 'group': 'jurassicraft:action_figure', 'pattern': [' C ', 'SSS'],
    'key': {'C': {'item': 'minecraft:clay_ball'}, 'S': {'tag': 'minecraft:wooden_slabs'}},
    'result': {'item': 'jurassicraft:action_figure'}})
write(data / 'recipes/action_figure_cycle.json', {'type': 'jurassicraft:action_figure_cycle'})
write(root / 'tools/lang/action_figures.json', {'item.jurassicraft.action_figure': '%s Action Figure'})
