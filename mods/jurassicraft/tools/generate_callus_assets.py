#!/usr/bin/env python3
from pathlib import Path
import json, shutil
root = Path(__file__).resolve().parents[1]
upstream = root / 'upstream/src/main/resources/assets/jurassicraft'
assets = root / 'src/main/resources/assets/jurassicraft'
texture = assets / 'textures/items/plant_callus.png'
texture.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(upstream / 'textures/items/plant_callus.png', texture)
fragment = {}
for f in sorted((upstream / 'models/item/soft_tissue/plants').glob('soft_tissue_*.json')):
    name = f.stem.removeprefix('soft_tissue_')
    overlay = json.loads(f.read_text())['textures']['layer1']
    overlay_path = Path(overlay.split(':', 1)[1] + '.png')
    (assets / 'textures' / overlay_path).parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(upstream / 'textures' / overlay_path, assets / 'textures' / overlay_path)
    path = assets / 'models/item' / (name + '_plant_callus.json')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': {
        'layer0': 'jurassicraft:items/plant_callus', 'layer1': overlay}}, indent=2) + '\n')
    fragment['item.jurassicraft.' + name + '_plant_callus'] = name.replace('_', ' ').title() + ' Callus'
(root / 'tools/genetics/callus_en_us.fragment.json').write_text(json.dumps(fragment, indent=2) + '\n')
