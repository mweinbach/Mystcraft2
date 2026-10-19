#!/usr/bin/env python3
"""Create static assembly-table resources and copy the original display icons."""
import json
import re
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT/'upstream/src/main/resources/assets/jurassicraft'
ASSETS = ROOT/'src/main/resources/assets/jurassicraft'
DATA = ROOT/'src/main/resources/data/jurassicraft'
SPECIES = ['velociraptor', 'coelacanth', 'microraptor', 'brachiosaurus', 'mussaurus', 'dilophosaurus', 'gallimimus', 'parasaurolophus', 'triceratops', 'tyrannosaurus']


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def cuboid(start, end, top, side):
    return {'from': start, 'to': end, 'faces': {
        'up': {'texture':'#table','uv':top}, 'down': {'texture':'#table','uv':top},
        **{face: {'texture':'#table','uv':side} for face in ('north','south','east','west')}
    }}


for part in ('foot', 'head'):
    board = cuboid([0,12,0],[16,14,16], [3.25,0,6.5,3.25], [3.25,3.25,6.5,3.5])
    elements = [board]
    for x in (1, 13):
        for z in (1, 13):
            elements.append(cuboid([x,0,z],[x+2,12,z+2], [0,0,0.25,0.25], [0,0,0.25,2.75]))
    if part == 'foot':
        elements.append(cuboid([2,14,2],[14,14.1,12], [2.125,3.75,8.25,6], [2.125,3.75,8.25,6]))
    write(ASSETS/f'models/block/skeleton_assembly_{part}.json', {'render_type':'minecraft:cutout',
        'textures':{'particle':'jurassicraft:blocks/skeleton_assembly','table':'jurassicraft:blocks/skeleton_assembly'},'elements':elements})
variants = {}
for part in ('foot','head'):
    for facing, rotation in [('north',0),('east',90),('south',180),('west',270)]:
        entry = {'model':f'jurassicraft:block/skeleton_assembly_{part}'}
        if rotation: entry['y'] = rotation
        variants[f'facing={facing},part={part}'] = entry
write(ASSETS/'blockstates/skeleton_assembly.json', {'variants':variants})
write(ASSETS/'models/item/skeleton_assembly.json', {'parent':'minecraft:item/generated','textures':{'layer0':'jurassicraft:items/skeleton_assembly'}})
write(ASSETS/'models/block/skeleton_display.json', {'parent':'minecraft:block/block',
    'textures':{'particle':'minecraft:block/smooth_stone','stone':'minecraft:block/smooth_stone'},
    'elements':[{'from':[2,0,2],'to':[14,2,14], 'faces':{face:{'texture':'#stone'} for face in ('north','south','east','west','up','down')}}]})
write(ASSETS/'blockstates/skeleton_display.json', {'variants':{f'facing={f}':{'model':'jurassicraft:block/skeleton_display'} for f in ('north','south','east','west')}})
for species in SPECIES:
    for kind in ('fossil', 'fresh'):
        model = f'skeleton_display_{species}' if kind == 'fossil' else f'skeleton_display_fresh_{species}'
        write(ASSETS/f'models/item/{model}.json', {'parent':'minecraft:item/generated', 'textures':{'layer0':f'jurassicraft:items/skeleton/{kind}/{species}'}})
        dest = ASSETS/f'textures/items/skeleton/{kind}/{species}.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(UP/f'textures/items/skeleton/{kind}/{species}.png', dest)
write(ASSETS/'models/item/skeleton_display.json', {'parent':'minecraft:item/generated',
    'textures':{'layer0':'jurassicraft:items/skeleton/fossil/velociraptor'},
    'overrides':[{'predicate':{'jurassicraft:species':i},'model':f'jurassicraft:item/skeleton_display_{species}'} for i,species in enumerate(SPECIES)] +
        [{'predicate':{'jurassicraft:species':i,'jurassicraft:fresh':1},'model':f'jurassicraft:item/skeleton_display_fresh_{species}'} for i,species in enumerate(SPECIES)]})

# Some original fresh models use differently named textures; follow their model
# references instead of guessing the texture from the bone's registry name.
species_source = (ROOT/'src/main/java/ports/jurassicraft/content/Species.java').read_text()
entries = re.findall(r'^\s*([A-Z]+)\(\d+,\s*"[^"]+",\s*(.*?)\)[,;]\s*$', species_source, re.M)
assert len(entries) == len(SPECIES)
original_lang = dict(line.split('=', 1) for line in (UP/'lang/en_US.lang').read_text().splitlines() if '=' in line and not line.startswith('#'))
fresh_lang = {}
for species, raw in entries:
    species = species.lower()
    for bone in re.findall(r'"([^"]+)"', raw):
        name = f'{species}_fresh_{bone}'
        old_model = json.loads((UP/f'models/item/fresh_bones/{species}_{bone}.json').read_text())
        texture = old_model['textures']['layer0']
        source = UP/'textures'/f'{texture.split(":",1)[1]}.png'
        dest = ASSETS/'textures'/f'{texture.split(":",1)[1]}.png'
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest)
        write(ASSETS/f'models/item/{name}.json', {'parent':'minecraft:item/generated','textures':{'layer0':texture}})
        template = original_lang.get(f'item.{bone}_fresh.name', f'Fresh {{dino}} {bone.replace("_", " ").title()}')
        fresh_lang[f'item.jurassicraft.{name}'] = template.replace('{dino}', species.title())
write(ROOT/'tools/lang/assembly_fresh.json', fresh_lang)

# Keep the GameTest oracle independent of the hand-ported recipe tables.
fixture_lines = ['package ports.jurassicraft.assembly;', '', 'import java.util.Map;',
                 'import ports.jurassicraft.content.Species;', '',
                 '/** Generated from the original dinosaur metadata by generate_assembly.py. */',
                 'final class AssemblyRecipeFixtures {', '    static final Map<Species, String[][]> RECIPES = Map.ofEntries(']
upstream_java = ROOT/'upstream/src/main/java/org/jurassicraft/server/dinosaur'
for i, species in enumerate(SPECIES):
    source = (upstream_java/f'{species.title()}Dinosaur.java').read_text()
    match = re.search(r'\.setRecipe\(new String\[\]\[\]\s*\{(.*?)\}\s*\)', source, re.S)
    assert match, f'no upstream skeleton recipe for {species}'
    rows = re.findall(r'\{([^{}]+)\}', match.group(1))
    assert rows
    formatted = ['{' + ', '.join(json.dumps(x) for x in re.findall(r'"([^"]*)"', row)) + '}' for row in rows]
    fixture_lines.append(f'            Map.entry(Species.{species.upper()}, new String[][] {{{", ".join(formatted)}}})' + (',' if i < len(SPECIES) - 1 else ''))
fixture_lines += ['    );', '    private AssemblyRecipeFixtures() {}', '}', '']
(ROOT/'src/main/java/ports/jurassicraft/assembly/AssemblyRecipeFixtures.java').write_text('\n'.join(fixture_lines))
write(DATA/'recipes/skeleton_assembly.json', {'type':'minecraft:crafting_shaped','pattern':['ABB','CDC','EFE'],
    'key':{'A':{'tag':'forge:dyes/blue'},'B':{'item':'minecraft:paper'},'C':{'tag':'minecraft:wooden_slabs'},
           'D':{'item':'minecraft:crafting_table'},'E':{'item':'minecraft:oak_fence'},'F':{'item':'jurassicraft:low_security_fence_wire'}},
    'result':{'item':'jurassicraft:skeleton_assembly'}})
write(DATA/'loot_tables/blocks/skeleton_assembly.json', {'type':'minecraft:block','pools':[{'rolls':1,
    'entries':[{'type':'minecraft:item','name':'jurassicraft:skeleton_assembly'}],
    'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(DATA/'loot_tables/blocks/skeleton_display.json', {'type':'minecraft:block','pools':[{'rolls':1,
    'entries':[{'type':'minecraft:item','name':'jurassicraft:skeleton_display',
        'functions':[{'function':'minecraft:copy_nbt','source':'block_entity','ops':[{'source':key,'target':'BlockEntityTag.'+key,'op':'replace'} for key in ('Species','Fresh','Gender','Pose')]}]}],
    'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for kind, name in [('blocks','skeleton_assembly'),('items','skeleton_assembly'),('gui','skeleton_assembler')]:
    dest=ASSETS/f'textures/{kind}/{name}.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP/f'textures/{kind}/{name}.png', dest)
