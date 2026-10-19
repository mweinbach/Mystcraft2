#!/usr/bin/env python3
"""Copy upstream models and textures for the port's split genetics item IDs."""
from pathlib import Path
import json
import shutil

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/jurassicraft'
upstream = root / 'upstream/src/main/resources/assets/jurassicraft'
models = assets / 'models/item'
textures = assets / 'textures'
models.mkdir(parents=True, exist_ok=True)
fragment = {
    'item.jurassicraft.storage_disc': 'Storage Disc',
    'tooltip.jurassicraft.dna_quality': 'DNA Quality: %s%%',
    'tooltip.jurassicraft.genetic_code': 'Genetic Code: %s',
    'tooltip.jurassicraft.dna_species': 'Species: %s',
}

def copy_model(source, target):
    model = json.loads(source.read_text())
    (models / (target + '.json')).write_text(json.dumps(model, indent=2) + '\n')
    for value in model.get('textures', {}).values():
        namespace, path = value.split(':', 1)
        if namespace == 'jurassicraft':
            src = upstream / 'textures' / (path + '.png')
            dst = textures / (path + '.png')
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)

copy_model(upstream / 'models/item/storage_disc.json', 'storage_disc')
for source in sorted((upstream / 'models/item/soft_tissue').glob('soft_tissue_*.json')):
    species = source.stem.removeprefix('soft_tissue_')
    title = species.replace('_', ' ').title()
    copy_model(source, species + '_soft_tissue')
    copy_model(upstream / 'models/item/dna' / ('dna_' + species + '.json'), species + '_dna')
    fragment['item.jurassicraft.' + species + '_soft_tissue'] = title + ' Soft Tissue'
    fragment['item.jurassicraft.' + species + '_dna'] = title + ' DNA'
    fragment['species.jurassicraft.' + species] = title
for source in sorted((upstream / 'models/item/soft_tissue/plants').glob('soft_tissue_*.json')):
    species = source.stem.removeprefix('soft_tissue_')
    title = species.replace('_', ' ').title()
    copy_model(source, species + '_plant_tissue')
    copy_model(upstream / 'models/item/dna/plants' / ('dna_' + species + '.json'), species + '_plant_dna')
    fragment['item.jurassicraft.' + species + '_plant_tissue'] = title + ' Soft Tissue'
    fragment['item.jurassicraft.' + species + '_plant_dna'] = title + ' DNA'
    fragment['species.jurassicraft.' + species] = title
(root / 'tools/genetics/en_us.fragment.json').write_text(json.dumps(fragment, indent=2, ensure_ascii=False) + '\n')
