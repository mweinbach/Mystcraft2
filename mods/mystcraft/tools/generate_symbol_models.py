from pathlib import Path
import re,json,shutil,hashlib,argparse,subprocess
from PIL import Image
parser=argparse.ArgumentParser(description="Rebuild classic symbol models from the pinned official source and retained catalog; requires Pillow")
parser.add_argument("legacy_checkout",type=Path)
args=parser.parse_args()
expected="9bc8ddc061845df0cd5ea47f4fdef773cff786ae"
actual=subprocess.check_output(["git","-C",str(args.legacy_checkout),"rev-parse","HEAD"],text=True).strip()
if actual != expected:parser.error("Expected official Legacy commit "+expected+", got "+actual)
module=Path(__file__).resolve().parents[1]
base=args.legacy_checkout/'src'; assets=module/'src/main/resources/assets/mystcraft' 
wordsrc=(base/'api/java/com/xcompwiz/mystcraft/api/word/WordData.java').read_text()
values={}
for name,value in re.findall(r'public static final String (\w+) = ([^;]+);',wordsrc):
 values[name]=value.strip('"') if value.startswith('"') else values[value]
words={values[name].lower():[int(x) for x in components.split(',')] for name,components in re.findall(r'registerWord\((\w+), new Integer\[\] \{([^}]+)\}',wordsrc)}
for i in range(26):
 first=63 if i>=20 else 62 if i>=15 else 61 if i>=10 else 60 if i>=5 else 0
 second=i%5+55 if i%5 else 0
 words[str(i)]=[1] if i==0 else [2] if i>=25 else [v for v in [first,second] if v]
class JavaRandom:
 def __init__(self,seed):self.seed=(seed^0x5DEECE66D)&((1<<48)-1)
 def next(self,n):self.seed=(self.seed*0x5DEECE66D+11)&((1<<48)-1);return self.seed>>(48-n)
 def nextInt(self,b):
  if b&(b-1)==0:return (b*self.next(31))>>31
  while True:
   bits=self.next(31);v=bits%b
   if bits-v+(b-1)<(1<<31):return v

def components(word):
 key=word.lower()
 if key not in words:
  h=0
  for c in key:h=((h*31)+ord(c))&0xffffffff
  if h>=1<<31:h-=1<<32
  r=JavaRandom(h); count=r.nextInt(10)+3
  maximum=20
  if key.startswith('easter'):count=4;maximum=8
  words[key]=[r.nextInt(maximum)+4 for _ in range(count)]
 return words[key]

def poem(entry):
 line=entry['source_registration']
 if entry['source_class']=='SymbolColor':return [values['Modifier'],values['Image'],values['Weave'],entry['id'].split(':')[1]]
 if entry['source_class']=='SymbolBlock':
  word=re.search(r'createMyst\(WordData\.(\w+)',line).group(1)
  return [values['Modifier'],values['Constraint'],values[word],entry['id'].split(':')[1]]
 tail=re.search(r'\), (?:null|\d+), (.*)\);',line).group(1)
 return [values[v.strip().split('.')[1]] if v.strip().startswith('WordData.') else v.strip().strip('"') for v in tail.split(',')]

catalog=json.loads((module/'docs/classic-symbols.json').read_text())
lang={k:v for line in (base/'main/resources/assets/mystcraft/lang/en_US.lang').read_text().splitlines() if '=' in line for k,v in [line.split('=',1)]}
atlas=Image.open(base/'main/resources/assets/mystcraft/textures/symbolcomponents.png').convert('RGBA')
background=Image.open(base/'main/resources/assets/mystcraft/textures/items/page_background.png').convert('RGBA').resize((160,160),Image.Resampling.NEAREST)
textures=assets/'textures/item/page'; textures.mkdir(parents=True,exist_ok=True)
models=assets/'models/item/page';models.mkdir(parents=True,exist_ok=True)
shutil.copyfile(base/'main/resources/assets/mystcraft/textures/symbolcomponents.png',assets/'textures/symbolcomponents.png')
positions=[(48,0),(96,48),(48,96),(0,48)]
def stamp(image,word,pos):
 for c in ([0] if word is None else components(word)):
  src=atlas.crop(((c%8)*64,(c//8)*64,(c%8+1)*64,(c//8+1)*64));alpha=src.getchannel('A')
  # Original PageBuilder.blend ignores atlas RGB, paints black and forces opacity on affected pixels.
  target=image.crop((pos[0],pos[1],pos[0]+64,pos[1]+64))
  pixels=target.load(); masks=alpha.load()
  for x in range(64):
   for y in range(64):
    a=masks[x,y]
    if a:pixels[x,y]=tuple(int(v*(1-a/255)) for v in pixels[x,y][:3])+(255,)
  image.paste(target,pos)
def save(name,image):
 image.resize((128,128),Image.Resampling.BILINEAR).save(textures/(name+'.png'))
 (models/(name+'.json')).write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'mystcraft:item/page/'+name}},indent=2)+'\n')
unknown=background.copy();stamp(unknown,None,(48,48));save('unknown',unknown)
panel=background.copy()
for x in range(25,136):
 for y in range(30,76):panel.putpixel((x,y),(0,0,0,255))
save('linkpanel',panel);save('blank',background.copy())
overrides=[{'predicate':{'mystcraft:page':1/256},'model':'mystcraft:item/page/unknown'},{'predicate':{'mystcraft:page':2/256},'model':'mystcraft:item/page/linkpanel'}]
for index,entry in enumerate(catalog['symbols'],3):
 entry['poem']=poem(entry);entry['glyph_components']=[components(w) for w in entry['poem']];entry['model_value']=index
 path=entry['id'].split(':')[1]
 label=lang.get('myst.symbol.'+path+'.name')
 if not label:label=re.sub(r'(?<=[a-z])(?=[A-Z])',' ',entry['legacy_id'].split(':')[1]).replace('ModMat_','Material: ').replace('_',' ')
 entry['display_name']=label
 image=background.copy()
 for word,pos in zip(entry['poem'],positions):stamp(image,word,pos)
 save(path,image)
 overrides.append({'predicate':{'mystcraft:page':index/256},'model':'mystcraft:item/page/'+path})
(assets/'models/item/page.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'mystcraft:item/page/blank'},'overrides':overrides},indent=2)+'\n')
catalog['word_components']=words
catalog['glyph_reference']={'atlas':'textures/symbolcomponents.png','atlas_sha256':hashlib.sha256((assets/'textures/symbolcomponents.png').read_bytes()).hexdigest(),'layout':'PageBuilder.PageSprite four64px words at (48,0),(96,48),(48,96),(0,48) on160px nearest5x original background then128px bilinear','fallback':'DrawableWordManager Java Random lowercase Java String.hashCode; unknown whole symbol uses component0 center','note':'Offline compositing preserves original component/word/poem mappings; seasonal/addon custom renderers and runtime resource-pack recomposition not implemented.'}
text=json.dumps(catalog,indent=2)+'\n'
(module/'docs/classic-symbols.json').write_text(text);(assets/'classic_symbols.json').write_text(text)
print('Generated',len(catalog['symbols']),'symbol models; words',len(words),'original atlas sha256',catalog['glyph_reference']['atlas_sha256'])
