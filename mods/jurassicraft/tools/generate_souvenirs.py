#!/usr/bin/env python3
"""Copy original decorative gift models and recipes into the 1.19 resource layout."""
import json
import shutil
from pathlib import Path

root=Path(__file__).resolve().parents[1]
legacy=root/'upstream/src/main/resources/assets/jurassicraft'
assets=root/'src/main/resources/assets/jurassicraft'
data=root/'src/main/resources/data/jurassicraft'
names={'amber_cane':'Amber Cane', 'amber_keychain':'Amber Keychain', 'mr_dna_keychain':'Mr DNA Keychain', 'lunch_box':'Plastic Lunch Box', 'stamp_set':'Stamp Set'}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n')

for item in names:
    path=assets/f'textures/item/{item}.png'
    path.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(legacy/f'textures/items/{item}.png',path)
    parent='minecraft:item/handheld' if item=='amber_cane' else 'minecraft:item/generated'
    write(assets/f'models/item/{item}.json',{'parent':parent,'textures':{'layer0':f'jurassicraft:item/{item}'}})
for item in ['amber_cane','amber_keychain','mr_dna_keychain']:
    recipe=json.loads((legacy/f'recipes/{item}.json').read_text())
    recipe.pop('group',None)
    for ingredient in recipe['key'].values():
        ingredient.pop('data',None)
        if ingredient.get('item')=='jurassicraft:dna_base_material': ingredient['item']='jurassicraft:dna_nucleotides'
    recipe['result'].pop('data',None)
    write(data/f'recipes/{item}.json',recipe)
write(root/'tools/lang/souvenirs.json',{f'item.jurassicraft.{item}':name for item,name in names.items()})
