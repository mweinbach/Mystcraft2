#!/usr/bin/env python3
"""Convert original room loot to 1.19 format, preserving weights and counts."""
import copy
import json
from pathlib import Path
from structure_bonus_loot import structure_bonus_pool
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'upstream/src/main/resources/assets/jurassicraft/loot_tables/structure/visitor_centre'
DEST=ROOT/'src/main/resources/data/jurassicraft/loot_tables/structure/visitor_centre'
RENAME={'minecraft:fish':'minecraft:cod','minecraft:dye':'minecraft:ink_sac','minecraft:wool':'minecraft:white_wool','minecraft:melon':'minecraft:melon_slice','minecraft:sign':'minecraft:oak_sign','jurassicraft:dna_base_material':'jurassicraft:dna_nucleotides'}
AUTHORS=['chef_alejandro','dennis_nedry','dr_gerry_harding','dr_henry_wu','dr_laura_sorkin','ed_regis','john_hammond','ray_arnold','robert_muldoon']
def number(value):
    if not isinstance(value,dict): return value
    if value['min']==value['max']: return value['min']
    return {'type':'minecraft:uniform','min':value['min'],'max':value['max']}
def convert_entry(old):
    entry={'type':'minecraft:item','name':RENAME.get(old['name'],old['name'])}
    if 'weight' in old: entry['weight']=old['weight']
    entry['functions']=[]; author=None
    for f in old.get('functions',[]):
        if f['function']=='set_count': entry['functions'].append({'function':'minecraft:set_count','count':number(f['count'])})
        elif f['function']=='minecraft:set_data' and entry['name']=='jurassicraft:ingen_journal': author=f['data']
        else: raise ValueError('Unsupported loot function: '+str(f))
    if author is None: return [entry]
    ids = range(author['min'],author['max']+1) if isinstance(author,dict) else [author]
    ids=list(ids)
    # Preserve relative weights when a legacy metadata range chooses between authors.
    result=[]
    for author_id in ids:
        new=copy.deepcopy(entry); new['weight']=entry.get('weight',1)//len(ids)
        new['functions'].append({'function':'minecraft:set_nbt','tag':'{JournalAuthor:"'+AUTHORS[author_id]+'"}'})
        result.append(new)
    return result

def convert():
    DEST.mkdir(parents=True,exist_ok=True)
    for path in SOURCE.glob('*.json'):
        old=json.loads(path.read_text()); pools=[]
        for p in old['pools']:
            pool={'rolls':number(p['rolls']),'entries':[v for e in p['entries'] for v in convert_entry(e)]}
            if p.get('conditions'):
                pool['conditions']=[{'condition':'minecraft:random_chance','chance':c['chance']} for c in p['conditions']]
            pools.append(pool)
        bonus = structure_bonus_pool(path.stem)
        if bonus is not None: pools.append(bonus)
        (DEST/path.name).write_text(json.dumps({'type':'minecraft:chest','pools':pools},indent=2)+'\n')
    # Upstream has a Cryonics marker but ships no corresponding table. Its
    # closest available set of medical/sample supplies is the laboratory.
    (DEST/'cryonics.json').write_text(json.dumps({'type':'minecraft:chest','pools':[{'rolls':1,'entries':[{'type':'minecraft:loot_table','name':'jurassicraft:structure/visitor_centre/laboratory'}]}]},indent=2)+'\n')
if __name__=='__main__': convert()
