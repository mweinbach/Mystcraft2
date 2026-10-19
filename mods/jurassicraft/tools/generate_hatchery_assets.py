#!/usr/bin/env python3
"""Convert the upstream hatchery models and generate their supporting assets."""
import json
import shutil
from pathlib import Path
from convert_dna_machine_models import convert, MODELS

base = Path(__file__).resolve().parent.parent
old = base / 'upstream/src/main/resources/assets/jurassicraft'
root = base / 'src/main/resources'
assets = root / 'assets/jurassicraft'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def copy(path):
    target = assets / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(old / path, target)

machine_names=['embryonic_machine','embryo_calcification_machine','incubator']
for name in machine_names:
    copy(f'textures/blocks/{name}.png'); copy(f'textures/gui/{name}.png')
    convert(name, *MODELS[name])
    write(assets/f'blockstates/{name}.json',{'variants':{f'facing={direction}':{'model':f'jurassicraft:block/{name}','y':angle} for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    write(assets/f'models/item/{name}.json',{'parent':f'jurassicraft:block/{name}'})
    write(root/f'data/jurassicraft/loot_tables/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'jurassicraft:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
copy('textures/blocks/peat_moss.png')
write(assets/'blockstates/peat_moss.json',{'variants':{'':{'model':'jurassicraft:block/peat_moss'}}})
write(assets/'models/block/peat_moss.json',{'parent':'minecraft:block/cube_all','textures':{'all':'jurassicraft:blocks/peat_moss'}})
write(assets/'models/item/peat_moss.json',{'parent':'jurassicraft:block/peat_moss'})
write(root/'data/jurassicraft/loot_tables/blocks/peat_moss.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'jurassicraft:peat_moss'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
copy('textures/items/syringe/syringe.png')
species=['velociraptor','coelacanth','microraptor','brachiosaurus','mussaurus','dilophosaurus','gallimimus','parasaurolophus','triceratops','tyrannosaurus']
translations={'block.jurassicraft.peat_moss':'Peat Moss', 'tooltip.jurassicraft.incubator_temperature':'Temperature: %s%% (%s)', 'tooltip.jurassicraft.incubator_male':'Male', 'tooltip.jurassicraft.incubator_female':'Female'}
for name in species:
    copy(f'textures/items/overlay/{name}.png')
    write(assets/f'models/item/{name}_syringe.json',{'parent':'item/generated','textures':{'layer0':'jurassicraft:items/syringe/syringe','layer1':f'jurassicraft:items/overlay/{name}'}})
    display=name.title()
    translations[f'item.jurassicraft.{name}_syringe']=f'{display} Embryo Syringe'
    if name == 'coelacanth':
        copy('textures/items/gestated/coelacanth.png')
        write(assets/'models/item/coelacanth_gestated.json',{'parent':'item/generated','textures':{'layer0':'jurassicraft:items/gestated/coelacanth','layer1':'jurassicraft:items/overlay/coelacanth'}})
        translations['item.jurassicraft.coelacanth_gestated']='Gestated Coelacanth'
    else:
        for kind,label in [('egg','Egg'),('hatched_egg','Hatched Egg')]:
            copy(f'textures/items/{kind}/{name}.png')
            write(assets/f'models/item/{name}_{kind}.json',{'parent':'item/generated','textures':{'layer0':f'jurassicraft:items/{kind}/{name}','layer1':f'jurassicraft:items/overlay/{name}'}})
            translations[f'item.jurassicraft.{name}_{kind}']=f'{display} {label}'
for name,label in [('embryonic_machine','Embryonic Machine'),('embryo_calcification_machine','Embryo Calcification Machine'),('incubator','Incubator')]:
    translations[f'block.jurassicraft.{name}']=label
    translations[f'container.jurassicraft.{name}']=label
write(base/'tools/lang/hatchery.json',translations)
recipes={
'embryonic_machine':(['ABC','ADC','EEC'],{'A':'#forge:nuggets/iron','B':'minecraft:piston','C':'minecraft:stone_button','D':'minecraft:glowstone_dust','E':'minecraft:iron_ingot'}),
'embryo_calcification_machine':(['ABC','DED','DFD'],{'A':'minecraft:iron_ingot','B':'minecraft:bowl','C':'jurassicraft:keyboard','D':'jurassicraft:iron_rod','E':'jurassicraft:basic_circuit','F':'minecraft:iron_block'}),
'incubator':(['ABA','CCC','BDB'],{'A':'minecraft:glass','B':'minecraft:iron_ingot','C':'minecraft:comparator','D':'jurassicraft:keyboard'}),
'peat_moss':(['AAA','AAA','AAA'],{'A':'jurassicraft:moss'}),
}
for name,(pattern,keys) in recipes.items():
    write(root/f'data/jurassicraft/recipes/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:({'tag':v[1:]} if v.startswith('#') else {'item':v}) for k,v in keys.items()},'result':{'item':f'jurassicraft:{name}'}})
