"""Programmer art for Gridwright's phase 3 blocks and items.

Imported by generate_textures.py; every image is drawn from flat colours and simple shapes with fixed
seeds, so the output is reproducible and contains nothing from any other mod. The phase 3 generator
fronts come from generate_textures.GENERATOR_COLOURS and the energy node's front from
generate_transfer_textures.NODE_ACCENTS.
"""
from PIL import ImageDraw

from utility_textures import blank, noisy, shade, ward


def crafter_side(seed):
    """Oak-coloured casing with an iron band and a small gear."""
    image = noisy((150, 112, 66), 10, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(96, 70, 40, 255))
    draw.rectangle([0, 6, 15, 9], fill=(150, 150, 156, 255))
    draw.line([(0, 6), (15, 6)], fill=(190, 190, 196, 255))
    draw.ellipse([5, 4, 10, 11], fill=(110, 110, 118, 255), outline=(70, 70, 76, 255))
    draw.point((7, 7), fill=(60, 220, 230, 255))
    return image


def crafter_top(seed):
    """A 3x3 grid of wells, like a crafting table seen from above."""
    image = noisy((150, 112, 66), 10, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(96, 70, 40, 255))
    for row in range(3):
        for column in range(3):
            x, y = 2 + column * 4, 2 + row * 4
            draw.rectangle([x, y, x + 3, y + 3], fill=(84, 62, 36, 255), outline=(60, 44, 26, 255))
    draw.point((7, 7), fill=(60, 220, 230, 255))
    return image


def cursed_lasso():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 1, 13, 9], outline=(110, 40, 150, 255), width=2)
    draw.line([(5, 8), (3, 12), (5, 15)], fill=(80, 30, 110, 255), width=1)
    draw.point((7, 2), fill=(220, 120, 255, 255))
    draw.point((11, 6), fill=(200, 40, 40, 255))
    return image


def textures(blocks, items):
    """Every phase 3 texture outside the generator and node families, keyed by output path."""
    return {
        blocks / 'mechanical_crafter_side.png': crafter_side(3001),
        blocks / 'mechanical_crafter_top.png': crafter_top(3002),
        blocks / 'chunk_loading_ward.png': ward((60, 150, 90), (120, 255, 170), 3003),
        items / 'cursed_lasso.png': cursed_lasso(),
    }
