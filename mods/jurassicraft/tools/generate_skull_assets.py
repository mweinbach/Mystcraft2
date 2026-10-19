#!/usr/bin/env python3
"""Extract the original Tyrannosaurus skull Tabula models and textures."""
import json
import shutil
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
legacy=root/'upstream/src/main/resources/assets/jurassicraft'
assets=root/'src/main/resources/assets/jurassicraft'
data=root/'src/main/resources/data/jurassicraft'

def write(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,indent=2)+'\n')

for orientation in ('horizontal','vertical'):
    path=legacy/f'models/block/skull_display/tyrannosaurus_{orientation}.tbl'
    with ZipFile(path) as archive: model=json.loads(archive.read('model.json'))
    write(assets/f'models/skull_port/tyrannosaurus_{orientation}.json',model)
    for bone in ('fresh','fossilized'):
        texture=f'tyrannosaurus_{bone}_{orientation}.png'
        dst=assets/f'textures/skull_display/{texture}'
        dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(legacy/f'textures/blocks/skull_display/{texture}',dst)
write(assets/'blockstates/skull_display.json',{'variants':{'':{'model':'jurassicraft:block/skull_display'}}})
write(assets/'models/block/skull_display.json',{'textures':{'particle':'minecraft:block/stone'}})
# The block entity returns its preserved skull stack; the loot table must not duplicate it.
write(data/'loot_tables/blocks/skull_display.json',{'type':'minecraft:block','pools':[]})
write(root/'tools/lang/skull.json',{'block.jurassicraft.skull_display':'Tyrannosaurus Skull', 'message.jurassicraft.skull_stand':'Skull pose: on stand', 'message.jurassicraft.skull_no_stand':'Skull pose: without stand', 'tooltip.jurassicraft.skull_pose':'Sneak and use in air to change pose'})
