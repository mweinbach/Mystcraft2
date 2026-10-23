#!/usr/bin/env python3
"""Generate JurassiCraft plant resources from the checked-in upstream textures."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data'
UP = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
TREES = ['ginkgo', 'calamites', 'psaronius', 'phoenix', 'araucaria']
SINGLE = ['small_royal_fern', 'small_chain_fern', 'small_cycad', 'bennettitalean_cycadeoidea',
          'cry_pansy', 'dictyophyllum', 'ladinia_simplex', 'orontium_mackii', 'raphaelia',
          'cinnamon_fern', 'bristle_fern', 'woolly_stalked_begonia', 'largestipule_leather_root']
TALL = ['scaly_tree_fern', 'cycad_zamites', 'dicksonia', 'dicroidium_zuberi', 'west_indian_lilac',
        'serenna_veriformans', 'umaltolepis', 'liriodendrites', 'encephalartos', 'tempskya',
        'rhacophyton', 'graminidites_bambusoides', 'heliconia']
AQUATIC = ['enallhelia', 'aulopora', 'cladochonus', 'lithostrotion', 'stylophyllopsis', 'hippurites_radiosus']
LANG = {}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n')


def texture(path):
    src = UP / 'textures' / path
    dst = ASSETS / 'textures' / path
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)
    return 'jurassicraft:' + str(path.with_suffix('')).replace('\\', '/')


def model(name, parent, textures, render=None):
    m = {'parent': parent, 'textures': textures}
    if render:
        m['render_type'] = 'minecraft:' + render
    write(ASSETS / 'models/block' / (name + '.json'), m)


def item(name, parent=None, tex=None):
    obj = {'parent': parent or 'jurassicraft:block/' + name}
    if tex:
        obj['textures'] = {'layer0': tex}
    write(ASSETS / 'models/item' / (name + '.json'), obj)


def state(name, variants):
    write(ASSETS / 'blockstates' / (name + '.json'), {'variants': variants})


def v(model_name, **kwargs):
    return {'model': 'jurassicraft:block/' + model_name, **kwargs}


def loot(name, drop=None, conditions=None, functions=None):
    entry = {'type': 'minecraft:item', 'name': 'jurassicraft:' + (drop or name)}
    if functions:
        entry['functions'] = functions
    pool = {'rolls': 1, 'entries': [entry]}
    if conditions:
        pool['conditions'] = conditions
    write(DATA / 'jurassicraft/loot_tables/blocks' / (name + '.json'), {'type': 'minecraft:block', 'pools': [pool]})


def recipe(name, pattern, keys, count):
    write(DATA / 'jurassicraft/recipes' / (name + '.json'), {
        'type': 'minecraft:crafting_shaped', 'pattern': pattern,
        'key': {k: {'item': val} for k, val in keys.items()},
        'result': {'item': 'jurassicraft:' + name, 'count': count}})


def label(name, value=None):
    LANG['block.jurassicraft.' + name] = value or name.replace('_', ' ').title()


for t in TREES:
    side = texture(Path(f'blocks/log_{t}.png'))
    top = texture(Path(f'blocks/log_{t}_top.png'))
    petrified_side = texture(Path(f'blocks/log_{t}_petrified.png'))
    petrified_top = texture(Path(f'blocks/log_{t}_petrified_top.png'))
    planks = texture(Path(f'blocks/planks_{t}.png'))
    leaves = texture(Path(f'blocks/leaves_{t}.png'))
    sapling = texture(Path(f'blocks/sapling_{t}.png'))
    bottom = texture(Path(f'blocks/door_{t}_lower.png'))
    door_top = texture(Path(f'blocks/door_{t}_upper.png'))
    door_item = texture(Path(f'items/door_{t}.png'))
    for suffix, logside, logtop in [('log', side, top), ('log_petrified', petrified_side, petrified_top)]:
        name = t + '_' + suffix
        model(name, 'minecraft:block/cube_column', {'side': logside, 'end': logtop})
        model(name + '_horizontal', 'minecraft:block/cube_column_horizontal', {'side': logside, 'end': logtop})
        state(name, {'axis=y': v(name), 'axis=x': v(name + '_horizontal', x=90, y=90),
                     'axis=z': v(name + '_horizontal', x=90)})
        item(name); loot(name); label(name)
    model(t + '_planks', 'minecraft:block/cube_all', {'all': planks})
    model(t + '_leaves', 'minecraft:block/leaves', {'all': leaves}, 'cutout_mipped')
    model(t + '_sapling', 'minecraft:block/cross', {'cross': sapling}, 'cutout')
    for suffix in ('planks', 'leaves', 'sapling'):
        name = t + '_' + suffix
        state(name, {'': v(name)})
        item(name, 'minecraft:item/generated' if suffix == 'sapling' else None,
             sapling if suffix == 'sapling' else None)
        if suffix != 'leaves': loot(name)
        label(name)
    shears_or_silk = {'condition': 'minecraft:alternative', 'terms': [
        {'condition': 'minecraft:match_tool', 'predicate': {'items': ['minecraft:shears']}},
        {'condition': 'minecraft:match_tool', 'predicate': {'enchantments': [
            {'enchantment': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}]}
    drop = t + '_sapling' if t != 'phoenix' else 'phoenix_fruit'
    chances = [0.1, 0.125, 0.1666667, 0.25, 0.5] if t != 'phoenix' else [0.2, 0.25, 0.3333333, 0.5, 0.5]
    write(DATA / 'jurassicraft/loot_tables/blocks' / (t + '_leaves.json'), {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': 'jurassicraft:' + t + '_leaves', 'conditions': [shears_or_silk]},
            {'type': 'minecraft:item', 'name': 'jurassicraft:' + drop, 'conditions': [
                {'condition': 'minecraft:survives_explosion'}, {'condition': 'minecraft:table_bonus',
                 'enchantment': 'minecraft:fortune', 'chances': chances}]}]}]}]})
    write(DATA / 'jurassicraft/recipes' / (t + '_planks.json'), {'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'item': 'jurassicraft:' + t + '_log'}],
        'result': {'item': 'jurassicraft:' + t + '_planks', 'count': 4}})
    write(DATA / 'jurassicraft/recipes' / (t + '_charcoal.json'), {'type': 'minecraft:smelting',
        'ingredient': {'item': 'jurassicraft:' + t + '_log'}, 'result': 'minecraft:charcoal',
        'experience': 0.15, 'cookingtime': 200})
    # Complete vanilla-compatible wood family: stairs, slab, fence, gate, door.
    for suffix, parent in [('stairs', 'stairs'), ('stairs_inner', 'inner_stairs'), ('stairs_outer', 'outer_stairs'),
                           ('slab', 'slab'), ('slab_top', 'slab_top')]:
        model(t + '_' + suffix, 'minecraft:block/' + parent, {'bottom': planks, 'side': planks, 'top': planks})
    variants = {}
    for facing, angle in [('east', 0), ('south', 90), ('west', 180), ('north', 270)]:
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                kind = 'stairs' + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
                offset = (-90 if shape.endswith('_left') else 0) if half == 'bottom' else (90 if shape.endswith('_right') else 0)
                rotation = (angle + offset) % 360
                args = {'uvlock': True}
                if half == 'top': args['x'] = 180
                if rotation: args['y'] = rotation
                variants[f'facing={facing},half={half},shape={shape}'] = v(t + '_' + kind, **args)
    state(t + '_stairs', variants)
    state(t + '_slab', {'type=bottom': v(t + '_slab'), 'type=top': v(t + '_slab_top'),
                         'type=double': v(t + '_planks')})
    for suffix, parent in [('fence_post', 'fence_post'), ('fence_side', 'fence_side'),
                           ('fence_inventory', 'fence_inventory'), ('fence_gate', 'template_fence_gate'),
                           ('fence_gate_open', 'template_fence_gate_open'), ('fence_gate_wall', 'template_fence_gate_wall'),
                           ('fence_gate_wall_open', 'template_fence_gate_wall_open')]:
        model(t + '_' + suffix, 'minecraft:block/' + parent, {'texture': planks})
    parts = [{'apply': v(t + '_fence_post')}]
    for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        args = {'uvlock': True}
        if angle: args['y'] = angle
        parts.append({'when': {facing: 'true'}, 'apply': v(t + '_fence_side', **args)})
    write(ASSETS / 'blockstates' / (t + '_fence.json'), {'multipart': parts})
    variants = {}
    for facing, angle in [('south', 0), ('west', 90), ('north', 180), ('east', 270)]:
        for wall in ('true', 'false'):
            for opened in ('true', 'false'):
                suffix = 'fence_gate' + ('_wall' if wall == 'true' else '') + ('_open' if opened == 'true' else '')
                args = {'uvlock': True}
                if angle: args['y'] = angle
                variants[f'facing={facing},in_wall={wall},open={opened}'] = v(t + '_' + suffix, **args)
    state(t + '_fence_gate', variants)
    for half in ('bottom', 'top'):
        for hinge in ('left', 'right'):
            for opened in (False, True):
                suffix = f'door_{half}_{hinge}' + ('_open' if opened else '')
                model(t + '_' + suffix, 'minecraft:block/' + suffix, {'bottom': bottom, 'top': door_top}, 'cutout')
    variants = {}
    for facing, angle in [('east', 0), ('south', 90), ('west', 180), ('north', 270)]:
        for half in ('lower', 'upper'):
            for hinge in ('left', 'right'):
                for opened in (False, True):
                    suffix = 'door_' + ('bottom' if half == 'lower' else 'top') + '_' + hinge + ('_open' if opened else '')
                    rotation = (angle + (90 if hinge == 'left' else 270) * opened) % 360
                    args = {'y': rotation} if rotation else {}
                    variants[f'facing={facing},half={half},hinge={hinge},open={str(opened).lower()}'] = v(t + '_' + suffix, **args)
    state(t + '_door', variants)
    for suffix in ('stairs', 'slab', 'fence', 'fence_gate', 'door'):
        name = t + '_' + suffix
        if suffix == 'fence': item(name, 'jurassicraft:block/' + name + '_inventory')
        elif suffix == 'door': item(name, 'minecraft:item/generated', door_item)
        else: item(name)
        cond = []
        funcs = []
        if suffix == 'door': cond.append({'condition': 'minecraft:block_state_property',
                                          'block': 'jurassicraft:' + name, 'properties': {'half': 'lower'}})
        if suffix == 'slab': funcs.append({'function': 'minecraft:set_count', 'count': 2,
            'conditions': [{'condition': 'minecraft:block_state_property', 'block': 'jurassicraft:' + name,
                            'properties': {'type': 'double'}}]})
        loot(name, conditions=cond, functions=funcs); label(name)
    a, stick = 'jurassicraft:' + t + '_planks', 'minecraft:stick'
    recipe(t + '_stairs', ['A  ', 'AA ', 'AAA'], {'A': a}, 4)
    recipe(t + '_slab', ['AAA'], {'A': a}, 6)
    recipe(t + '_fence', ['ABA', 'ABA'], {'A': a, 'B': stick}, 3)
    recipe(t + '_fence_gate', ['BAB', 'BAB'], {'A': a, 'B': stick}, 3)
    recipe(t + '_door', ['AA', 'AA', 'AA'], {'A': a}, 3)

for name in SINGLE + AQUATIC:
    tex = texture(Path('blocks/plants/' + name + '.png'))
    model(name, 'minecraft:block/cross', {'cross': tex}, 'cutout')
    state(name, {'': v(name)}); item(name, 'minecraft:item/generated', tex); loot(name); label(name)

for name in TALL:
    lower = texture(Path('blocks/plants/' + name + '_bottom.png'))
    upper = texture(Path('blocks/plants/' + name + '_top.png'))
    model(name + '_bottom', 'minecraft:block/cross', {'cross': lower}, 'cutout')
    model(name + '_top', 'minecraft:block/cross', {'cross': upper}, 'cutout')
    state(name, {'half=lower': v(name + '_bottom'), 'half=upper': v(name + '_top')})
    upstream_item = json.loads((UP / 'models/item' / (name + '.json')).read_text())['textures']['layer0']
    path = Path(upstream_item.split(':')[1] + '.png')
    item(name, 'minecraft:item/generated', texture(path))
    loot(name, conditions=[{'condition': 'minecraft:block_state_property', 'block': 'jurassicraft:' + name,
                            'properties': {'half': 'lower'}}]); label(name)

tex = texture(Path('blocks/gracilaria_seaweed.png'))
model('gracilaria_seaweed', 'minecraft:block/cross', {'cross': tex}, 'cutout')
state('gracilaria_seaweed', {'': v('gracilaria_seaweed')})
item('gracilaria', 'minecraft:item/generated', tex)
loot('gracilaria_seaweed', 'gracilaria'); label('gracilaria_seaweed', 'Gracilaria')
LANG['item.jurassicraft.gracilaria'] = 'Gracilaria'
for name in ('phoenix_fruit', 'phoenix_seeds'):
    tex = texture(Path('items/' + name + '.png'))
    item(name, 'minecraft:item/generated', tex)
    LANG['item.jurassicraft.' + name] = name.replace('_', ' ').title()

for kind in ('blocks', 'items'):
    tags = {
        'logs': [f'#{"jurassicraft"}:{t}_logs' for t in TREES],
        'logs_that_burn': [f'#{"jurassicraft"}:{t}_logs' for t in TREES],
        'planks': ['jurassicraft:' + t + '_planks' for t in TREES],
        'leaves': ['jurassicraft:' + t + '_leaves' for t in TREES],
        'saplings': ['jurassicraft:' + t + '_sapling' for t in TREES],
        'wooden_stairs': ['jurassicraft:' + t + '_stairs' for t in TREES],
        'wooden_slabs': ['jurassicraft:' + t + '_slab' for t in TREES],
        'wooden_fences': ['jurassicraft:' + t + '_fence' for t in TREES],
        'wooden_doors': ['jurassicraft:' + t + '_door' for t in TREES],
        'fence_gates': ['jurassicraft:' + t + '_fence_gate' for t in TREES],
    }
    if kind == 'blocks':
        tags['mineable/axe'] = ['jurassicraft:' + t + '_' + s for t in TREES
                                for s in ('log', 'planks', 'stairs', 'slab', 'fence', 'fence_gate', 'door')]
    for tag, values in tags.items(): write(DATA / 'minecraft/tags' / kind / (tag + '.json'), {'replace': False, 'values': values})
    # Forge and Fabric list their wooden gates explicitly rather than using minecraft:fence_gates.
    write(DATA / 'forge/tags' / kind / 'fence_gates/wooden.json',
          {'replace': False, 'values': tags['fence_gates']})
    write(DATA / 'c/tags' / kind / 'wooden_fence_gates.json',
          {'replace': False, 'values': tags['fence_gates']})
    for t in TREES: write(DATA / 'jurassicraft/tags' / kind / (t + '_logs.json'), {'replace': False, 'values': ['jurassicraft:' + t + '_log']})

write(DATA / 'jurassicraft/worldgen/configured_feature/gracilaria.json', {'type': 'jurassicraft:gracilaria', 'config': {}})
write(DATA / 'jurassicraft/worldgen/placed_feature/gracilaria.json', {'feature': 'jurassicraft:gracilaria',
    'placement': [{'type': 'minecraft:rarity_filter', 'chance': 8}, {'type': 'minecraft:in_square'},
                  {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'}, {'type': 'minecraft:biome'}]})
write(DATA / 'jurassicraft/forge/biome_modifier/gracilaria.json', {'type': 'forge:add_features',
    'biomes': '#minecraft:is_ocean', 'features': 'jurassicraft:gracilaria', 'step': 'vegetal_decoration'})

# Crops retain upstream 1.12 growth stages and separate harvested items.
CROPS = {
    'ajuginucula_smithii': ('ajuginucula_smithii', 7, 'ajuginucula_smithii_seeds', 'ajuginucula_smithii_leaves', (1, 4), (2, 5)),
    'wild_onion_plant': ('wild_onion', 6, 'wild_onion', 'wild_onion', (0, 0), (1, 4)),
    'wild_potato_plant': ('wild_potato', 5, 'wild_potato_seeds', 'wild_potato', (1, 3), (1, 2)),
    'rhamnus_salicifolius': ('rhamnus', 7, 'rhamnus_salicifolius_seeds', 'rhamnus_salicifolius_berries', (1, 3), (1, 2)),
}
for crop, (directory, maximum, seed, harvest, seed_count, crop_count) in CROPS.items():
    variants = {}
    for age in range(8):
        stage = min(age, maximum)
        upstream_model = json.loads((UP / 'models/block' / directory / f'stage_{stage}.json').read_text())
        upstream_path = Path(upstream_model['textures']['crop'].split(':')[1] + '.png')
        tex = texture(upstream_path)
        model(crop + f'_stage_{age}', 'minecraft:block/crop', {'crop': tex}, 'cutout')
        variants[f'age={age}'] = v(crop + f'_stage_{age}')
    state(crop, variants)
    mature = {'condition': 'minecraft:block_state_property', 'block': 'jurassicraft:' + crop, 'properties': {'age': str(maximum)}}
    def drop_entry(item_name, counts):
        return {'type': 'minecraft:item', 'name': 'jurassicraft:' + item_name,
                'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform',
                                'min': counts[0], 'max': counts[1]}}]}
    pools = [{'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {**drop_entry(harvest, crop_count), 'conditions': [mature]},
        {'type': 'minecraft:item', 'name': 'jurassicraft:' + seed}]}]}]
    if seed_count[1] > 0:
        pools.append({'rolls': 1, 'conditions': [mature], 'entries': [drop_entry(seed, seed_count)]})
    write(DATA / 'jurassicraft/loot_tables/blocks' / (crop + '.json'), {'type': 'minecraft:block', 'pools': pools})
    label(crop)

for name in ('ajuginucula_smithii_seeds', 'ajuginucula_smithii_leaves', 'wild_onion',
             'wild_potato_seeds', 'wild_potato', 'wild_potato_cooked',
             'rhamnus_salicifolius_seeds', 'rhamnus_salicifolius_berries'):
    original = json.loads((UP / 'models/item' / (name + '.json')).read_text())
    tex = texture(Path(original['textures']['layer0'].split(':')[1] + '.png'))
    item(name, 'minecraft:item/generated', tex)
    LANG['item.jurassicraft.' + name] = name.replace('_', ' ').title()
write(DATA / 'minecraft/tags/blocks/crops.json', {'replace': False, 'values': ['jurassicraft:' + crop for crop in CROPS]})
write(DATA / 'jurassicraft/recipes/wild_potato_cooked.json', {'type': 'minecraft:smelting',
    'ingredient': {'item': 'jurassicraft:wild_potato'}, 'result': 'jurassicraft:wild_potato_cooked',
    'experience': 0.5, 'cookingtime': 200})


# Moss and peat are renewable environmental materials used by the incubator.
tex = texture(Path('blocks/moss.png'))
moss_model = json.loads((UP / 'models/block/moss.json').read_text())
moss_model['render_type'] = 'minecraft:translucent'
write(ASSETS / 'models/block/moss.json', moss_model)
state('moss', {'': v('moss')}); item('moss'); loot('moss'); label('moss')
tex = texture(Path('blocks/peat.png'))
model('peat', 'minecraft:block/cube_all', {'all': tex})
state('peat', {'': v('peat')}); item('peat'); loot('peat'); label('peat')

write(DATA / 'jurassicraft/tags/worldgen/biome/moss_biomes.json', {'replace': False,
    'values': ['#minecraft:is_forest', '#minecraft:is_taiga', '#minecraft:is_jungle', '#forge:is_swamp']})
write(DATA / 'jurassicraft/tags/worldgen/biome/lilac_biomes.json', {'replace': False,
    'values': ['#minecraft:is_jungle', '#forge:is_swamp']})
for name, biome in [('moss', '#jurassicraft:moss_biomes'),
                    ('west_indian_lilac', '#jurassicraft:lilac_biomes'),
                    ('heliconia', '#minecraft:is_jungle')]:
    write(DATA / 'jurassicraft/worldgen/configured_feature' / (name + '.json'),
          {'type': 'jurassicraft:land_plant', 'config': {'state': {'Name': 'jurassicraft:' + name}}})
    write(DATA / 'jurassicraft/worldgen/placed_feature' / (name + '.json'), {'feature': 'jurassicraft:' + name,
          'placement': [{'type': 'minecraft:rarity_filter', 'chance': 8}, {'type': 'minecraft:in_square'},
                        {'type': 'minecraft:heightmap', 'heightmap': 'WORLD_SURFACE_WG'}, {'type': 'minecraft:biome'}]})
    write(DATA / 'jurassicraft/forge/biome_modifier' / (name + '.json'), {'type': 'forge:add_features',
          'biomes': biome, 'features': 'jurassicraft:' + name, 'step': 'vegetal_decoration'})
write(DATA / 'jurassicraft/worldgen/configured_feature/peat.json', {'type': 'jurassicraft:peat', 'config': {}})
write(DATA / 'jurassicraft/worldgen/placed_feature/peat.json', {'feature': 'jurassicraft:peat',
    'placement': [{'type': 'minecraft:rarity_filter', 'chance': 2}, {'type': 'minecraft:in_square'},
                  {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR_WG'}, {'type': 'minecraft:biome'}]})
write(DATA / 'jurassicraft/forge/biome_modifier/peat.json', {'type': 'forge:add_features',
    'biomes': '#forge:is_swamp', 'features': 'jurassicraft:peat', 'step': 'underground_ores'})
LANG['gamerule.plantSpreading'] = 'Plant spreading'
LANG['gamerule.plantSpreading.description'] = 'Whether JurassiCraft plants and corals spread naturally.'


# Lilac progresses from leaves to flowers to fruit under bone meal.
lilac_states = {}
for age, stage in [(0, ''), (1, '_flowers'), (2, '_fruit')]:
    for half, part in [('lower', 'bottom'), ('upper', 'top')]:
        name = 'west_indian_lilac' + stage + '_' + part
        tex = texture(Path('blocks/plants/' + name + '.png'))
        model(name, 'minecraft:block/cross', {'cross': tex}, 'cutout')
        lilac_states[f'age={age},half={half}'] = v(name)
state('west_indian_lilac', lilac_states)
for name in ('ajuginucula_smithii_oil', 'oiled_potato_strips', 'fun_fries',
             'chilean_sea_bass', 'west_indian_lilac_berries'):
    original = json.loads((UP / 'models/item' / (name + '.json')).read_text())
    tex = texture(Path(original['textures']['layer0'].split(':')[1] + '.png'))
    item(name, 'minecraft:item/generated', tex)
    LANG['item.jurassicraft.' + name] = name.replace('_', ' ').title()

def shapeless(name, inputs, output, count=1):
    write(DATA / 'jurassicraft/recipes' / (name + '.json'), {'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'item': val} for val in inputs], 'result': {'item': output, 'count': count}})

shapeless('ajuginucula_smithii_oil', ['jurassicraft:ajuginucula_smithii_leaves'] * 4 + ['minecraft:glass_bottle'],
          'jurassicraft:ajuginucula_smithii_oil')
shapeless('oiled_potato_strips_wild', ['jurassicraft:wild_potato'] * 4 + ['jurassicraft:ajuginucula_smithii_oil'],
          'jurassicraft:oiled_potato_strips')
shapeless('oiled_potato_strips_potato', ['minecraft:potato'] * 2 + ['jurassicraft:ajuginucula_smithii_oil'],
          'jurassicraft:oiled_potato_strips', 4)
shapeless('phoenix_seeds', ['jurassicraft:phoenix_fruit'], 'jurassicraft:phoenix_seeds')
shapeless('chilean_sea_bass', ['minecraft:cooked_cod', 'jurassicraft:wild_onion', 'minecraft:carrot',
          'jurassicraft:ajuginucula_smithii_leaves'], 'jurassicraft:chilean_sea_bass')
shapeless('rhamnus_berries_yellow_dye', ['jurassicraft:rhamnus_salicifolius_berries'] * 4, 'minecraft:yellow_dye', 2)
shapeless('rhamnus_seeds_black_dye', ['jurassicraft:rhamnus_salicifolius_seeds'] * 4, 'minecraft:black_dye')
write(DATA / 'jurassicraft/recipes/fun_fries.json', {'type': 'minecraft:smelting',
    'ingredient': {'item': 'jurassicraft:oiled_potato_strips'}, 'result': 'jurassicraft:fun_fries',
    'experience': 0.5, 'cookingtime': 200})


# Paleo bales preserve the original nine-plant crafting recipes and cushion falls.
BALES = {'cycadeoidea': 'bennettitalean', 'cycad': 'cycad', 'fern': 'fern', 'leaves': 'leaves', 'other': 'other'}
for variant, texture_name in BALES.items():
    name = 'paleo_bale_' + variant
    side = texture(Path('blocks/paleo_bale/' + texture_name + '_side.png'))
    top = texture(Path('blocks/paleo_bale/' + texture_name + '_top.png'))
    model(name, 'minecraft:block/cube_column', {'side': side, 'end': top})
    model(name + '_horizontal', 'minecraft:block/cube_column_horizontal', {'side': side, 'end': top})
    state(name, {'axis=y': v(name), 'axis=x': v(name + '_horizontal', x=90, y=90),
                 'axis=z': v(name + '_horizontal', x=90)})
    item(name); loot(name); label(name)
# The old crop blocks had separate hidden block items. Their harvested leaves,
# berries and onions are the equivalent available crafting ingredients now.
crop_items = {'ajuginucula_smithii': 'ajuginucula_smithii_leaves',
              'rhamnus_salicifolius': 'rhamnus_salicifolius_berries',
              'wild_onion_plant': 'wild_onion'}
for original in sorted((UP / 'recipes').glob('paleo_bale*.json')):
    recipe_data = json.loads(original.read_text())
    ingredient = recipe_data['key']['A']['item'].split(':', 1)[1]
    ingredient = crop_items.get(ingredient, ingredient)
    result = recipe_data['result']['item'].split(':', 1)[1]
    output_name = result + '_from_' + ingredient
    write(DATA / 'jurassicraft/recipes' / (output_name + '.json'), {'type': 'minecraft:crafting_shaped',
        'pattern': ['AAA', 'AAA', 'AAA'], 'key': {'A': {'item': 'jurassicraft:' + ingredient}},
        'result': {'item': 'jurassicraft:' + result, 'count': recipe_data['result'].get('count', 1)}})
feeder_plants = ['jurassicraft:' + name for name in SINGLE + TALL + AQUATIC]
feeder_plants += ['jurassicraft:' + tree + '_leaves' for tree in TREES]
feeder_plants += ['jurassicraft:' + tree + '_sapling' for tree in TREES]
feeder_plants += ['jurassicraft:paleo_bale_' + variant for variant in BALES]
feeder_plants += ['jurassicraft:ajuginucula_smithii_leaves', 'jurassicraft:wild_onion',
                  'jurassicraft:wild_potato', 'jurassicraft:rhamnus_salicifolius_berries', 'jurassicraft:gracilaria']
write(DATA / 'jurassicraft/tags/items/ancient_feeder_plants.json', {'replace': False, 'values': feeder_plants})

write(ROOT / 'tools/lang/plants_en_us.json', LANG)
