#!/usr/bin/env python3
"""Convert the original village templates and loot to 1.19 jigsaw pieces."""
from copy import deepcopy
import json
from pathlib import Path
import shutil
import sys
from structure_nbt import Tag, read, write, plain, from_plain, compound, integer, string
from convert_structures import convert_state

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
BIOMES = ['plains', 'desert', 'savanna', 'snowy', 'taiga']
NAMES = ['geneticist_house', 'fossildigsite_1', 'fossildigsite_2', 'fossildigsite_3']

def put_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def block(name, **properties):
    result = {'Name': name}
    if properties: result['Properties'] = properties
    return result

def old_state(state):
    name, properties = state['Name'], state.get('Properties', {})
    if name == 'minecraft:glowstone': return block(name)
    if name == 'minecraft:glass_pane': return block(name, **properties)
    if name == 'minecraft:stone' and properties.get('variant') == 'diorite': return block('minecraft:diorite')
    if name == 'minecraft:sandstone': return block(name)
    if name == 'minecraft:oak_stairs': return block(name, **properties)
    return convert_state(state)

def biome_state(state, biome):
    state = deepcopy(state)
    name = state['Name']
    wood = {'savanna': 'acacia', 'taiga': 'spruce', 'snowy': 'spruce'}.get(biome, 'oak')
    if name.startswith('minecraft:oak_'): state['Name'] = name.replace('minecraft:oak_', 'minecraft:' + wood + '_', 1)
    if biome == 'desert':
        names = {'minecraft:oak_planks':'minecraft:cut_sandstone', 'minecraft:oak_log':'minecraft:smooth_sandstone',
                 'minecraft:cobblestone':'minecraft:sandstone', 'minecraft:stone_bricks':'minecraft:sandstone',
                 'minecraft:stone_brick_stairs':'minecraft:sandstone_stairs'}
        if name in names:
            state['Name'] = names[name]
            if name == 'minecraft:oak_log': state.pop('Properties', None)
    return state

def coords(values): return Tag(9, (3, [integer(v) for v in values]))
def doubles(values): return Tag(9, (6, [Tag(6, float(v)) for v in values]))
def villager(name, position, biome, zombie=False):
    x,y,z = position
    data = {'id':string('minecraft:zombie_villager' if zombie else 'minecraft:villager'),
            # Preserve the assigned legacy profession until the villager finds its workstation.
            'Xp':integer(1),
            'VillagerData':compound({'profession':string('jurassicraft:' + name), 'level':integer(1), 'type':string('minecraft:' + biome)}),
            'Pos':doubles((x + .5, y, z + .5))}
    if zombie: data['PersistenceRequired'] = Tag(1, 1)
    return compound({'pos':doubles((x+.5,y,z+.5)), 'blockPos':coords(position), 'nbt':compound(data)})

def template(name, biome, zombie=False):
    root_name, root = read(SOURCE / 'structures' / (name + '.nbt'))
    raw = root.value
    size = plain(raw['size'])
    originals = [plain(x) for x in raw['palette'].value[1]]
    palette = [biome_state(old_state(s), biome) for s in originals]
    blocks = raw['blocks'].value[1]
    by_pos = {tuple(plain(b.value['pos'])): b for b in blocks}
    def set_block(pos, state, nbt=None):
        state = biome_state(state, biome)
        try: index = palette.index(state)
        except ValueError: index = len(palette); palette.append(state)
        b = by_pos.get(tuple(pos))
        if b is None:
            b = compound({'pos': coords(pos), 'state': integer(index)})
            by_pos[tuple(pos)] = b; blocks.append(b)
        else: b.value['state'] = integer(index)
        if nbt is None: b.value.pop('nbt', None)
        else: b.value['nbt'] = nbt
    def marker(pos, kind):
        set_block(pos, block('jurassicraft:ancient_item_holding_block'), compound({'id':string('jurassicraft:ancient_item_holding_block'), 'JurassicMarker':string(kind)}))
    def chest(pos, table, facing='north'):
        set_block(pos, block('minecraft:chest', facing=facing), compound({'id':string('minecraft:chest'), 'LootTable':string('jurassicraft:structure/' + table)}))
    static = {
        'Base':block('minecraft:cobblestone'), 'BaseWood':block('minecraft:oak_planks'), 'Wall':block('minecraft:oak_planks'),
        'Log':block('minecraft:oak_log'), 'StainedClay':block('minecraft:oak_log'), 'Bricks':block('minecraft:stone_bricks'),
        'Hay':block('minecraft:hay_block'), 'Fence':block('minecraft:oak_fence'), 'FenceGate':block('minecraft:oak_fence_gate'),
        'Door':block('minecraft:oak_door', facing='north', half='lower'), 'DoorTop':block('minecraft:oak_door', facing='north', half='upper'),
        'Torch':block('minecraft:torch'), 'TorchDoor':block('minecraft:wall_torch', facing='north'),
    }
    for pos,b in list(by_pos.items()):
        info = b.value.get('nbt')
        if not info: continue
        nbt = plain(info)
        if nbt.get('id') == 'minecraft:chest':
            # Digsite's chest marker sits immediately above its existing chest.
            state = palette[b.value['state'].value]
            chest(pos, 'fossil_digsite', state.get('Properties',{}).get('facing','north'))
            continue
        if nbt.get('id') != 'minecraft:structure_block': raise ValueError((name, pos, nbt))
        kind = nbt.get('metadata')
        if kind == 'GeneticistChest': chest(pos, 'geneticist_house')
        elif kind == 'GeneticistMachine':
            if pos == (4,2,4): set_block(pos, block('jurassicraft:dna_extractor', facing='north'))
            else: marker(pos, 'machine')
        elif kind == 'Fossile': marker(pos, 'fossil')
        elif kind == 'Fossile2': marker(pos, 'fossil_or_ground')
        elif kind == 'Shovel': marker(pos, 'tool')
        elif kind in ('Chest', 'Stair'): set_block(pos, block('minecraft:air'))
        elif kind == 'Log1': set_block(pos, block('minecraft:oak_log', axis='y'))
        elif kind == 'Log2': set_block(pos, block('minecraft:oak_log', axis='x'))
        elif kind == 'Log' and name.startswith('fossil'): set_block(pos, block('minecraft:oak_log', axis='z'))
        elif kind == 'BaseStairs': set_block(pos, block('minecraft:stone_brick_stairs', facing='north'))
        elif zombie and kind in ('Torch', 'TorchDoor'): set_block(pos, block('minecraft:air'))
        elif kind in static: set_block(pos, static[kind])
        else: raise ValueError((name, pos, 'unhandled marker', kind))
    # Add a single street-facing entrance so vanilla jigsaw generation chooses
    # and rotates the piece like other village houses.
    if name == 'geneticist_house':
        entry = (3,0,0); orientation='north_up'; final='minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'
        profession, spawn = 'geneticist', (3,1,3)
    else:
        top = size[1]-1
        entry = (0,top,3); orientation='west_up'; final='minecraft:air'
        station = (0,top,0) if name.endswith('_1') else (1,top,1)
        set_block(station, block('jurassicraft:cleaning_station', facing='north'))
        profession, spawn = 'paleontologist', (3,top,3)
    jig = compound({'id':string('minecraft:jigsaw'), 'name':string('minecraft:building_entrance'),
                    'target':string('minecraft:building_entrance'), 'pool':string('minecraft:village/' + biome + ('/zombie' if zombie else '') + '/streets'),
                    'joint':string('aligned'), 'final_state':string(final)})
    set_block(entry, block('minecraft:jigsaw', orientation=orientation), jig)
    raw['palette'] = Tag(9, (10, [from_plain(s) for s in palette]))
    raw['entities'] = Tag(9, (10, [villager(profession, spawn, biome, zombie)]))
    raw['DataVersion'] = integer(3120)
    raw.pop('version', None)
    destination = DATA / 'structures/village' / biome
    if zombie: destination /= 'zombie'
    write(destination / (name + '.nbt'), root_name, root)

for biome in BIOMES:
    for name in NAMES:
        if '--zombies-only' not in sys.argv: template(name, biome)
        template(name, biome, zombie=True)

if '--zombies-only' in sys.argv:
    print('Generated 20 abandoned-village templates')
    sys.exit(0)

# Upstream chest probabilities, counts, and separate amber metadata variants.
from structure_bonus_loot import structure_bonus_pool
def number(value):
    return {'type':'minecraft:uniform','min':value['min'],'max':value['max']} if isinstance(value, dict) and value['min'] != value['max'] else value['min'] if isinstance(value,dict) else value
for name in ['geneticist_house', 'fossil_digsite']:
    original = json.loads((SOURCE / 'loot_tables/structure' / (name + '.json')).read_text())
    pools = []
    for pool in original['pools']:
        entries=[]
        scaled = any(any(f['function']=='set_data' for f in old.get('functions', [])) for old in pool['entries'])
        for old in pool['entries']:
            item = old['name'].replace('jurassicraft:dna_base_material','jurassicraft:dna_nucleotides')
            names = [item, 'jurassicraft:amber_aphid'] if any(f['function']=='set_data' for f in old.get('functions', [])) else [item]
            for value in names:
                entry={'type':'minecraft:item','name':value}
                weight = old.get('weight', 1) * (2 if scaled and len(names) == 1 else 1)
                entry['weight'] = weight
                functions=[{'function':'minecraft:set_count','count':number(f['count'])} for f in old.get('functions',[]) if f['function']=='set_count']
                if functions: entry['functions']=functions
                if old.get('conditions'):
                    entry['conditions']=[{'condition':'minecraft:random_chance','chance':c['chance']} for c in old['conditions']]
                entries.append(entry)
        pools.append({'rolls':number(pool['rolls']), 'entries':entries})
    bonus = structure_bonus_pool(name)
    if bonus is not None: pools.append(bonus)
    put_json(DATA/'loot_tables/structure'/(name+'.json'), {'type':'minecraft:chest', 'pools':pools})

put_json(ASSETS/'blockstates/ancient_item_holding_block.json', {'variants':{'':{'model':'jurassicraft:block/ancient_item_holding_block'}}})
put_json(ASSETS/'models/block/ancient_item_holding_block.json', {'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/sand'}})
put_json(ASSETS/'models/item/ancient_item_holding_block.json', {'parent':'jurassicraft:block/ancient_item_holding_block'})
put_json(DATA/'loot_tables/blocks/ancient_item_holding_block.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'minecraft:sand'}]}]})
put_json(ROOT/'tools/lang/village.json', {'block.jurassicraft.ancient_item_holding_block':'Ancient Item Holding Block',
    'entity.minecraft.villager.jurassicraft.geneticist':'Geneticist','entity.minecraft.villager.jurassicraft.paleontologist':'Paleontologist'})
for profession in ['geneticist','paleontologist']:
    for layer,suffix in [('villager',''),('zombie_villager','_zombie')]:
        dest=ASSETS/'textures/entity'/layer/'profession'/(profession+'.png')
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(SOURCE/'textures/entities/villager'/(profession+suffix+'.png'),dest)
print('Generated 40 village templates, professions, and loot tables')
