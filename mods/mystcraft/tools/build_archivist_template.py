from pathlib import Path
import struct,gzip
blocks={}
def put(x,y,z,name,props=None,nbt=None):
 blocks[(x,y,z+1)]=(name,props or {},nbt)
def fill(x1,y1,z1,x2,y2,z2,name,props=None):
 for x in range(x1,x2+1):
  for y in range(y1,y2+1):
   for z in range(z1,z2+1):put(x,y,z,name,props)
air='minecraft:air';oak='minecraft:oak_planks';cobble='minecraft:cobblestone'
fill(1,1,1,8,6,6,air);fill(0,7,0,8,11,6,air)
fill(1,0,1,7,0,5,oak)
fill(0,0,0,0,3,5,cobble);fill(0,0,0,7,3,0,cobble);fill(1,2,0,7,2,0,oak)
fill(8,0,0,8,3,5,cobble);fill(8,2,1,8,2,5,oak);fill(8,3,2,8,3,4,oak)
fill(0,0,6,8,3,6,cobble)
fill(0,6,3,8,6,3,oak);fill(0,5,2,8,5,4,oak);fill(0,4,1,8,4,5,oak);fill(2,4,2,6,4,4,air)
for i in range(-1,3):
 for j in range(9):
  put(j,4+i,i,'minecraft:oak_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
  put(j,4+i,6-i,'minecraft:oak_stairs',{'facing':'south','half':'bottom','shape':'straight','waterlogged':'false'})
for x,y,z in [(2,2,0),(3,2,0),(8,2,2),(8,2,3),(8,2,4)]:put(x,y,z,'minecraft:glass_pane',{'north':'false','south':'false','east':'false','west':'false','waterlogged':'false'})
for half,y in [('lower',1),('upper',2)]:put(6,y,0,'minecraft:oak_door',{'facing':'north','half':half,'hinge':'left','open':'false','powered':'false'})
put(6,0,-1,'minecraft:cobblestone_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
for x,y,z,facing in [(1,3,1,'east'),(6,4,2,'north'),(5,3,5,'south')]:put(x,y,z,'minecraft:wall_torch',{'facing':facing})
put(6,1,4,'minecraft:oak_fence',{'north':'false','south':'false','east':'false','west':'false','waterlogged':'false'});put(6,2,4,'minecraft:oak_pressure_plate',{'powered':'false'})
for x,y,z,facing in [(6,1,5,'north'),(7,1,5,'north'),(7,1,4,'east')]:put(x,y,z,'minecraft:oak_stairs',{'facing':facing,'half':'bottom','shape':'straight','waterlogged':'false'})
for x,y,z in [(1,1,1),(1,1,2),(1,1,5),(1,2,5)]:put(x,y,z,oak)
for x,y,z in [(1,1,3),(1,2,3),(1,1,4),(1,2,4),(2,1,5),(2,2,5),(3,1,5),(3,2,5)]:put(x,y,z,'minecraft:bookshelf')
for x,y,z,action in [(1,2,1,'lectern'),(1,2,2,'lectern'),(4,1,1,'desk_head'),(4,1,2,'desk_foot'),(4,2,4,'archivist')]:
 put(x,y,z,'minecraft:structure_block',{'mode':'data'},{'id':'minecraft:structure_block','mode':'DATA','metadata':action})
# The source's front roof overhang occupies local z=-1, hence the one-cell template Z shift.
blocks[(6,1,0)]=('minecraft:jigsaw',{'orientation':'north_up'},{'id':'minecraft:jigsaw','name':'minecraft:building_entrance','target':'minecraft:street','pool':'minecraft:empty','final_state':'minecraft:air','joint':'aligned'})
palette=[];lookup={};entries=[]
for pos,(name,props,nbt) in sorted(blocks.items()):
 key=(name,tuple(sorted(props.items())))
 if key not in lookup:
  lookup[key]=len(palette);state={'Name':name}
  if props:state['Properties']=props
  palette.append(state)
 e={'pos':list(pos),'state':lookup[key]}
 if nbt:e['nbt']=nbt
 entries.append(e)
root={'DataVersion':3120,'size':[9,12,9],'palette':palette,'blocks':entries,'entities':[]}
def kind(v):return 3 if isinstance(v,int) else 8 if isinstance(v,str) else 9 if isinstance(v,list) else 10
def text(v):
 b=v.encode();return struct.pack('>H',len(b))+b
def payload(v):
 t=kind(v)
 if t==3:return struct.pack('>i',v)
 if t==8:return text(v)
 if t==9:return bytes([kind(v[0]) if v else 0])+struct.pack('>i',len(v))+b''.join(payload(x) for x in v)
 return b''.join(bytes([kind(x)])+text(k)+payload(x) for k,x in v.items())+b'\0'
p=Path('mods/mystcraft/src/main/resources/data/mystcraft/structures/village/archivist_house.nbt')
p.write_bytes(gzip.compress(b'\x0a\0\0'+payload(root),mtime=0))
print(len(entries),'blocks',len(palette),'palette entries',p.stat().st_size,'bytes')
