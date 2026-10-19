#!/usr/bin/env python3
"""Port Jurassicraft's 1.12 templates to 1.19.2, deterministically and offline.

Palette mappings are vanilla's DFU 1343->3120 output, with explicit legacy
JurassiCraft states and residual properties fixed here. Unknown states fail
rather than silently becoming air. Generated files are ordinary data-pack NBT.
"""
from pathlib import Path
from copy import deepcopy
import json
import math
from structure_nbt import Tag, read, write, plain, from_plain, compound, integer, string

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'upstream/src/main/resources/assets/jurassicraft/structures'
DEST = ROOT / 'src/main/resources/data/jurassicraft/structures'
PALETTE = json.loads((ROOT / 'tools/structure_legacy_palette.json').read_text())['states']
SPECIES = {0:'velociraptor',1:'coelacanth',2:'microraptor',3:'brachiosaurus',4:'mussaurus',7:'dilophosaurus',9:'gallimimus',13:'parasaurolophus',19:'triceratops',20:'tyrannosaurus'}
MURALS = ['creation_lab','fossils','hunt_left','hunt_middle','hunt_right','mosasaurus','park_entrance','riding','sketch','triceratops','valley']
DIRS = {'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
ORDER = ['north','east','south','west']

def convert_state(old):
    key = json.dumps(old,sort_keys=True,separators=(',',':'))
    if key not in PALETTE: raise ValueError('Unmapped legacy block state: ' + key)
    state = deepcopy(PALETTE[key]); name = state['Name']; props = state.get('Properties', {})
    if name.startswith('jurassicraft:fossil_block_') or name.startswith('jurassicraft:encased_fossil_'):
        fossil = name.startswith('jurassicraft:fossil_block_')
        number = int(name.rsplit('_',1)[1])*16 + int(props['variant'])
        if number not in SPECIES: raise ValueError('Unmapped fossil species '+str(number))
        state = {'Name':'jurassicraft:' + SPECIES[number] + ('_fossil_block' if fossil else '_encased_fossil')}
    elif name == 'jurassicraft:fossilized_trackway':
        state = {'Name':'jurassicraft:fossilized_trackway_'+props['variant'],'Properties':{'facing':props['facing']}}
    elif name in ('jurassicraft:low_security_fence_base','jurassicraft:low_security_fence_pole'):
        state = {'Name':name} # These 1.19 blocks have neither legacy properties nor block entities.
    elif name == 'jurassicraft:low_security_fence_wire':
        state['Properties'] = {key:props[key] for key in ('north','east','south','west')}
    elif name.endswith('_leaves') and name.startswith('minecraft:'):
        state['Properties'] = {'persistent':'true' if old.get('Properties',{}).get('decayable') == 'false' else 'false'}
    elif name == 'minecraft:vine':
        # The vanilla 1.12 fixer sets UP=true even when the source has it false.
        state['Properties']['up'] = old.get('Properties',{}).get('up','false')
    return state

def convert(name):
    root_name, root = read(SOURCE / (name+'.nbt'))
    data = root.value
    palette = [convert_state(plain(t)) for t in data['palette'].value[1]]
    blocks = data['blocks'].value[1]
    by_pos = {tuple(plain(b.value['pos'])): b for b in blocks}
    def state_at(pos):
        block = by_pos.get(pos)
        return palette[block.value['state'].value] if block else None
    def set_state(block, state):
        try: index = palette.index(state)
        except ValueError: index = len(palette); palette.append(state)
        block.value['state'] = integer(index)
    for pos, block in by_pos.items():
        state = state_at(pos); blockname = state['Name']; props = state.get('Properties', {})
        # In 1.12 the upper door half did not encode facing/open; 1.19 stores
        # a complete state in both halves. Rebuild it from both source halves.
        if blockname.endswith('_door') and props.get('half') == 'lower':
            upper_block = by_pos.get((pos[0],pos[1]+1,pos[2]))
            upper = state_at((pos[0],pos[1]+1,pos[2]))
            if upper and upper['Name'] == blockname and upper.get('Properties',{}).get('half') == 'upper':
                lower_state=deepcopy(state); upper_state=deepcopy(upper)
                # Metadata for the upper half carried hinge/powered; the lower half carried facing/open.
                for key in ('hinge','powered'): lower_state['Properties'][key]=upper['Properties'][key]
                for key in ('facing','open'): upper_state['Properties'][key]=props[key]
                set_state(block,lower_state); set_state(upper_block,upper_state)
        elif blockname == 'minecraft:chest':
            facing = props['facing']; i = ORDER.index(facing)
            for offset, chest_type in ((1,'left'),(-1,'right')):
                dx,dz = DIRS[ORDER[(i+offset)%4]]
                other = state_at((pos[0]+dx,pos[1],pos[2]+dz))
                if other and other['Name']=='minecraft:chest' and other.get('Properties',{}).get('facing')==facing:
                    merged=deepcopy(state); merged.setdefault('Properties',{})['type']=chest_type; set_state(block,merged); break
        nbt = block.value.get('nbt')
        if nbt:
            n = nbt.value
            old_id = n.get('id', string('')).value
            if blockname.startswith('jurassicraft:low_security_fence_') or blockname.startswith('minecraft:potted_'):
                del block.value['nbt'] # Neither has a block entity in this port.
            elif blockname.startswith('jurassicraft:'):
                # Upstream machine inventories are empty; freshly created 1.19 BEs initialize safely.
                del block.value['nbt']
            elif old_id in ('Chest','Furnace','Structure'):
                n['id']=string({'Chest':'minecraft:chest','Furnace':'minecraft:furnace','Structure':'minecraft:structure_block'}[old_id])
            else: raise ValueError('Unmapped block entity '+str(old_id)+' at '+str(pos))
    # Old template entities have absolute Tile coordinates. Preserve their
    # relative anchors for the runtime processor, and remove saved UUIDs.
    for entity in data['entities'].value[1]:
        n=entity.value['nbt'].value; old=n['id'].value
        origin=[round(plain(n['Pos'])[i]-plain(entity.value['pos'])[i]) for i in range(3)]
        for i,k in enumerate(('TileX','TileY','TileZ')): n[k]=integer(n[k].value-origin[i])
        if old=='jurassicraft.mural':
            n['id']=string('jurassicraft:mural'); n['Artwork']=string(MURALS[n['Type'].value]); n.pop('Type',None)
        elif old=='Painting':
            n['id']=string('minecraft:painting'); n['variant']=string('minecraft:'+n['Motive'].value.lower()); n.pop('Motive',None)
            n['facing']=n.pop('Facing')
        else: raise ValueError('Unmapped entity '+old)
        for k in ('UUID','UUIDMost','UUIDLeast','ForgeCaps','Dimension'): n.pop(k,None)
    data['palette']=Tag(9,(10,[from_plain(s) for s in palette])); data['DataVersion']=integer(3120); data.pop('version',None)
    write(DEST/(name+'.nbt'),root_name,root)
    print(name, len(blocks), 'blocks,',len(palette),'states,',len(data['entities'].value[1]),'entities')

if __name__ == '__main__':
    for name in ('raptor_paddock','visitor_centre'): convert(name)
