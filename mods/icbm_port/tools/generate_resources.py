#!/usr/bin/env python3
"""Original prototype resources using only vanilla texture references; no upstream art.
Source-mapped recipe overrides and explicit adaptations are documented in docs/RECIPES.md.
"""
import json, zipfile, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
NS='icbm_port'
PAYLOADS='condensed shrapnel incendiary debilitation chemical anvil repulsive attractive fragmentation contagious sonic breaching thermobaric nuclear emp exothermic endothermic antigravitational ender antimatter redmatter colors smoke'.split()
MATERIALS='gunpowder iron_nugget blaze_powder fermented_spider_eye spider_eye anvil piston slime_ball iron_ingot rotten_flesh note_block obsidian fire_charge nether_star redstone magma_cream blue_ice phantom_membrane ender_pearl dragon_breath nether_star red_dye gray_dye'.split()
COLORS='white light_gray orange purple lime gray blue pink gray green cyan black red lime blue orange light_blue magenta purple black red magenta gray'.split()
def put(path,value):
 p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
lang=json.loads((ROOT/'assets/icbm_port/lang/en_us.json').read_text())
def name(id,block=False,title=None):lang[f'{"block" if block else "item"}.{NS}.{id}']=title or id.replace('_',' ').title()
def model(id,texture):put(f'assets/{NS}/models/item/{id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'minecraft:item/{texture}'}})
def recipe(id,ingredients,count=1):
 put(f'data/{NS}/recipes/{id}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':i if ':' in i else 'minecraft:'+i} for i in ingredients],'result':{'item':NS+':'+id,'count':count}})
 unlock=ingredients[0] if ':' in ingredients[0] else 'minecraft:'+ingredients[0]
 put(f'data/{NS}/advancements/recipes/{id}.json',{'parent':'minecraft:recipes/root','criteria':{'has_ingredient':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[unlock]}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':NS+':'+id}}},'requirements':[['has_ingredient','has_the_recipe']],'rewards':{'recipes':[NS+':'+id]}})
def block(id,texture):
 put(f'assets/{NS}/blockstates/{id}.json',{'variants':{'':{'model':NS+':block/'+id}}})
 put(f'assets/{NS}/models/block/{id}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'minecraft:block/'+texture}})
 put(f'assets/{NS}/models/item/{id}.json',{'parent':NS+':block/'+id});name(id,True)
 put(f'data/{NS}/loot_tables/blocks/{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+':'+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for index,(payload,material,color) in enumerate(zip(PAYLOADS,MATERIALS,COLORS)):
 explosive=payload+'_explosive';missile=payload+'_missile'
 if payload!='condensed':
  block(explosive,color+'_concrete')
  # Original casing with a colored band, using vanilla texture identifiers only.
  put(f'assets/{NS}/models/block/{explosive}.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/iron_block','case':'minecraft:block/iron_block','band':'minecraft:block/'+color+'_concrete','top':'minecraft:block/tnt_top'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{face:{'texture':'#top' if face in ('up','down') else '#case'} for face in ['up','down','north','south','east','west']}},{'from':[-0.01,5,-0.01],'to':[16.01,11,16.01],'faces':{face:{'texture':'#band'} for face in ['north','south','east','west']}}]})
  recipe(explosive,['icbm_port:condensed_explosive',material]);model(missile,'firework_rocket');recipe(missile,['iron_ingot','icbm_port:'+explosive,'redstone']);name(missile)
 cart=payload+'_bomb_cart';model(cart,'tnt_minecart');recipe(cart,['icbm_port:'+explosive,'minecart']);name(cart)
 if index<8 or payload in ('colors','smoke'):
  grenade=payload+'_grenade';model(grenade,'fire_charge');recipe(grenade,['icbm_port:'+explosive,'iron_nugget'],4);name(grenade)
for id,tex in {'gps':'compass_00','laser_designator':'spyglass','radio_detonator':'recovery_compass_00','radar_gun':'compass_00','defuser':'shears','antidote':'honey_bottle','rocket_launcher':'crossbow_standby'}.items():
 model(id,tex);name(id)
for id,ingredients in {'gps':['compass','redstone'],'laser_designator':['spyglass','redstone'],'radio_detonator':['compass','redstone_torch'],'radar_gun':['compass','iron_ingot'],'defuser':['shears','redstone'],'antidote':['honey_bottle','milk_bucket'],'rocket_launcher':['bow','iron_ingot','iron_ingot','redstone']}.items():recipe(id,ingredients)
for id,texture,ingredients in [('radar_station','observer_top',['observer','compass','redstone']),('emp_tower','redstone_block',['lightning_rod','redstone_block','iron_block']),('sam_launcher','dispenser_front',['dispenser','iron_block','redstone']),('spikes','iron_block',['iron_ingot','iron_ingot','iron_ingot']),('concrete','gray_concrete',['gray_concrete','iron_nugget']),('reinforced_glass','glass',['glass','iron_ingot']),('glass_button','glass',['glass']),('glass_pressure_plate','glass',['glass','glass'])]:
 block(id,texture);recipe(id,ingredients)
lang['entity.icbm_port.grenade']='Grenade';lang['entity.icbm_port.bomb_cart']='Explosive Minecart'
lang['message.icbm_port.empty']='Use a missile on the launcher to load it.'
put(f'assets/{NS}/lang/en_us.json',lang)
blocks=[p.stem for p in (ROOT/f'assets/{NS}/blockstates').glob('*.json')]
put('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':[NS+':'+b for b in sorted(blocks) if not b.endswith('_explosive')]})

# Vanilla button/plate state rotations with original glass texture models.
vanilla=zipfile.ZipFile(sys.argv[1])
for id,base,parents in [('glass_button','stone_button',['button','button_pressed','button_inventory']),('glass_pressure_plate','stone_pressure_plate',['pressure_plate_up','pressure_plate_down'])]:
 state=json.loads(vanilla.read('assets/minecraft/blockstates/'+base+'.json'))
 for entry in state['variants'].values():
  old=entry['model'].split('/')[-1]
  suffix=old[len(base):]
  entry['model']='icbm_port:block/'+id+suffix
 put('assets/icbm_port/blockstates/'+id+'.json',state)
 for parent in parents:
  suffix=('_inventory' if parent=='button_inventory' else '_pressed' if parent=='button_pressed' else '_down' if parent=='pressure_plate_down' else '')
  put('assets/icbm_port/models/block/'+id+suffix+'.json',{'parent':'minecraft:block/'+parent,'textures':{'texture':'minecraft:block/glass'}})
 put('assets/icbm_port/models/item/'+id+'.json',{'parent':'icbm_port:block/'+id+('_inventory' if id=='glass_button' else '')})

# Source-recipe migration takes priority over the earlier prototype recipes.
# This mapping contains recipe data only, never upstream art or language files.
mapping=json.loads((Path(__file__).parent/'upstream_recipe_mapping.json').read_text())
components={'steel_clump':'raw_iron','steel_ingot':'iron_ingot','steel_plate':'iron_ingot','iron_plate':'iron_ingot','copper_wire':'copper_ingot','gold_wire':'gold_ingot','basic_circuit':'redstone','advanced_circuit':'repeater','elite_circuit':'comparator','battery':'redstone','poison_powder':'sugar','sulfur_dust':'glowstone_dust','saltpeter_ball':'clay_ball','saltpeter_dust':'sugar','missile_module':'firework_rocket','empty_bomblet':'iron_nugget'}
components.update({'held_item_missile':'firework_rocket','ballistic_launcher':'crossbow_standby'})
for id,texture in components.items():model(id,texture);name(id)
for id,texture in {'launcher_base_tier_1':'iron_block','launcher_base_tier_2':'gold_block','launcher_base_tier_3':'diamond_block','cruise_launcher':'dispenser_front','launcher_frame':'iron_block','launcher_support':'redstone_block','launcher_screen':'observer_front','launcher_connector':'hopper_outside'}.items():
 block(id,texture)
 if id.startswith('launcher_base') or id=='cruise_launcher':
  put('assets/icbm_port/blockstates/'+id+'.json',json.loads((ROOT/'assets/icbm_port/blockstates/launcher.json').read_text()))
for payload in PAYLOADS[:8]+['colors','smoke']:
 model(payload+'_bomblet','fire_charge');name(payload+'_bomblet')
 # Upstream publishes only condensed; extension to other payloads uses same pattern.
 if payload!='condensed':
  mapping[payload+'_bomblet']={'source':'adapted from bomblet/condensed.json','recipe':{'type':'minecraft:crafting_shaped','pattern':['mx'],'key':{'m':{'item':'icbm_port:empty_bomblet'},'x':{'item':'icbm_port:'+payload+'_explosive'}},'result':{'item':'icbm_port:'+payload+'_bomblet','count':1}}}
for id,texture in {'cluster_missile':'firework_rocket','cargo_parachute':'paper','cargo_balloon':'slime_ball'}.items():model(id,texture);name(id)
# New port-only controller/support and tier upgrade mappings have no upstream file.
for id,ingredients in {'launcher_support':['icbm_port:steel_plate','icbm_port:battery','icbm_port:copper_wire'],'launcher_base_tier_2':['icbm_port:launcher_base_tier_1','icbm_port:advanced_circuit','icbm_port:steel_plate'],'launcher_base_tier_3':['icbm_port:launcher_base_tier_2','icbm_port:elite_circuit','icbm_port:steel_plate']}.items():recipe(id,ingredients)
lang['entity.icbm_port.bomblet']='Bomblet'
lang['entity.icbm_port.sam_interceptor']='SAM Interceptor'
put('assets/icbm_port/lang/en_us.json',lang)
blocks=[p.stem for p in (ROOT/'assets/icbm_port/blockstates').glob('*.json')]
put('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['icbm_port:'+b for b in sorted(blocks) if not b.endswith('_explosive')]})

for id,texture in {'compact_concrete':'black_concrete','reinforced_concrete':'iron_block','poison_spikes':'mossy_cobblestone','fire_spikes':'magma'}.items():block(id,texture)
for id,pattern,key,count,source in [
 ('compact_concrete',['coc','oco','coc'],{'c':{'item':'icbm_port:concrete'},'o':{'item':'minecraft:obsidian'}},8,'concrete.1.json'),
 ('reinforced_concrete',['coc','oco','coc'],{'c':{'item':'icbm_port:compact_concrete'},'o':{'item':'minecraft:iron_ingot'}},8,'concrete.2.json'),
 ('poison_spikes',['c','i'],{'c':{'item':'icbm_port:poison_powder'},'i':{'item':'icbm_port:spikes'}},1,'spikes.1.json'),
 ('fire_spikes',['c','i'],{'c':{'item':'icbm_port:sulfur_dust'},'i':{'item':'icbm_port:spikes'}},1,'spikes.2.json')]:
 put('data/icbm_port/recipes/'+id+'.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':key,'result':{'item':'icbm_port:'+id,'count':count}})
 recipe_ingredient=key[next(iter(key))]['item']
 put('data/icbm_port/advancements/recipes/'+id+'.json',{'parent':'minecraft:recipes/root','criteria':{'has_ingredient':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[recipe_ingredient]}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'icbm_port:'+id}}},'requirements':[['has_ingredient','has_the_recipe']],'rewards':{'recipes':['icbm_port:'+id]}})
for id,texture in [('spikes','iron_block'),('poison_spikes','mossy_cobblestone'),('fire_spikes','magma')]:
 elements=[]
 for x,z in [(2,2),(10,2),(2,10),(10,10)]:
  for inset,y0,y1 in [(0,0,6),(1,6,11)]:
   elements.append({'from':[x+inset,y0,z+inset],'to':[x+4-inset,y1,z+4-inset],'faces':{face:{'texture':'#all'} for face in ['up','down','north','south','east','west']}})
 put('assets/icbm_port/models/block/'+id+'.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/'+texture,'all':'minecraft:block/'+texture},'elements':elements})
lang['entity.icbm_port.cargo']='Cargo Carrier'
put('assets/icbm_port/lang/en_us.json',lang)
blocks=[p.stem for p in (ROOT/'assets/icbm_port/blockstates').glob('*.json')]
put('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['icbm_port:'+b for b in sorted(blocks) if not b.endswith('_explosive')]})

put('data/icbm_port/recipes/cluster_loading.json',{'type':'icbm_port:cluster_loading'})
lang['tooltip.icbm_port.cluster_contents']='Bomblets: %s / %s'
lang['tooltip.icbm_port.cluster_load']='Craft with bomblets to load. Mixed payloads are supported.'
put('assets/icbm_port/lang/en_us.json',lang)

for serializer in ['held_item_loading','held_item_unloading']:put('data/icbm_port/recipes/'+serializer+'.json',{'type':'icbm_port:'+serializer})
lang['entity.icbm_port.held_item_missile']='Held Item Missile'
lang['tooltip.icbm_port.held_contents']='Held item: %s'
lang['tooltip.icbm_port.held_mode']='Action: %s'
put('assets/icbm_port/lang/en_us.json',lang)

# Original three-dimensional rockets: local +Y nose axis, centered at X/Z=8.
def rocket_model(id,color):
 elements=[]
 def box(lo,hi,texture):elements.append({'from':lo,'to':hi,'faces':{face:{'texture':'#'+texture} for face in ['up','down','north','south','east','west']}})
 box([5,2,5],[11,12,11],'case');box([4.99,8,4.99],[11.01,10,11.01],'band')
 box([6,12,6],[10,14,10],'band');box([7,14,7],[9,16,9],'tip')
 box([4,0,4],[12,2,12],'engine')
 for lo,hi in [([1,1,7],[5,6,9]),([11,1,7],[15,6,9]),([7,1,1],[9,6,5]),([7,1,11],[9,6,15])]:box(lo,hi,'fin')
 put('assets/icbm_port/models/item/'+id+'.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/iron_block','case':'minecraft:block/iron_block','band':'minecraft:block/'+color+'_concrete','tip':'minecraft:block/quartz_block_top','engine':'minecraft:block/blast_furnace_front','fin':'minecraft:block/gray_concrete'},'elements':elements,'display':{'gui':{'rotation':[25,-35,-20],'translation':[0,0,0],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-25,-20],'translation':[0,1,-2],'scale':[.55,.55,.55]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.6,.6,.6]},'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]}}})
for payload,color in zip(PAYLOADS,COLORS):rocket_model(payload+'_missile',color)
rocket_model('cluster_missile','yellow');rocket_model('held_item_missile','cyan')
for id,texture in [('radioactive_stone','stone'),('radioactive_dirt','dirt'),('emp_tower_extension','gold_block')]:
 block(id,texture)
 if id.startswith('radioactive'):
  put('assets/icbm_port/models/block/'+id+'.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/'+texture,'base':'minecraft:block/'+texture,'mark':'minecraft:block/lime_concrete'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{face:{'texture':'#base'} for face in ['up','down','north','south','east','west']}},{'from':[5,16.01,5],'to':[11,16.02,11],'faces':{'up':{'texture':'#mark'}}}]})
put('data/icbm_port/recipes/emp_tower_extension.json',{'type':'minecraft:crafting_shaped','pattern':['prp','ewe','prp'],'key':{'p':{'item':'icbm_port:steel_plate'},'r':{'item':'minecraft:redstone'},'e':{'item':'icbm_port:battery'},'w':{'item':'icbm_port:gold_wire'}},'result':{'item':'icbm_port:emp_tower_extension'}})
lang['entity.icbm_port.flying_block']='Flying Block'
lang['entity.icbm_port.redmatter_singularity']='Redmatter Singularity'
lang['entity.icbm_port.fragment']='Blast Fragment'
lang['entity.icbm_port.smoke']='Smoke Plume'
lang['death.attack.icbm_port.chemical']='%1$s succumbed to chemical gas'
lang['death.attack.icbm_port.chemical.player']='%1$s succumbed to chemical gas while fighting %2$s'
lang['death.attack.icbm_port.contagious']='%1$s succumbed to a viral cloud'
lang['death.attack.icbm_port.contagious.player']='%1$s succumbed to a viral cloud while fighting %2$s'
put('assets/icbm_port/lang/en_us.json',lang)
blocks=[p.stem for p in (ROOT/'assets/icbm_port/blockstates').glob('*.json')]
put('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['icbm_port:'+b for b in sorted(blocks) if not b.endswith('_explosive')]})
lang['tooltip.icbm_port.carrier_mode']='Cargo action: %s'
put('assets/icbm_port/lang/en_us.json',lang)
for serializer in ['carrier_loading','carrier_unloading']:put('data/icbm_port/recipes/'+serializer+'.json',{'type':'icbm_port:'+serializer})
lang['tooltip.icbm_port.carrier_mode']='Cargo mode: %s (sneak-use to change)'
put('assets/icbm_port/lang/en_us.json',lang)

rocket_model('sam_missile','light_blue');name('sam_missile','',title='Surface-to-Air Missile')
# Original physical cargo carriers, composed of vanilla-textured boxes.
for id in ['cargo_parachute','cargo_balloon']:
 elements=[]
 def cargo_box(lo,hi,tex):elements.append({'from':lo,'to':hi,'faces':{face:{'texture':'#'+tex} for face in ['up','down','north','south','east','west']}})
 if id=='cargo_parachute':
  cargo_box([0,11,0],[16,13,16],'cloth');cargo_box([2,13,2],[14,15,14],'cloth');cargo_box([5,15,5],[11,16,11],'cloth')
  for x,z in [(1,1),(14,1),(1,14),(14,14)]:cargo_box([x,2,z],[x+.5,11,z+.5],'rope')
 else:
  cargo_box([3,8,3],[13,14,13],'cloth');cargo_box([5,14,5],[11,16,11],'cloth');cargo_box([5,5,5],[11,8,11],'cloth')
  for x,z in [(5,5),(10,5),(5,10),(10,10)]:cargo_box([x,1,z],[x+.5,5,z+.5],'rope')
 cargo_box([5,0,5],[11,2,11],'basket')
 put('assets/icbm_port/models/item/'+id+'.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/white_wool','cloth':'minecraft:block/'+('white_wool' if id=='cargo_parachute' else 'red_wool'),'rope':'minecraft:block/oak_log','basket':'minecraft:block/oak_planks'},'elements':elements,'display':{'gui':{'rotation':[25,-35,0],'scale':[.8,.8,.8]},'ground':{'translation':[0,2,0],'scale':[.6,.6,.6]}}})
put('assets/icbm_port/lang/en_us.json',lang)

# Apply audited recipe data last: later model/variant generation must not flatten tags.
from material_tags import write_material_tags, write_recipe
write_material_tags(ROOT)
for identifier, entry in mapping.items():
 write_recipe(ROOT, identifier, entry["recipe"])

# Original artwork and geometry are the final visual layer; recipe generation is independent.
from visual_assets import generate as generate_visual_assets
generate_visual_assets(ROOT)
