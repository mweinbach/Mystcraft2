#!/usr/bin/env python3
"""Validate the optional study pack's Minecraft resource contracts, without launching a client."""
import json
from pathlib import Path
import sys

from PIL import Image

MOD = Path(__file__).resolve().parents[1]
ROOT = MOD / 'docs/texture-study'
PACK = ROOT / 'preview-pack'
LIVE = MOD / 'src/main/resources/assets/gridwright'
ART = PACK / 'assets/gridwright'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    manifest = json.loads((ROOT/'manifest.json').read_text())
    meta = json.loads((PACK/'pack.mcmeta').read_text())
    require(meta['pack']['pack_format'] == 9, 'Minecraft 1.19.2 requires resource pack format 9')
    paths = {p.relative_to(ART/'textures').as_posix() for p in (ART/'textures').rglob('*.png')}
    require(paths == set(manifest['textures']), 'Texture manifest does not match the pack')
    for path in paths:
        with Image.open(ART/'textures'/path) as im:
            require(im.mode == 'RGBA' and im.size == (32, 32), f'{path}: expected RGBA 32x32')
            require(set(im.getchannel('A').tobytes()) <= {0, 255}, f'{path}: unexpected partial alpha')
    models = {p.stem: json.loads(p.read_text()) for p in (ART/'models/block').glob('*.json')}
    require(set(models) == set(manifest['models']), 'Model manifest does not match the pack')
    for name, data in models.items():
        require((LIVE/'models/block'/f'{name}.json').is_file(), f'{name}: no live model to replace')
        require(data['parent'] == 'minecraft:block/block', f'{name}: unexpected template')
        textures = data['textures']
        def resolve(ref):
            seen = set()
            while ref.startswith('#'):
                require(ref not in seen, f'{name}: texture alias cycle')
                seen.add(ref)
                require(ref[1:] in textures, f'{name}: unknown texture alias {ref}')
                ref = textures[ref[1:]]
            require(ref.startswith('gridwright:'), f'{name}: unexpected namespace {ref}')
            path = ref.removeprefix('gridwright:')+'.png'
            require((ART/'textures'/path).is_file() or (LIVE/'textures'/path).is_file(),
                    f'{name}: missing texture {path}')
        for ref in textures.values():
            resolve(ref)
        require(len(data['elements']) <= 16, f'{name}: exceeds the study budget of 16 cuboids')
        for element in data['elements']:
            require(all(0 <= a < b <= 16 for a, b in zip(element['from'], element['to'])),
                    f'{name}: invalid or out-of-block cuboid')
            for face in element['faces'].values():
                resolve(face['texture'])
                require('cullface' not in face, f'{name}: inset faces cannot cull against a neighbor')
                if 'uv' in face:
                    require(len(face['uv']) == 4 and all(0 <= v <= 16 for v in face['uv']),
                            f'{name}: invalid UVs, Minecraft keeps UV units at 16 for 32px textures')
    # Changing only the lit model would regress the machine when its fuel runs out.
    for kind in ['survivalist', 'furnace', 'culinary', 'lava', 'potion', 'ender', 'redstone']:
        require(kind+'_generator' in models and kind+'_generator_on' in models,
                f'{kind}: both generator states need model replacements')
        require((ART/'textures/block'/f'{kind}_generator_front.png').read_bytes() !=
                (ART/'textures/block'/f'{kind}_generator_front_on.png').read_bytes(),
                f'{kind}: idle and active must be distinguishable')
    # A resource pack must not accidentally change registry state or game behavior.
    require(not (PACK/'data').exists(), 'Preview pack must contain no gameplay data')
    require(not (ART/'blockstates').exists(), 'Preserve existing states and rotation wiring')
    require((PACK/'LICENSE').read_bytes() == (MOD/'LICENSE').read_bytes(), 'Missing MIT license')
    print(f'Texture study OK: {len(paths)} native 32px textures, {len(models)} models, all references resolve.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        sys.exit(str(error))
