#!/usr/bin/env python3
"""Original ICBM industrial visual assets. All pixels/geometry authored here; no upstream art."""
import json, struct, zlib
from pathlib import Path
from payload_visuals import (
    GLYPH_UV,
    GRENADE_IDS,
    PROFILES,
    catalog,
    core_pixels,
    disc_pixels,
    paint_label,
    radiation_pixels,
)
NS='icbm_port'
GLYPH={'north':GLYPH_UV,'south':GLYPH_UV,'east':GLYPH_UV,'west':GLYPH_UV}
def rgb(hex):return tuple(bytes.fromhex(hex.lstrip('#')))
def png(path,w,h,pixels):
 def chunk(k,d):return struct.pack('!I',len(d))+k+d+struct.pack('!I',zlib.crc32(k+d)&0xffffffff)
 path.parent.mkdir(parents=True,exist_ok=True)
 raw=b''.join(b'\0'+bytes(v for px in pixels[y*w:(y+1)*w] for v in (px if len(px)==4 else (*px,255))) for y in range(h))
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def generate(root):
 assets=root/'assets'/NS
 def put(path,data):
  p=assets/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2)+'\n')
 def texture(id,pixel,frames=1):
  png(assets/f'textures/block/{id}.png',16,16*frames,[pixel(x,y%16,y//16) for y in range(16*frames) for x in range(16)])
  if frames>1:put(f'textures/block/{id}.png.mcmeta',{'animation':{'frametime':5,'interpolate':False}})
 metal=rgb('#34434d');edge=rgb('#18242d');light=rgb('#708691');dark=rgb('#101c25');accent=rgb('#e5ad41');screen=rgb('#43dfc3')
 texture('casing',lambda x,y,f:edge if x in (0,15) or y in (0,15) else light if x in (2,13) and y in (2,13) else tuple(min(255,c+((x*11+y*7)%3)*3) for c in metal))
 texture('alloy',lambda x,y,f:rgb('#a9bac0') if (x+y)%7 else rgb('#8299a2'))
 texture('carbon',lambda x,y,f:rgb('#14212a') if (x//2+y//2)%2 else rgb('#24343e'))
 texture('hazard',lambda x,y,f:accent if ((x+y)//3)%2 else edge)
 texture('grille',lambda x,y,f:dark if y%3<2 and 2<x<13 else light)
 texture('coil',lambda x,y,f:rgb('#b76e42') if y%4<3 else rgb('#623c2e'))
 texture('optic',lambda x,y,f:screen if 3<=x<=12 and 3<=y<=12 else edge)
 texture('panel',lambda x,y,f:edge if x in (0,15) or y in (0,15) else screen if ((x==4+f*2 and 3<=y<=12) or (y==8 and 3<=x<=12) or (x==11 and y==4)) else rgb('#103c40'),4)
 texture('warning',lambda x,y,f:accent if (y>=4 and y<=12 and abs(x-8)<=((y-3)//2)) else edge)
 texture('exhaust',lambda x,y,f:(255,236,170) if 6<=x<=9 and 6<=y<=8+f else (243,150,48) if 5<=x<=10 and 5<=y<=10 else edge if 3<=x<=12 and 3<=y<=12 else metal,4)
 textures={k:NS+':block/'+k for k in ['casing','alloy','carbon','hazard','grille','coil','optic','panel','warning','exhaust']}
 class Model:
  def __init__(self):self.elements=[];self.tex=dict(textures);self.tex['particle']=textures['casing']
  def box(self,lo,hi,tex='casing',rotation=None,face=None,uv=None):
   faces={}
   for side in ['up','down','north','south','east','west']:
    entry={'texture':'#'+(face.get(side,tex) if face else tex)}
    # A short element without uv samples v = 16 - y and hides the glyph.
    if uv and side in uv:entry['uv']=uv[side]
    faces[side]=entry
   e={'from':lo,'to':hi,'faces':faces}
   if rotation:e['rotation']=rotation
   self.elements.append(e);return self
  def save(self,path,item=False):
   data={'parent':'minecraft:block/block','textures':self.tex,'elements':self.elements}
   if item:data['display']={'gui':{'rotation':[25,-35,-15],'scale':[.8,.8,.8]},'firstperson_righthand':{'rotation':[0,-35,0],'translation':[0,1,-1],'scale':[.65,.65,.65]},'thirdperson_righthand':{'rotation':[0,0,0],'scale':[.65,.65,.65]},'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]}}
   if path in ['item/rocket_launcher','item/ballistic_launcher']:
    # Keep the vanilla BOW use animation while preserving the crosshair view.
    data['display']['firstperson_righthand']={'rotation':[0,-35,0],'translation':[0,-1,-3],'scale':[.43,.43,.43]}
    data['display']['firstperson_lefthand']={'rotation':[0,35,0],'translation':[0,-1,-3],'scale':[.43,.43,.43]}
   put('models/'+path+'.json',data)
  def base(self):return self.box([0,0,0],[16,3,16],'carbon').box([1,3,1],[15,5,15],'casing').box([0,1,0],[16,2,16],'hazard')
 def launcher(id,tier=1,loaded=False):
  m=Model().base();m.box([2,5,2],[14,7,14]);m.box([2,7,0],[5,14,16]);m.box([11,7,0],[14,14,16]);m.box([5,12,0],[11,14,16]);m.box([5,7,0],[11,9,16],'carbon')
  m.box([4,6,0],[12,7,15],'alloy');m.box([6,9,14],[10,12,16],'grille');m.box([0,5,5],[2,10,11],face={'west':'panel'})
  for i in range(tier):m.box([14,7+i*2,3],[15,8+i*2,7],'hazard')
  if loaded:m.box([6,9,1],[10,12,12],'alloy').box([7,10,0],[9,11,1],'warning')
  m.save('block/'+id)
 launcher('launcher');launcher('launcher_loaded',loaded=True)
 def vertical_launcher(id,tier,loaded):
  m=Model().base()
  for lo,hi in [([3,5,3],[5,16,13]),([11,5,3],[13,16,13]),([5,5,3],[11,16,5]),([5,5,11],[11,16,13])]:m.box(lo,hi,'casing')
  m.box([2,14,2],[5,16,14],'alloy').box([11,14,2],[14,16,14],'alloy')
  m.box([5,14,2],[11,16,5],'alloy').box([5,14,11],[11,16,14],'alloy')
  m.box([0,5,5],[3,11,11],face={'west':'panel'})
  for i in range(tier):m.box([13,6+i*2,5],[14,7+i*2,10],'hazard')
  if loaded:m.box([6,6,6],[10,13,10],'alloy').box([6,11,6],[10,12,10],'hazard').box([7,13,7],[9,16,9],'alloy')
  m.save('block/'+id)
 for tier in range(1,4):
  id=f'launcher_base_tier_{tier}';vertical_launcher(id,tier,False);vertical_launcher(id+'_loaded',tier,True)
  variants={}
  for facing,rotation in {'up':{},'down':{'x':180},'north':{'x':90},'south':{'x':90,'y':180},'east':{'x':90,'y':90},'west':{'x':90,'y':270}}.items():
   for loaded in [False,True]:variants[f'facing={facing},loaded={str(loaded).lower()}']={'model':NS+':block/'+id+('_loaded' if loaded else ''),**rotation}
  put('blockstates/'+id+'.json',{'variants':variants})
 launcher('cruise_launcher',2);launcher('cruise_launcher_loaded',2,True)
 state=json.loads((assets/'blockstates/launcher.json').read_text())
 for v in state['variants'].values():v['model']=NS+':block/cruise_launcher'+('_loaded' if v['model'].endswith('_loaded') else '')
 put('blockstates/cruise_launcher.json',state)
 # Radar dish: a stepped concave antenna with a center feed horn.
 m=Model().base();m.box([5,5,5],[11,10,11]);m.box([2,10,7],[14,12,10],'alloy');m.box([0,12,6],[16,16,8],'alloy');m.box([0,9,5],[2,16,7],'alloy');m.box([14,9,5],[16,16,7],'alloy');m.box([3,9,5],[13,11,7],'alloy');m.box([7,11,2],[9,13,7],'carbon').box([6,10,1],[10,14,2],'optic');m.box([2,4,0],[7,7,1],face={'north':'panel'});m.save('block/radar_station')
 # SAM: four distinct dark apertures in two tubes, mounted on a central pedestal.
 m=Model().base();m.box([5,5,5],[11,8,11],'carbon')
 for x in [1,9]:
  m.box([x,8,1],[x+6,14,15]);m.box([x+1,9,0],[x+5,13,1],'carbon');m.box([x+2,10,-.01],[x+4,12,0],'exhaust');m.box([x,13,3],[x+6,14,5],'hazard')
 m.save('block/sam_launcher')
 # Animated heads are reserved for the client block-entity renderers. Inventory
 # parents retain complete silhouettes when no renderer is active.
 for machine in ['radar_station','sam_launcher']:
  full=json.loads((assets/f'models/block/{machine}.json').read_text())
  put('models/block/'+machine+'_inventory.json',full)
  put('models/item/'+machine+'.json',{'parent':NS+':block/'+machine+'_inventory'})
  base=Model().base().box([5,5,5],[11,15,11],'casing').box([4,15,4],[12,16,12],'carbon')
  base.box([2,4,0],[7,7,1],face={'north':'panel'})
  base.save('block/'+machine)
 # EMP coil mast: copper rings and insulated ceramic core.
 for id,extension in [('emp_tower',False),('emp_tower_extension',True)]:
  m=Model().base();m.box([6,5,6],[10,15,10],'alloy')
  for y in [6,9,12]:m.box([3,y,3],[13,y+1.5,13],'coil')
  m.box([4,15,4],[12,16,12],'alloy');m.box([0,5,4],[2,10,12],face={'west':'panel'});m.save('block/'+id)
 for id in ['launcher_frame','launcher_support','launcher_screen','launcher_connector']:
  m=Model().base()
  if id=='launcher_frame':
   for x in [1,12]:m.box([x,3,1],[x+3,16,4],'alloy').box([x,3,12],[x+3,16,15],'alloy')
   m.box([1,13,1],[15,16,4],'casing').box([1,13,12],[15,16,15],'casing')
  elif id=='launcher_screen':m.box([6,5,7],[10,9,11],'alloy').box([1,8,5],[15,16,9],'casing',face={'north':'panel'}).box([3,5,1],[13,6,6],'carbon')
  elif id=='launcher_support':m.box([2,4,2],[14,13,14],face={'north':'grille'}).box([5,13,5],[11,16,11],'coil')
  else:
   m.box([4,4,4],[12,11,12],'alloy')
   for lo,hi in [([0,5,6],[16,9,10]),([6,5,0],[10,9,16])]:m.box(lo,hi,'carbon')
   m.box([5,11,5],[11,12,11],'hazard')
  m.save('block/'+id)
 def label_box(m,lo,hi,tex='label'):
  m.box(lo,hi,tex,face={'up':'casing','down':'casing','north':'label','south':'label','east':'label','west':'label'},uv=GLYPH)
 def fins(m,y0=1,y1=6):
  for lo,hi in [([1,y0,7],[5,y1,9]),([11,y0,7],[15,y1,9]),([7,y0,1],[9,y1,5]),([7,y0,11],[9,y1,15])]:m.box(lo,hi,'carbon')
 def missile(profile):
  m=Model();m.tex['label']=NS+':block/payload_'+profile.id
  m.box([5,0,5],[11,2,11],'exhaust')
  body=profile.body
  if body=='canister':
   label_box(m,[4,2,4],[12,13,12]);m.box([5,13,5],[11,15,11],'alloy');m.box([7,15,7],[9,16,9],'carbon');fins(m,0,4)
  elif body=='warhead':
   m.box([6,2,6],[10,8,10],'alloy');m.box([4,7,4],[12,13,12],'casing');label_box(m,[3.8,8,3.8],[12.2,12,12.2])
   m.box([6,13,6],[10,15,10],'carbon');m.box([7,15,7],[9,16,9],'alloy');fins(m)
  elif body=='flame':
   m.box([6,2,6],[10,11,10],'alloy');label_box(m,[5.1,3,5.1],[10.9,10,10.9])
   m.box([4,11,4],[12,14,12],'warning');m.box([6,14,6],[10,16,10],'exhaust');fins(m)
  elif body=='core':
   m.box([6,2,6],[10,5,10],'alloy');m.box([4,5,4],[12,11,12],'carbon');label_box(m,[3.7,6.5,3.7],[12.3,9.5,12.3])
   m.box([6,11,6],[10,13,10],'alloy');m.box([7,13,7],[9,16,9],'casing');fins(m,1,5)
  elif body=='coil':
   m.box([6.5,2,6.5],[9.5,14,9.5],'alloy')
   for y in (4,8,12):m.box([4,y,4],[12,y+1.2,12],'coil')
   label_box(m,[5.4,6,5.4],[10.6,8,10.6]);m.box([7,14,7],[9,16,9],'optic');fins(m)
  else:
   m.box([6,2,6],[10,12,10],'alloy');label_box(m,[5.05,4,5.05],[10.95,11,10.95])
   m.box([6.5,12,6.5],[9.5,14,9.5],'carbon');m.box([7,14,7],[9,16,9],'alloy');fins(m)
  m.save('item/'+profile.id+'_missile',True)
 for profile in PROFILES:
  png(assets/f'textures/block/payload_{profile.id}.png',16,16,paint_label(profile))
  m=Model();m.tex['label']=NS+':block/payload_'+profile.id
  m.box([1,0,1],[15,16,15],face={'north':'label','south':'label','east':'label','west':'label','up':'warning','down':'casing'},uv=GLYPH)
  for x in [0,14]:
   for z in [0,14]:m.box([x,0,z],[x+2,16,z+2],'carbon')
  m.box([0,0,0],[16,2,16],'alloy').box([0,14,0],[16,16,16],'alloy');m.box([4,16,6],[12,16.5,10],'carbon');m.save('block/'+profile.id+'_explosive')
  missile(profile)
  if profile.id in GRENADE_IDS:
   for suffix in ['_grenade','_bomblet']:
    m=Model();m.tex['label']=NS+':block/payload_'+profile.id
    label_box(m,[4,2,4],[12,11,12]);m.box([5,11,5],[11,13,11],'alloy');m.box([8,13,6],[12,14,10],'carbon');m.box([10,11,6],[13,13,10],'hazard')
    m.save('item/'+profile.id+suffix,True)
 # Purpose-built held tool silhouettes, rather than recognizable vanilla tools.
 for id in ['rocket_launcher','ballistic_launcher','radar_gun','laser_designator','gps','radio_detonator','defuser','antidote','battery']:
  m=Model()
  if id in ['rocket_launcher','ballistic_launcher']:
   m.box([4,6,0],[12,13,16],'casing').box([5,7,-.02],[11,12,0],'carbon').box([6,1,9],[9,6,13],'carbon').box([5,4,2],[11,6,5],'alloy').box([8,13,7],[11,15,11],'optic');m.box([3,7,4],[4,12,8],'hazard')
   if id=='ballistic_launcher':m.box([3,13,3],[7,16,9],'casing').box([2.9,14,4],[3,15,8],'panel')
  elif id in ['radar_gun','laser_designator']:
   m.box([5,7,2],[11,12,14],'casing').box([6,3,9],[9,7,13],'carbon').box([6,8,0],[10,11,2],'optic').box([7,12,8],[9,14,12],'alloy')
  elif id in ['gps','radio_detonator']:
   m.box([4,1,5],[12,13,10],'casing').box([5,5,4.9],[11,11,5],'panel').box([6,2,4.8],[10,4,5],'hazard');m.box([10,13,7],[11,16,8],'alloy')
  elif id=='defuser':m.box([6,1,6],[10,10,10],'casing').box([5,10,5],[7,15,7],'alloy').box([9,10,9],[11,15,11],'alloy').box([6,3,5],[10,7,6],'hazard')
  elif id=='antidote':m.box([5,1,5],[11,11,11],'alloy').box([6,11,6],[10,14,10],'carbon').box([5,5,4.9],[11,8,5],'optic')
  else:m.box([4,1,4],[12,13,12],'casing').box([5,13,6],[7,15,10],'alloy').box([9,13,6],[11,15,10],'coil').box([4.9,4,3.9],[11,10,4],'hazard')
  m.save('item/'+id,True)
 # Retexture special rocket geometry as well without changing manifests or item IDs.
 for id in ['cluster_missile','held_item_missile','sam_missile','missile_module']:
  m=Model();m.box([5,2,5],[11,12,11],'casing').box([4.9,7,4.9],[11.1,10,11.1],'hazard' if id=='cluster_missile' else 'optic').box([6,12,6],[10,14,10],'alloy').box([7,14,7],[9,16,9],'alloy').box([5,0,5],[11,2,11],'exhaust')
  for x,z in [(2,7),(11,7),(7,2),(7,11)]:m.box([x,1,z],[x+3,5,z+3],'carbon')
  m.save('item/'+id,True)
 # Original transparent component sprites: no vanilla item texture substitutes.
 def component(id):
  canvas=[(0,0,0,0)]*256
  def rect(x0,y0,x1,y1,color):
   c=rgb(color) if isinstance(color,str) else color
   for y in range(max(0,y0),min(16,y1)):
    for x in range(max(0,x0),min(16,x1)):canvas[y*16+x]=(*c,255) if len(c)==3 else c
  def dot(x,y,c):rect(x,y,x+1,y+1,c)
  if id.endswith('_circuit'):
   tier=['basic_circuit','advanced_circuit','elite_circuit'].index(id)
   board=['#234f47','#284967','#54375f'][tier];trace=['#65c69d','#7ec3e1','#d5a8ea'][tier]
   rect(3,2,13,14,'#152730');rect(4,3,12,13,board)
   for y in [3,6,9,12]:rect(1,y,4,y+1,'#d7ad57');rect(12,y,15,y+1,'#d7ad57')
   for x in [5,8,11]:rect(x,1,x+1,3,'#d7ad57');rect(x,13,x+1,15,'#d7ad57')
   rect(6,5,10,10,'#17232d');rect(7,6,9,9,'#8194a0')
   for y in range(4,12,3):rect(4,y,6,y+1,trace);rect(10,y+1,12,y+2,trace)
   for x in range(tier+1):dot(5+x*2,11,trace)
  elif id.endswith('_wire'):
   copper=id.startswith('copper');c='#d18d55' if copper else '#e5be55';shine='#f6bc89' if copper else '#fff09a'
   rect(3,3,13,13,'#162733');rect(2,4,14,12,'#263b46')
   for y in [4,6,8,10]:rect(3,y,13,y+1,c);rect(4,y,6,y+1,shine)
   rect(1,3,3,13,'#7a909a');rect(13,3,15,13,'#7a909a');rect(9,12,11,15,c);rect(10,14,14,15,c)
  elif id in ['steel_plate','iron_plate']:
   shade='#7b929e' if id=='steel_plate' else '#b9c8ce';shine='#d8e3e6' if id=='iron_plate' else '#afc6cf'
   rect(2,5,13,13,'#223842');rect(3,3,14,12,shade);rect(4,4,13,5,shine);rect(4,5,5,10,shine)
   for x,y in [(5,6),(11,6),(5,10),(11,10)]:dot(x,y,'#314652')
  elif id=='steel_ingot':
   rect(2,7,14,12,'#2c424e');rect(3,5,13,11,'#7d98a6');rect(4,4,12,6,'#b9cbd2');rect(3,6,5,10,'#a1bbc7');rect(10,6,13,10,'#5a7483');rect(6,7,10,9,'#5c8e96')
  elif id=='steel_clump':
   for x,y in [(2,6),(6,3),(9,6),(6,9)]:
    rect(x,y,x+5,y+4,'#263c47');rect(x+1,y,x+4,y+3,'#667f8b');dot(x+1,y,'#bdd0d6')
  elif id=='empty_bomblet':
   rect(5,4,11,14,'#263c47');rect(4,5,12,12,'#718b98');rect(6,5,10,12,'#152630');rect(5,2,11,4,'#a6bbc5');rect(6,1,10,2,'#d2e0e3');rect(5,12,11,14,'#abc0ca');dot(5,6,'#d8e3e6')
  elif id=='saltpeter_ball':
   for y in range(3,13):
    for x in range(3,13):
     if (x-7.5)**2+(y-7.5)**2<24:dot(x,y,'#d4ddd0' if (x+y)%3 else '#a3b6ac')
   rect(5,4,8,5,'#f2f2d9');dot(4,6,'#f2f2d9');rect(7,10,10,11,'#809b96')
  else:
   color={'sulfur_dust':'#e7c95a','saltpeter_dust':'#d6e5cf','poison_powder':'#94c960'}[id]
   rect(4,4,12,13,'#253e49');rect(3,6,13,12,'#34535a');rect(5,3,11,5,'#a5bbc0');rect(6,2,10,3,'#e0e7df')
   rect(5,6,11,11,color);rect(6,7,10,10,'#243943')
   for x,y in [(3,13),(5,14),(10,14),(12,13)]:dot(x,y,color)
  png(assets/f'textures/item/{id}.png',16,16,canvas)
  put('models/item/'+id+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':NS+':item/'+id}})
 png(assets/'textures/block/radiation.png',16,16,radiation_pixels())
 png(assets/'textures/entity/redmatter_core.png',16,16,core_pixels())
 png(assets/'textures/entity/redmatter_disc.png',16,16,disc_pixels())
 put('payload_visuals.json',catalog())
 for block_id,base in [('radioactive_stone','stone'),('radioactive_dirt','dirt')]:
  put('models/block/'+block_id+'.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/'+base,'base':'minecraft:block/'+base,'mark':NS+':block/radiation'},'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{face:{'texture':'#base'} for face in ['up','down','north','south','east','west']}},{'from':[5,16.01,5],'to':[11,16.02,11],'faces':{'up':{'texture':'#mark','uv':GLYPH_UV}}}]})
 for id in ['basic_circuit','advanced_circuit','elite_circuit','copper_wire','gold_wire','steel_plate','iron_plate','steel_ingot','steel_clump','empty_bomblet','sulfur_dust','saltpeter_dust','poison_powder','saltpeter_ball']:component(id)
 # Payload-marked rail carts share industrial geometry, never the vanilla TNT cart sprite.
 for profile in PROFILES:
  payload=profile.id
  m=Model();m.tex['label']=NS+':block/payload_'+payload
  m.box([1,3,2],[15,6,14],'carbon').box([1,5,2],[15,7,14],'alloy')
  for x in [0,13]:
   for z in [2,10]:m.box([x,1,z],[x+3,4,z+4],'carbon')
  label_box(m,[2,7,3],[14,13,13]);m.box([2,13,3],[14,14,13],'alloy')
  m.box([2,7,2],[14,9,3],'casing').box([2,7,13],[14,9,14],'casing')
  for x in [3,11]:m.box([x,6,1],[x+2,8,15],'hazard')
  m.box([5,14,6],[11,15,10],'warning');m.save('item/'+payload+'_bomb_cart',True)
 # Practical family guidance is part of the reproducible visual/content layer.
 lang_path=assets/'lang/en_us.json'
 lang=json.loads(lang_path.read_text())
 lang.update({'tooltip.icbm_port.payload.condensed': 'A compact terrain blast for straightforward demolition.', 'tooltip.icbm_port.payload.shrapnel': 'Scatters burning projectiles to strike exposed targets around the impact.', 'tooltip.icbm_port.payload.incendiary': 'Ignites nearby targets and scatters fire across exposed ground.', 'tooltip.icbm_port.payload.debilitation': 'Leaves poisonous gas that slows unprotected targets and causes mining fatigue.', 'tooltip.icbm_port.payload.chemical': 'Lingering toxic gas builds up damage through repeated unprotected exposure.', 'tooltip.icbm_port.payload.anvil': 'Showers the area with falling anvils that damage creatures below.', 'tooltip.icbm_port.payload.repulsive': 'Pushes nearby entities away; use it to shove a redmatter core off course.', 'tooltip.icbm_port.payload.attractive': 'Pulls nearby entities inward to bunch targets together for an area attack.', 'tooltip.icbm_port.payload.fragmentation': 'Scatters explosive fragments and projectiles across the surrounding area.', 'tooltip.icbm_port.payload.contagious': 'Spreads viral exposure between nearby creatures and can mutate pigs or villagers.', 'tooltip.icbm_port.payload.sonic': 'A force wave tears up terrain and throws loose blocks outward.', 'tooltip.icbm_port.payload.breaching': 'Cuts a narrow tunnel toward the most resistant wall beside the blast.', 'tooltip.icbm_port.payload.thermobaric': 'A large, destructive terrain blast for clearing a wide area.', 'tooltip.icbm_port.payload.nuclear': 'A massive blast leaves radioactive fallout and can mutate nearby creatures.', 'tooltip.icbm_port.payload.emp': 'Disrupts powered ICBM machines and disables nearby missiles without a terrain blast.', 'tooltip.icbm_port.payload.exothermic': 'Evaporates water and ice, turning common terrain into lava, magma or netherrack.', 'tooltip.icbm_port.payload.endothermic': 'Freezes water, solidifies lava and extinguishes fire across the affected terrain.', 'tooltip.icbm_port.payload.antigravitational': 'Lifts terrain and nearby entities into a sustained low-gravity field.', 'tooltip.icbm_port.payload.ender': 'Targeted missiles relocate terrain and entities; other forms scatter nearby entities.', 'tooltip.icbm_port.payload.antimatter': 'Erases a large area and annihilates redmatter cores caught in the blast.', 'tooltip.icbm_port.payload.redmatter': 'A hungry core pulls in terrain to grow and merges with other redmatter cores.', 'tooltip.icbm_port.payload.colors': 'Leaves a harmless colored particle cloud to mark an impact or celebrate.', 'tooltip.icbm_port.payload.smoke': 'A short-lived smoke plume marks the impact without a terrain blast.', 'tooltip.icbm_port.redmatter_counter': 'Use antimatter to collapse the core. Repulsive blasts can push it away.', 'death.attack.icbm_port.redmatter': '%1$s was consumed by a redmatter core', 'death.attack.icbm_port.redmatter.player': "%1$s was consumed by %2$s's redmatter core"})
 put('lang/en_us.json',lang)
if __name__=='__main__':generate(Path(__file__).resolve().parents[1]/'src/main/resources')
