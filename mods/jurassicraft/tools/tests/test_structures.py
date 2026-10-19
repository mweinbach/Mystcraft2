"""Validate converted template integrity without launching or accepting a server EULA."""
import collections
import json
import sys
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from structure_nbt import read, plain

SOURCE=ROOT/'upstream/src/main/resources/assets/jurassicraft/structures'
DEST=ROOT/'src/main/resources/data/jurassicraft/structures'
LOOT=ROOT/'src/main/resources/data/jurassicraft/loot_tables/structure/visitor_centre'
ROOMS={'GroundStorage':'ground_storage','ControlRoom':'control_room','Laboratory':'laboratory','Cryonics':'cryonics','Infirmary':'infirmary','Garage':'garage','StaffDorms':'staff_dorms','Kitchen':'kitchen','DormTower':'dorm_tower','DiningHall':'dining_hall'}

def template(path):
    root=read(path)[1].value
    palette=[plain(t) for t in root['palette'].value[1]]
    blocks={tuple(plain(t.value['pos'])):t for t in root['blocks'].value[1]}
    return root,palette,blocks

def state(palette, block): return palette[block.value['state'].value]

class StructureTests(unittest.TestCase):
    def test_source_geometry_and_mod_blocks_resolve(self):
        for name,size in [('raptor_paddock',[31,16,22]),('visitor_centre',[81,34,104])]:
            with self.subTest(template=name):
                source,_,original=template(SOURCE/(name+'.nbt'))
                root,palette,blocks=template(DEST/(name+'.nbt'))
                self.assertEqual(plain(root['size']),size)
                self.assertEqual(set(blocks),set(original))
                self.assertEqual(root['DataVersion'].value,3120)
                for entry in palette:
                    name=entry['Name']
                    if name.startswith('jurassicraft:'):
                        model=ROOT/'src/main/resources/assets/jurassicraft/blockstates'/(name.split(':')[1]+'.json')
                        self.assertTrue(model.exists(),name)
                        self.assertNotIn('variant',entry.get('Properties',{}),name)
                for block in blocks.values():
                    st=state(palette,block)
                    if st['Name'].endswith('_leaves'):
                        self.assertFalse({'decayable','check_decay'} & set(st.get('Properties',{})))
                    if 'nbt' in block.value:
                        tag=plain(block.value['nbt'])
                        self.assertTrue(tag['id'].startswith('minecraft:'),tag)
                        self.assertFalse(st['Name'].startswith('jurassicraft:low_security_fence_'))

    def test_door_halves_and_chest_pairs(self):
        for name in ('raptor_paddock','visitor_centre'):
            with self.subTest(template=name):
                _,palette,blocks=template(DEST/(name+'.nbt'))
                for (x,y,z),block in blocks.items():
                    st=state(palette,block); p=st.get('Properties',{}); block_id=st['Name']
                    if block_id.endswith('_door') and p.get('half')=='lower' and (x,y+1,z) in blocks:
                        upper=state(palette,blocks[(x,y+1,z)])
                        if upper['Name']==block_id and upper.get('Properties',{}).get('half')=='upper':
                            for field in ('facing','open','hinge','powered'):
                                self.assertEqual(p.get(field),upper['Properties'].get(field),(name,(x,y,z),field))
                    if block_id == 'minecraft:chest' and p.get('type') in ('left','right'):
                        order=['north','east','south','west']; dirs={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
                        idx=order.index(p['facing']); dx,dz=dirs[order[(idx+(1 if p['type']=='left' else -1))%4]]
                        other=state(palette,blocks[(x+dx,y,z+dz)]); other_p=other['Properties']
                        self.assertEqual(other['Name'],'minecraft:chest')
                        self.assertEqual(other_p['facing'],p['facing'])
                        self.assertEqual(other_p['type'],'right' if p['type']=='left' else 'left')

    def test_chest_markers_and_room_loot(self):
        for name,count in [('raptor_paddock',1),('visitor_centre',61)]:
            _,palette,blocks=template(DEST/(name+'.nbt')); markers=[]
            for (x,y,z),block in blocks.items():
                if state(palette,block)['Name']!='minecraft:structure_block': continue
                nbt=plain(block.value['nbt']); markers.append(nbt['metadata'])
                self.assertEqual(nbt['mode'],'DATA')
                self.assertEqual(state(palette,blocks[(x,y-1,z)])['Name'],'minecraft:chest')
                if nbt['metadata']!='Chest': self.assertTrue((LOOT/(ROOMS[nbt['metadata']]+'.json')).exists())
            self.assertEqual(len(markers),count)
        self.assertEqual(len(list(LOOT.glob('*.json'))),10)
        for path in LOOT.glob('*.json'):
            data=json.loads(path.read_text()); self.assertEqual(data['type'],'minecraft:chest')
            for pool in data['pools']:
                for entry in pool['entries']:
                    if entry['type']=='minecraft:item' and entry['name'].startswith('jurassicraft:'):
                        model=ROOT/'src/main/resources/assets/jurassicraft/models/item'/(entry['name'].split(':')[1]+'.json')
                        self.assertTrue(model.exists(),entry['name'])
                    for function in entry.get('functions',[]): self.assertNotEqual(function['function'],'minecraft:set_data')

    def test_visitor_hanging_entities_have_relative_anchor_and_current_ids(self):
        root,_,_=template(DEST/'visitor_centre.nbt')
        entities=[plain(t) for t in root['entities'].value[1]]
        self.assertEqual(collections.Counter(e['nbt']['id'] for e in entities),{'jurassicraft:mural':11,'minecraft:painting':3})
        for entity in entities:
            nbt=entity['nbt']; self.assertTrue(all(-1<=nbt[k]<=105 for k in ('TileX','TileY','TileZ')))
            self.assertFalse({'UUID','UUIDLeast','UUIDMost','ForgeCaps'} & set(nbt))
            if nbt['id']=='jurassicraft:mural': self.assertIn('Artwork',nbt)
            else: self.assertIn('facing',nbt); self.assertEqual(nbt['variant'],'minecraft:aztec')

if __name__=='__main__': unittest.main()
