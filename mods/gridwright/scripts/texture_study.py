#!/usr/bin/env python3
"""Export the original 32px texture/model proposal into docs, never shipped assets.

Requires Pillow >= 10.1. Run: python3 mods/gridwright/scripts/texture_study.py
"""
import base64
import html
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, ImageFont
from texture_study_assets import samples, proposed_models

MOD = Path(__file__).resolve().parents[1]
ASSETS = MOD / 'src/main/resources/assets/gridwright'
OUT = MOD / 'docs/texture-study'
PACK = OUT / 'preview-pack'
TEXTURES = PACK / 'assets/gridwright/textures'
GENS = ['survivalist', 'furnace', 'culinary', 'lava', 'potion', 'ender', 'redstone']


def font(size):
    # Bundle no font. Pillow's own embedded font keeps the export portable.
    return ImageFont.load_default(size=size)


def label(d, xy, text, size=16, fill='#343a3c'):
    d.text(xy, text, font=font(size), fill=fill)


def tile(canvas, im, xy, size=96):
    x, y = xy
    d = ImageDraw.Draw(canvas)
    for yy in range(0, size, 8):
        for xx in range(0, size, 8):
            c = '#deded3' if (xx//8+yy//8) % 2 else '#e9e8df'
            d.rectangle((x+xx, y+yy, x+xx+7, y+yy+7), fill=c)
    canvas.paste(im.resize((size, size), Image.Resampling.NEAREST), xy, im.resize((size, size), Image.Resampling.NEAREST))


def comparison(art):
    selected = [
        ('Resonator', 'block/resonator_front.png'),
        ('Furnace, active', 'block/furnace_generator_front_on.png'),
        ('Culinary, active', 'block/culinary_generator_front_on.png'),
        ('Potion, active', 'block/potion_generator_front_on.png'),
        ('Water mill', 'block/water_mill_side.png'),
        ('Solar panel, top', 'block/solar_panel_top.png'),
        ('Item transfer node', 'block/transfer_node_front.png'),
        ('Fluid retrieval node', 'block/fluid_retrieval_node_front.png'),
        ('Converter, to GP', 'block/power_converter_to_grid.png'),
        ('Quantum quarry', 'block/quantum_quarry_side.png'),
        ('Iron drum', 'block/iron_drum_side.png'),
        ('Grid crystal', 'item/grid_crystal.png'),
    ]
    im = Image.new('RGB', (1200, 1050), '#f2f0e7'); d = ImageDraw.Draw(im)
    label(d, (36, 22), 'Gridwright / texture study', 32)
    label(d, (36, 65), 'Current 16px left / original 32px proposal right. Equal display size.', 18)
    for i, (title, path) in enumerate(selected):
        x, y = 36+(i % 4)*294, 112+(i//4)*290
        label(d, (x, y), title, 18)
        tile(im, Image.open(ASSETS/'textures'/path).convert('RGBA'), (x, y+34))
        tile(im, art[path], (x+126, y+34))
        label(d, (x, y+143), 'Current', 14)
        label(d, (x+126, y+143), 'Proposed', 14)
        # A truthful native-size swatch is part of each comparison.
        tile(im, art[path], (x+126, y+180), 32)
        label(d, (x+164, y+180), '1x', 14)
    label(d, (36, 1011), 'Review candidates. Optional pack includes new static models. Runtime collision and occlusion still need changes.', 16)
    im.save(OUT/'comparison.png')


def family_sheet(art):
    im = Image.new('RGB', (1200, 850), '#f2f0e7'); d = ImageDraw.Draw(im)
    label(d, (36, 22), '32 pixels, with a different symbol for every fuel', 27)
    label(d, (36, 64), 'Idle above, active below. Extra resolution adds plate seams, rivets and material detail.', 18)
    for i, kind in enumerate(GENS):
        x = 36+i*164
        label(d, (x, 108), kind.capitalize(), 16)
        for active in (False, True):
            key = f'block/{kind}_generator_front'+('_on' if active else '')+'.png'
            tile(im, art[key], (x, 140+int(active)*140), 96)
            label(d, (x, 256+int(active)*140), 'Active' if active else 'Idle', 14)
    label(d, (36, 444), 'Compressed cobblestone / count the tier pips', 23)
    for i, prefix in enumerate(['', 'double_', 'triple_', 'quadruple_', 'quintuple_', 'sextuple_', 'septuple_', 'octuple_']):
        x = 36+i*144
        tile(im, art[f'block/{prefix}compressed_cobblestone.png'], (x, 490), 96)
        label(d, (x, 598), f'Tier {i+1}', 16)
    label(d, (36, 655), 'Inventory / transparent sprites at 2x', 23)
    for i, name in enumerate(['grid_crystal', 'machine_frame', 'angel_ring', 'speed_upgrade', 'stack_upgrade', 'world_interaction_upgrade', 'overclock_upgrade']):
        x = 36+i*164
        tile(im, art[f'item/{name}.png'], (x, 706), 64)
        label(d, (x, 785), name.replace('_', '\n'), 13)
    im.save(OUT/'families.png')


def model_data(name, proposed=False):
    model = proposed_models().get(name) if proposed else None
    model = model or json.loads((ASSETS/'models/block'/f'{name}.json').read_text())
    textures = model['textures']
    if 'elements' in model:
        return model['elements'], textures
    parent = model['parent'].split('/')[-1]
    if parent == 'cube_bottom_top':
        refs = dict(north='side', east='side', up='top')
    elif parent == 'cube_column':
        refs = dict(north='side', east='side', up='end')
    elif parent == 'cube_all':
        refs = dict(north='all', east='all', up='all')
    elif parent == 'orientable_with_bottom':
        refs = dict(north='front', east='side', up='top')
    else:
        raise ValueError(f'Unreviewed model parent: {parent}')
    return [dict(**{'from': [0, 0, 0], 'to': [16, 16, 16]},
                 faces={k: {'texture': '#'+v} for k, v in refs.items()})], textures


def resolve_texture(ref, textures, art):
    while ref.startswith('#'):
        ref = textures[ref[1:]]
    key = ref.removeprefix('gridwright:')+'.png'
    if key in art:
        return art[key]
    return Image.open(ASSETS/'textures'/key).convert('RGBA')


def render_model(name, art, proposed=False):
    """Orthographic north/east/up preview, sampling the model's actual cuboid UVs.

    No invented mesh, animation, AO, biome tint or emissive light. NOT a game screenshot.
    Default UVs follow BlockElement. The selected models use only unrotated cuboids.
    """
    elements, textures = model_data(name, proposed)
    im = Image.new('RGBA', (224, 224))
    pixels = im.load()
    depths = [[float('-inf')]*224 for _ in range(224)]
    def project(x, y, z):
        return (112+(16-x-z)*6, 160+(x-z)*3-y*6)
    for el in elements:
        if 'rotation' in el:
            raise ValueError('Rotated elements need an in-game review')
        x0, y0, z0 = el['from']; x1, y1, z1 = el['to']
        for face in ('north', 'east', 'up'):
            if face not in el['faces']:
                continue
            data = el['faces'][face]
            if data.get('rotation', 0):
                raise ValueError('Rotated UVs are not supported by this study')
            if face == 'north':
                uv = [16-x1, 16-y1, 16-x0, 16-y0]
                point = lambda u, v: (x1-u*(x1-x0), y1-v*(y1-y0), z0)
            elif face == 'east':
                uv = [16-z1, 16-y1, 16-z0, 16-y0]
                point = lambda u, v: (x1, y1-v*(y1-y0), z1-u*(z1-z0))
            else:
                uv = [x0, z0, x1, z1]
                point = lambda u, v: (x0+u*(x1-x0), y1, z0+v*(z1-z0))
            u0, v0, u1, v1 = data.get('uv', uv)
            tex = resolve_texture(data['texture'], textures, art)
            origin = project(*point(0, 0))
            pu, pv = project(*point(1, 0)), project(*point(0, 1))
            ax, ay = pu[0]-origin[0], pu[1]-origin[1]
            bx, by = pv[0]-origin[0], pv[1]-origin[1]
            determinant = ax*by-ay*bx
            if abs(determinant) < .0001:
                continue
            corners = [project(*point(u, v)) for u, v in [(0, 0), (1, 0), (1, 1), (0, 1)]]
            xlo, xhi = max(0, int(min(p[0] for p in corners))), min(224, int(max(p[0] for p in corners))+1)
            ylo, yhi = max(0, int(min(p[1] for p in corners))), min(224, int(max(p[1] for p in corners))+1)
            shade = {'up': 1, 'north': .85, 'east': .7}[face]
            for y in range(ylo, yhi):
                for x in range(xlo, xhi):
                    dx, dy = x+.5-origin[0], y+.5-origin[1]
                    u, v = (dx*by-dy*bx)/determinant, (ax*dy-ay*dx)/determinant
                    if not (0 <= u <= 1 and 0 <= v <= 1):
                        continue
                    xx, yy, zz = point(u, v)
                    depth = xx+yy-zz
                    if depth < depths[y][x]:
                        continue
                    tx = min(tex.width-1, max(0, int((u0+u*(u1-u0))*tex.width/16)))
                    ty = min(tex.height-1, max(0, int((v0+v*(v1-v0))*tex.height/16)))
                    r, g, b, a = tex.getpixel((tx, ty))
                    if a:
                        pixels[x, y] = (int(r*shade), int(g*shade), int(b*shade), a)
                        depths[y][x] = depth
    return im


def block_sheet(art):
    names = ['resonator', 'furnace_generator_on', 'culinary_generator_on', 'potion_generator_on',
             'solar_panel', 'water_mill', 'wind_mill', 'manual_mill', 'transfer_node', 'power_converter_to_grid',
             'quantum_quarry', 'iron_drum', 'deep_dark_portal', 'octuple_compressed_cobblestone']
    im = Image.new('RGB', (1400, 1390), '#f2f0e7'); d = ImageDraw.Draw(im)
    label(d, (36, 22), 'Smaller machines, visible mechanisms', 32)
    label(d, (36, 65), 'Current 16px models left / proposed 32px art and 3D models right. Offline renders, not game screenshots.', 18)
    for i, name in enumerate(names):
        x, y = 24+(i%3)*462, 112+(i//3)*246
        label(d, (x+12, y), name.replace('_', ' ').replace(' on', ' / active'), 17)
        for j, source in enumerate([{}, art]):
            block = render_model(name, source, proposed=j == 1)
            im.paste(block, (x+j*224, y+16), block)
    label(d, (36, 1350), 'Actual JSON geometry and UVs. Fixed shading. Static models. Collision, occlusion and in-game lighting need review.', 16)
    im.save(OUT/'blocks.png')


def review_page(art):
    cards = []
    for path in sorted(art):
        baseline = ASSETS/'textures'/path
        current = base64.b64encode(baseline.read_bytes() if baseline.is_file() else (TEXTURES/path).read_bytes()).decode()
        current_label = 'Current' if baseline.is_file() else 'New material'
        proposed = base64.b64encode((TEXTURES/path).read_bytes()).decode()
        cards.append(f'<article data-name="{html.escape(path)}"><h3>{html.escape(path.removesuffix(".png"))}</h3>'
                     f'<div class="pair"><figure><img alt="{current_label}" src="data:image/png;base64,{current}"><figcaption>{current_label}</figcaption></figure>'
                     f'<figure><img alt="Proposed" src="data:image/png;base64,{proposed}"><figcaption>Proposed</figcaption></figure></div></article>')
    page = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Gridwright texture study</title><style>
*{box-sizing:border-box}body{margin:0;background:#f2f0e7;color:#24292d;font:16px/1.5 system-ui,sans-serif}
main{max-width:1400px;padding:32px;margin:auto}h1{font-size:clamp(30px,5vw,54px);letter-spacing:-.04em;margin:12px 0}
p{max-width:780px}a{color:#28565b}nav{display:flex;gap:24px;flex-wrap:wrap;margin:24px 0}h2{margin-top:48px}
.board{width:100%;height:auto}input{font:inherit;padding:12px;width:min(100%,540px);border:1px solid #7c8987;background:#fffdf5}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px;margin-top:24px}
article{padding:16px;border:1px solid #cccbbd;background:#faf8ef}article[hidden]{display:none}h3{font-size:13px;overflow-wrap:anywhere;margin-top:0}
.pair{display:flex;gap:20px}figure{margin:0}figure img{width:96px;height:96px;image-rendering:pixelated;background:repeating-conic-gradient(#deded3 0% 25%,#e9e8df 0% 50%) 0/16px 16px}
figcaption{font-size:13px;margin-top:4px}.note{border-left:3px solid #b37b50;padding-left:16px}footer{margin:48px 0}
</style><main><p>GRIDWRIGHT / ART DIRECTION 01</p><h1>A workshop built into Minecraft.</h1>
<p>Stone for the first generator. Riveted iron for machines. Copper joints and small crystal accents.
Original 32x32 textures, inset machine bodies, open rotors and a low portal basin. The resource pack is a partial study for review.</p>
<p class="note">The right-hand block renders use the proposed models. Offline renders are not game screenshots.
The optional pack changes appearance only. Smaller models retain the current hitboxes and occlusion until a gameplay PR updates them.</p>
<nav><a href="#comparison">Before / after</a><a href="#models">Block models</a><a href="#families">Families</a><a href="#assets">All candidates</a></nav>
'''
    for name, title in [('comparison', 'Before / after'), ('blocks', 'Proposed 3D models'), ('families', 'Fuel, tier and inventory recognition')]:
        b64 = base64.b64encode((OUT/f'{name}.png').read_bytes()).decode()
        anchor = 'models' if name == 'blocks' else name
        page += f'<section id="{anchor}"><h2>{title}</h2><img class="board" alt="{title}" src="data:image/png;base64,{b64}"></section>'
    page += '<section id="assets"><h2>Every 32px candidate at 3x</h2><label for="filter">Filter by filename</label><br><input id="filter" type="search" placeholder="Try generator, mill, upgrade..."><div class="grid">'+''.join(cards)+'</div></section>'
    page += '''<footer><p>Optional preview pack: copy the <code>preview-pack</code> folder into the Minecraft 1.19.2 resourcepacks directory,
then select it above other packs. Requires Gridwright. Disable it to return to the shipped art. Static model and texture changes only. Runtime collision and placement do not change.</p>
<p>Original art, MIT. See <a href="../texture-plan.md">the texture plan</a> for scope, model limitations and rollout.</p></footer></main>
<script>document.getElementById('filter').addEventListener('input',e=>{const q=e.target.value.toLowerCase();document.querySelectorAll('article').forEach(a=>a.hidden=!a.dataset.name.includes(q));});</script></html>'''
    (OUT/'review.html').write_text(page)


def main():
    art = samples()
    for key, image in art.items():
        original = ASSETS/'textures'/key
        if not original.is_file() and not Path(key).stem.startswith('study_'):
            raise ValueError(f'No shipped replacement target: {key}')
        assert image.size == (32, 32) and image.mode == 'RGBA', key
        assert set(image.getchannel('A').tobytes()) <= {0, 255}, key
        dest = TEXTURES/key
        dest.parent.mkdir(parents=True, exist_ok=True)
        image.save(dest, optimize=True)
    models = proposed_models()
    for name, data in models.items():
        dest = PACK/'assets/gridwright/models/block'/f'{name}.json'
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(data, indent=2)+'\n')
    (PACK/'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 9,
        'description': 'Gridwright texture study: partial original 32px art and 3D models, MIT'}}, indent=2)+'\n')
    (PACK/'LICENSE').write_text((MOD/'LICENSE').read_text())
    manifest = {'size': 32, 'scope': 'partial static texture and model review pack', 'models': sorted(models),
                'textures': sorted(art), 'unchanged_shipped_texture_count':
                sum(p.relative_to(ASSETS/'textures').as_posix() not in art for p in (ASSETS/'textures').rglob('*.png'))}
    (OUT/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    comparison(art)
    family_sheet(art)
    block_sheet(art)
    review_page(art)
    # Disposable installable ZIP. Do not duplicate committed pack source in Git.
    archive = MOD/'build/texture-study/gridwright-texture-study.zip'
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted(PACK.rglob('*')):
            if path.is_file():
                info = zipfile.ZipInfo(path.relative_to(PACK).as_posix(), (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, path.read_bytes())
    print(f'Wrote {len(art)} original 32px textures, {len(models)} models, three review sheets, review.html and {archive}')


if __name__ == '__main__':
    main()
