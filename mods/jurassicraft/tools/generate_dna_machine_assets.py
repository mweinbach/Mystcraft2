#!/usr/bin/env python3
from pathlib import Path
import json, shutil
from convert_dna_machine_models import convert, MODELS
root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/jurassicraft'
data = root / 'src/main/resources/data/jurassicraft'
source = root / 'upstream/src/main/resources/assets/jurassicraft'
names = {'dna_extractor': 'DNA Extractor', 'dna_sequencer': 'DNA Sequencer',
         'dna_combinator_hybridizer': 'DNA Combinator / Hybridizer', 'dna_synthesizer': 'DNA Synthesizer'}
fragment = {}
for name in ('dna_extractor', 'dna_sequencer', 'dna_combinator', 'dna_hybridizer', 'dna_synthesizer'):
    texture = assets / 'textures/gui' / (name + '.png')
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'textures/gui' / (name + '.png'), texture)
def out(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')
for name, title in names.items():
    fragment['block.jurassicraft.' + name] = title
    fragment['container.jurassicraft.' + name] = title
    texture = assets / 'textures/items' / (name + '.png')
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'textures/items' / (name + '.png'), texture)
    convert(name, *MODELS[name])
    out(assets / 'models/item' / (name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jurassicraft:items/' + name}})
    out(assets / 'blockstates' / (name + '.json'), {'variants': {
        'facing=north': {'model': 'jurassicraft:block/' + name},
        'facing=east': {'model': 'jurassicraft:block/' + name, 'y': 90},
        'facing=south': {'model': 'jurassicraft:block/' + name, 'y': 180},
        'facing=west': {'model': 'jurassicraft:block/' + name, 'y': 270}}})
    out(data / 'loot_tables/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jurassicraft:' + name}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    recipe = json.loads((source / 'recipes' / (name + '.json')).read_text())
    for ingredient in recipe['key'].values(): ingredient.pop('data', None)
    recipe['result'].pop('data', None)
    out(data / 'recipes' / (name + '.json'), recipe)
fragment.update({
    'gui.jurassicraft.dna_slot.extract_sample': 'Amber, sea lamprey or dinosaur meat',
    'gui.jurassicraft.dna_slot.blank_disc': 'Blank storage disc',
    'gui.jurassicraft.dna_slot.tissue': 'Soft tissue',
    'gui.jurassicraft.dna_slot.filled_disc': 'Disc with DNA',
    'gui.jurassicraft.dna_slot.hybrid_disc': 'Disc with 100% dinosaur DNA',
    'gui.jurassicraft.dna_slot.complete_disc': 'Disc with 100% DNA',
    'gui.jurassicraft.dna_slot.test_tube': 'Empty test tube',
    'gui.jurassicraft.dna_slot.nucleotides': 'DNA base material',
    'gui.jurassicraft.dna_slot.output': 'Output',
    'gui.jurassicraft.dna_mode.hybridize': 'Hybridize',
    'gui.jurassicraft.dna_mode.combine': 'Combine',
    'gui.jurassicraft.dna_mode.no_hybrids': 'No hybrids',
})
(root / 'tools/genetics/machine_en_us.fragment.json').write_text(json.dumps(fragment, indent=2) + '\n')
