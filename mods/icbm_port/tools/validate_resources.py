#!/usr/bin/env python3
"""Check JSON, model/texture references, recipe outputs and block loot coverage.
Pass the unmodified Minecraft 1.19.2 client jar as the first argument.
"""
import json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
vanilla=zipfile.ZipFile(sys.argv[1]); errors=[]; refs=0
files={str(p.relative_to(ROOT)):json.loads(p.read_text()) for p in ROOT.rglob('*.json')}
def exists(ref,kind,suffix='.json'):
 global refs
 refs+=1
 namespace,path=ref.split(':') if ':' in ref else ('minecraft',ref)
 key=f'assets/{namespace}/{kind}/{path}{suffix}'
 if not (key in vanilla.namelist() if namespace=='minecraft' else key in files):errors.append('Missing '+key)
for path,data in files.items():
 if '/models/' in path:
  if 'parent' in data:exists(data['parent'],'models')
  for texture in data.get('textures',{}).values():
   if not texture.startswith('#'):exists(texture,'textures','.png')
 if '/blockstates/' in path:
  def models(obj):
   if isinstance(obj,dict):
    for k,v in obj.items():
     if k=='model':exists(v,'models')
     else:models(v)
   elif isinstance(obj,list):
    for value in obj:models(value)
  models(data)
 if '/recipes/' in path and '/advancements/' not in path:
  def ingredients(obj):
   if isinstance(obj,dict):
    if 'item' in obj:yield obj['item']
    if 'tag' in obj:
     namespace,name=obj['tag'].split(':')
     key=f'data/{namespace}/tags/items/{name}.json'
     if not (key in vanilla.namelist() if namespace=='minecraft' else key in files):errors.append('Missing ingredient tag '+key)
    for value in obj.values():yield from ingredients(value)
   elif isinstance(obj,list):
    for value in obj:yield from ingredients(value)
  candidates=list(ingredients(data))
  if isinstance(data.get('result'),str):candidates.append(data['result'])
  for item in candidates:
   if item.startswith('icbm_port:'):exists(item.replace(':',':item/'),'models')
for state in (ROOT/'assets/icbm_port/blockstates').glob('*.json'):
 if f'data/icbm_port/loot_tables/blocks/{state.name}' not in files:errors.append('Missing block loot '+state.stem)
print(f'Validated {len(files)} JSON files and {refs} resource references against local resources and Minecraft 1.19.2.')
if errors:
 print('\n'.join(sorted(set(errors))));sys.exit(1)
print('PASS: JSON, model parents, vanilla textures, recipe item models, and block loot coverage.')

# Survival progression closure. Sulfur is supplied by the runtime mob-loot hook.
recipes=[data for path,data in files.items() if '/recipes/' in path and '/advancements/' not in path and 'result' in data]
obtainable={'icbm_port:sulfur_dust','icbm_port:radioactive_stone','icbm_port:radioactive_dirt'}
def available(value):
 if isinstance(value,list):return any(available(v) for v in value)
 if 'tag' in value:return True
 return not value['item'].startswith('icbm_port:') or value['item'] in obtainable
for _ in range(len(recipes)):
 for data in recipes:
  values=list(data.get('key',{}).values()) if 'key' in data else data.get('ingredients',[data['ingredient']] if 'ingredient' in data else [])
  if all(available(v) for v in values):
   result=data['result'];obtainable.add(result if isinstance(result,str) else result['item'])
blocked=sorted({(d['result'] if isinstance(d['result'],str) else d['result']['item']) for d in recipes}-obtainable)
if blocked:print('Unobtainable recipe outputs: '+', '.join(blocked));sys.exit(1)
print(f'PASS: all {len(recipes)} recipes have a survival ingredient path (sulfur via runtime loot hook).')

# Static catalog extraction for this module's registration conventions. This is
# deliberately explicit rather than claiming to execute arbitrary Java registries.
import re
java=ROOT.parent/'java/dev/icbm/port'
root=(java/'IcbmPort.java').read_text()
launcher=(java/'launcher/LauncherContent.java').read_text()
defense=(java/'defense/DefenseContent.java').read_text()
delivery=(java/'delivery/DeliveryContent.java').read_text()
materials=(java/'content/Materials.java').read_text()
exposure=(java/'exposure/ExposureContent.java').read_text()
enum=re.search(r'public\s+enum\s+PayloadType\s*\{(.*?);', (java/'payload/PayloadType.java').read_text(), re.S).group(1)
payloads=[]
for match in re.finditer(r'([A-Z][A-Z_]*)(?:\s*\(([^)]*)\))?',enum):
 name,args=match.groups()
 if args and 'false' in args:continue
 payloads.append(re.search(r'"([^" ]+)"',args).group(1) if args else name.lower())
tier_one=payloads[:8]+['colors','smoke']
blocks={p+'_explosive' for p in payloads}|{'launcher'}
for source in [launcher,defense,exposure]:blocks.update(re.findall(r'\bblock\s*\(\s*"([a-z0-9_]+)"',source))
items=set(blocks)|{p+'_missile' for p in payloads}|{p+'_bomb_cart' for p in payloads}|{p+suffix for p in tier_one for suffix in ['_grenade','_bomblet']}
for source in [root,launcher,defense,delivery,exposure]:
 items.update(re.findall(r'Registry\s*\.\s*register\s*\(\s*Registry\s*\.\s*ITEM\s*,\s*(?:IcbmPort\s*\.\s*)?id\s*\(\s*"([a-z0-9_]+)"',source))
items.update(re.findall(r'\bitem\s*\(\s*"([a-z0-9_]+)"',defense))
items.update(re.findall(r'"([a-z0-9_]+)"',re.search(r'new\s+String\s*\[\s*\]\s*\{([^}]*)\}', materials, re.S).group(1)))
entities=set()
for source in [root,launcher,defense,delivery,exposure]:entities.update(re.findall(r'Registry\s*\.\s*register\s*\(\s*Registry\s*\.\s*ENTITY_TYPE\s*,\s*(?:IcbmPort\s*\.\s*)?id\s*\(\s*"([a-z0-9_]+)"',source))
# Also inspect direct Identifier constructors used by simulation registration.
for source_file in java.rglob('*.java'):
 source_text=source_file.read_text()
 for registry,target in [('ITEM',items),('BLOCK',blocks),('ENTITY_TYPE',entities)]:
  target.update(re.findall(r'Registry\s*\.\s*register\s*\(\s*Registry\s*\.\s*'+registry+r'\s*,\s*new\s+Identifier\s*\(\s*"icbm_port"\s*,\s*"([a-z0-9_]+)"',source_text))
lang=files['assets/icbm_port/lang/en_us.json'];catalog_errors=[]
for item in items:
 if f'assets/icbm_port/models/item/{item}.json' not in files:catalog_errors.append('No model '+item)
 if f'{"block" if item in blocks else "item"}.icbm_port.{item}' not in lang:catalog_errors.append('No name '+item)
 if 'icbm_port:'+item not in obtainable:catalog_errors.append('No survival path '+item)
for block in blocks:
 if f'assets/icbm_port/blockstates/{block}.json' not in files:catalog_errors.append('No blockstate '+block)
for entity in entities:
 if 'entity.icbm_port.'+entity not in lang:catalog_errors.append('No entity name '+entity)
for recipe in recipes:
 for item in ingredients(recipe):
  if item.startswith('icbm_port:') and item.split(':')[1] not in items:catalog_errors.append('Recipe references unregistered item '+item)
for path in files:
 if path.startswith('assets/icbm_port/models/item/'):
  item=Path(path).stem
  if item not in items:catalog_errors.append('Item model without registration '+item)
if catalog_errors:print('\n'.join(sorted(set(catalog_errors))));sys.exit(1)
print(f'PASS: static Java catalog matches {len(items)} item models/names/survival paths, {len(blocks)} blockstates, and {len(entities)} entity names.')
translation_keys={key for source in java.rglob('*.java') for key in re.findall(r'Text\s*\.\s*translatable\s*\(\s*"([^"]+)"',source.read_text()) if key.startswith(('message.icbm_port.','tooltip.icbm_port.','container.icbm_port.'))}
missing_keys=translation_keys-lang.keys()
if missing_keys:print('Missing translation keys: '+', '.join(sorted(missing_keys)));sys.exit(1)
print(f'PASS: all {len(translation_keys)} literal message/tooltip/container translation keys are defined.')
serializers=set(re.findall(r'Registry\s*\.\s*register\s*\(\s*Registry\s*\.\s*RECIPE_SERIALIZER\s*,\s*(?:IcbmPort\s*\.\s*)?id\s*\(\s*"([a-z0-9_]+)"',delivery))
for serializer in serializers:
 path='data/icbm_port/recipes/'+serializer+'.json'
 if files.get(path,{}).get('type')!='icbm_port:'+serializer:
  print('Missing or mismatched special recipe: '+serializer);sys.exit(1)
print(f'PASS: all {len(serializers)} registered special recipe serializers have activating recipe JSON.')
