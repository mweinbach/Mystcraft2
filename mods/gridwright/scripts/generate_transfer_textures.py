#!/usr/bin/env python3
"""Generate the original programmer art for Gridwright's transfer pipes, nodes, upgrades and filter.

Kept apart from generate_textures.py (whose helpers it reuses) so the transfer system's art can be
regenerated on its own. Every texture is drawn from flat colours and simple shapes with fixed seeds.

    python3 mods/gridwright/scripts/generate_transfer_textures.py

Requires Pillow.
"""
from PIL import Image, ImageDraw

from generate_textures import BLOCKS, ITEMS, bevel, casing, noisy

# Accent colours: items orange, fluids blue; retrieval nodes use a darker shade.
NODE_ACCENTS = {
    'transfer_node': (230, 150, 40),
    'retrieval_node': (170, 90, 20),
    'fluid_transfer_node': (60, 140, 230),
    'fluid_retrieval_node': (30, 70, 160),
    'energy_transfer_node': (220, 40, 40),
}


def pipe(seed):
    image = bevel(noisy((150, 150, 156), 5, seed), (200, 200, 206, 255), (90, 90, 96, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([5, 5, 10, 10], fill=(70, 72, 80, 255))
    draw.rectangle([6, 6, 9, 9], fill=(40, 170, 210, 255))
    return image


def node_front(accent, seed, retrieval):
    image = casing(seed, (96, 100, 108))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=(40, 42, 48, 255))
    dark = tuple(max(0, c - 60) for c in accent) + (255,)
    if retrieval:
        # A ring: pulls inwards.
        draw.rectangle([4, 4, 11, 11], outline=accent + (255,), width=2)
        draw.rectangle([7, 7, 8, 8], fill=dark)
    else:
        # A filled square: pushes outwards.
        draw.rectangle([5, 5, 10, 10], fill=accent + (255,), outline=dark)
    return image


def node_side(seed):
    image = casing(seed, (110, 114, 122))
    draw = ImageDraw.Draw(image)
    draw.line([(2, 8), (13, 8)], fill=(70, 72, 80, 255))
    return image


def upgrade(colour, symbol):
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=(70, 74, 82, 255), outline=(150, 154, 160, 255))
    fill = colour + (255,)
    if symbol == 'arrows':
        for x in (4, 8):
            draw.polygon([(x, 4), (x + 3, 7), (x, 10)], fill=fill)
        draw.line([(4, 11), (11, 11)], fill=fill)
    elif symbol == 'stack':
        for y in (4, 7, 10):
            draw.rectangle([4, y, 11, y + 1], fill=fill)
    else:  # pick
        draw.line([(4, 11), (10, 5)], fill=(160, 120, 70, 255), width=2)
        draw.polygon([(6, 4), (12, 4), (12, 10)], fill=fill)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        draw.point((x, y), fill=(40, 170, 210, 255))
    return image


def item_filter():
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(2, 3), (13, 3), (9, 9), (9, 13), (6, 14), (6, 9)], fill=(225, 220, 200, 255),
                 outline=(120, 110, 90, 255))
    for x in range(4, 12, 2):
        draw.point((x, 5), fill=(120, 110, 90, 255))
    draw.point((7, 11), fill=(200, 40, 40, 255))
    return image


def main():
    textures = {
        BLOCKS / 'transfer_pipe.png': pipe(201),
        BLOCKS / 'node_side.png': node_side(202),
        ITEMS / 'speed_upgrade.png': upgrade((255, 210, 60), 'arrows'),
        ITEMS / 'stack_upgrade.png': upgrade((220, 220, 230), 'stack'),
        ITEMS / 'world_interaction_upgrade.png': upgrade((100, 230, 220), 'pick'),
        ITEMS / 'item_filter.png': item_filter(),
    }
    for index, (node, accent) in enumerate(NODE_ACCENTS.items()):
        textures[BLOCKS / f'{node}_front.png'] = node_front(accent, 210 + index, 'retrieval' in node)
    for path, image in textures.items():
        image.save(path, optimize=True)
    print(f'Wrote {len(textures)} textures.')


if __name__ == '__main__':
    main()
