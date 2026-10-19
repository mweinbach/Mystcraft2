#!/usr/bin/env python3
"""Generate the per-species meat item models, recipes, textures and names."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
RECIPES = ROOT / 'src/main/resources/data/jurassicraft/recipes'
SOURCE = ROOT / 'upstream/src/main/resources/assets/jurassicraft/textures/items'


def generate(species):
    lang_path = ROOT / 'tools/lang/dinosaurs.json'
    names = json.loads(lang_path.read_text())
    for name in species:
        for kind, item in (('meat', f'{name}_meat'), ('steak', f'{name}_steak')):
            target = ASSETS / 'textures/item_port' / kind / f'{name}.png'
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(SOURCE / kind / f'{name}.png', target)
            model = {'parent': 'minecraft:item/generated',
                     'textures': {'layer0': f'jurassicraft:item_port/{kind}/{name}'}}
            (ASSETS / 'models/item' / f'{item}.json').write_text(json.dumps(model, indent=2) + '\n')
        names[f'item.jurassicraft.{name}_meat'] = f'Raw {name.capitalize()} Meat'
        names[f'item.jurassicraft.{name}_steak'] = f'{name.capitalize()} Steak'
        for recipe_type, cooking_time in (('smelting', 200), ('smoking', 100)):
            recipe = {'type': f'minecraft:{recipe_type}', 'ingredient': {'item': f'jurassicraft:{name}_meat'},
                      'result': f'jurassicraft:{name}_steak', 'experience': 5.0, 'cookingtime': cooking_time}
            (RECIPES / f'{name}_steak_from_{recipe_type}.json').write_text(json.dumps(recipe, indent=2) + '\n')
    lang_path.write_text(json.dumps(names, indent=2) + '\n')


if __name__ == '__main__':
    from import_dinosaur_models import SPECIES
    generate(SPECIES)
