#!/usr/bin/env python3
"""Copy the upstream white cultivator models and textures into 1.19 resources."""
import json, shutil
from pathlib import Path
base=Path(__file__).resolve().parent.parent
old=base/'upstream/src/main/resources/assets/jurassicraft'
root=base/'src/main/resources'
assets=root/'assets/jurassicraft'
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(data,indent=2)+'\n')
def copy(path):
    dest=assets/path; dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(old/path,dest)
for name in ('cultivate_top','cultivate_liquid_bottom_white','cultivate_liquid_top_white'):
    copy(f'textures/blocks/{name}.png')
for name in ('cultivator','cultivator_nutrients'):
    copy(f'textures/gui/{name}.png')
copy('textures/items/cultivator_white.png')
for half in ('bottom','top'):
    oldpath=old/f'models/block/cultivator/{half}/cultivate_{half}_white.json'
    data=json.loads(oldpath.read_text()); data['parent']='minecraft:block/block'
    write(assets/f'models/block/cultivator_{half}.json',data)
write(assets/'blockstates/cultivate_bottom.json',{'variants':{'half=lower':{'model':'jurassicraft:block/cultivator_bottom'},'half=upper':{'model':'jurassicraft:block/cultivator_top'}}})
write(assets/'models/item/cultivate_bottom.json',{'parent':'item/generated','textures':{'layer0':'jurassicraft:items/cultivator_white'}})
write(root/'data/jurassicraft/recipes/cultivate_bottom.json',{'type':'minecraft:crafting_shaped','pattern':['ABA','A A','BCB'],'key':{'A':{'item':'minecraft:white_stained_glass_pane'},'B':{'item':'minecraft:iron_ingot'},'C':{'item':'minecraft:comparator'}},'result':{'item':'jurassicraft:cultivate_bottom'}})
write(root/'data/jurassicraft/loot_tables/blocks/cultivate_bottom.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'jurassicraft:cultivate_bottom'}],'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'jurassicraft:cultivate_bottom','properties':{'half':'lower'}}]}]})
write(base/'tools/lang/cultivator.json',{'block.jurassicraft.cultivate_bottom':'Cultivator','container.jurassicraft.cultivator':'Cultivator','gui.jurassicraft.cultivator_nutrient_0':'Proximates','gui.jurassicraft.cultivator_nutrient_1':'Minerals','gui.jurassicraft.cultivator_nutrient_2':'Vitamins','gui.jurassicraft.cultivator_nutrient_3':'Lipids','gui.jurassicraft.cultivator_progress':'Gestation: %s%%'})
