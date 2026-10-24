#!/usr/bin/env python3
"""Generate Gridwright's original programmer art.

Every texture is drawn here from flat colours, bevels and simple shapes with a fixed random seed,
so the PNGs are reproducible and contain nothing from any other mod. Run from anywhere:

    python3 mods/gridwright/scripts/generate_textures.py

Requires Pillow.
"""
from pathlib import Path
import random

from PIL import Image, ImageDraw

import utility_textures

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/gridwright'
BLOCKS = ROOT / 'textures/block'
ITEMS = ROOT / 'textures/item'

GENERATOR_COLOURS = {
    'survivalist': ((222, 140, 60), (255, 200, 90)),
    'furnace': ((200, 70, 40), (255, 150, 60)),
    'culinary': ((90, 170, 60), (170, 240, 110)),
    'lava': ((230, 90, 20), (255, 190, 40)),
    'potion': ((150, 70, 190), (230, 140, 255)),
    'ender': ((30, 140, 120), (90, 240, 200)),
    'redstone': ((170, 20, 20), (255, 60, 50)),
}


def noisy(base, spread, seed, size=16):
    rng = random.Random(seed)
    image = Image.new('RGBA', (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            shift = rng.randint(-spread, spread)
            pixels[x, y] = tuple(max(0, min(255, c + shift)) for c in base) + (255,)
    return image


def bevel(image, light, dark):
    draw = ImageDraw.Draw(image)
    w, h = image.size
    draw.line([(0, 0), (w - 1, 0)], fill=light)
    draw.line([(0, 0), (0, h - 1)], fill=light)
    draw.line([(0, h - 1), (w - 1, h - 1)], fill=dark)
    draw.line([(w - 1, 0), (w - 1, h - 1)], fill=dark)
    return image


def casing(seed, base=(110, 114, 120)):
    image = bevel(noisy(base, 6, seed), (160, 164, 170, 255), (60, 62, 66, 255))
    draw = ImageDraw.Draw(image)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point((x, y), fill=(70, 72, 76, 255))
    return image


def stone_casing(seed):
    image = bevel(noisy((128, 128, 128), 12, seed), (170, 170, 170, 255), (72, 72, 72, 255))
    draw = ImageDraw.Draw(image)
    draw.line([(1, 8), (14, 8)], fill=(96, 96, 96, 255))
    draw.line([(8, 1), (8, 7)], fill=(96, 96, 96, 255))
    draw.line([(5, 9), (5, 14)], fill=(96, 96, 96, 255))
    return image


def generator_front(kind, lit, seed):
    image = stone_casing(seed) if kind == 'survivalist' else casing(seed)
    dark, bright = GENERATOR_COLOURS[kind]
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 4, 12, 12], fill=(30, 30, 34, 255), outline=(50, 50, 56, 255))
    if lit:
        draw.rectangle([4, 7, 11, 11], fill=dark + (255,))
        draw.polygon([(5, 11), (7, 6), (8, 9), (10, 5), (11, 11)], fill=bright + (255,))
    else:
        draw.rectangle([4, 10, 11, 11], fill=tuple(c // 3 for c in dark) + (255,))
    draw.rectangle([5, 2, 10, 2], fill=dark + (255,))
    return image


def resonator_front(seed):
    image = casing(seed)
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], outline=(40, 160, 200, 255))
    draw.ellipse([5, 5, 10, 10], outline=(90, 220, 255, 255))
    draw.point((7, 7), fill=(200, 250, 255, 255))
    draw.point((8, 8), fill=(200, 250, 255, 255))
    return image


def resonator_top(seed):
    image = casing(seed)
    draw = ImageDraw.Draw(image)
    for x in (4, 7, 10):
        draw.line([(x, 3), (x, 12)], fill=(40, 160, 200, 255))
    return image


def panel_top(seed, cell, line, dot=None):
    image = noisy(cell, 4, seed)
    draw = ImageDraw.Draw(image)
    for i in (0, 5, 10, 15):
        draw.line([(i, 0), (i, 15)], fill=line + (255,))
        draw.line([(0, i), (15, i)], fill=line + (255,))
    if dot:
        draw.ellipse([6, 6, 9, 9], fill=dot + (255,))
    return image


def panel_side(seed):
    image = noisy((150, 150, 156), 5, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 9], fill=(0, 0, 0, 0))
    draw.line([(0, 10), (15, 10)], fill=(40, 60, 120, 255))
    draw.line([(0, 15), (15, 15)], fill=(80, 80, 86, 255))
    return image


def planks(seed, base):
    image = noisy(base, 8, seed)
    draw = ImageDraw.Draw(image)
    for y in (3, 7, 11, 15):
        draw.line([(0, y), (15, y)], fill=tuple(c - 40 for c in base) + (255,))
    return image


def mill_side(seed, base, blade):
    image = planks(seed, base)
    draw = ImageDraw.Draw(image)
    draw.line([(2, 2), (13, 13)], fill=blade + (255,), width=2)
    draw.line([(13, 2), (2, 13)], fill=blade + (255,), width=2)
    draw.ellipse([6, 6, 9, 9], fill=(70, 70, 76, 255))
    return image


def crystal():
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(8, 1), (13, 7), (8, 14), (3, 7)], fill=(40, 170, 210, 255), outline=(20, 90, 130, 255))
    draw.polygon([(8, 3), (11, 7), (8, 9)], fill=(150, 235, 255, 255))
    return image


def frame():
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 14, 14], outline=(90, 94, 100, 255), width=2)
    draw.rectangle([1, 1, 14, 14], outline=(150, 154, 160, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        draw.point((x, y), fill=(40, 160, 200, 255))
    return image


def portal_side(seed):
    image = bevel(noisy((52, 52, 60), 8, seed), (86, 86, 98, 255), (24, 24, 30, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=(12, 8, 22, 255), outline=(70, 40, 120, 255))
    rng = random.Random(seed)
    for _ in range(9):
        x, y = rng.randint(4, 11), rng.randint(4, 11)
        draw.point((x, y), fill=(150, 90, 230, 255))
    draw.point((7, 7), fill=(220, 190, 255, 255))
    draw.point((8, 8), fill=(220, 190, 255, 255))
    return image


def portal_top(seed):
    image = bevel(noisy((52, 52, 60), 8, seed), (86, 86, 98, 255), (24, 24, 30, 255))
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], fill=(18, 10, 32, 255), outline=(90, 50, 150, 255))
    draw.ellipse([5, 5, 10, 10], fill=(60, 30, 110, 255))
    draw.rectangle([7, 7, 8, 8], fill=(200, 160, 255, 255))
    return image


def icon():
    image = Image.new('RGBA', (64, 64), (34, 38, 46, 255))
    draw = ImageDraw.Draw(image)
    for i in range(8, 64, 12):
        draw.line([(i, 4), (i, 59)], fill=(60, 90, 140, 255))
        draw.line([(4, i), (59, i)], fill=(60, 90, 140, 255))
    draw.polygon([(32, 8), (50, 30), (32, 56), (14, 30)], fill=(40, 170, 210, 255), outline=(150, 235, 255, 255))
    return image


def quarry_side(seed, glow, dark):
    image = casing(seed, (70, 72, 82))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=dark + (255,), outline=(40, 40, 46, 255))
    draw.ellipse([5, 5, 10, 10], outline=glow + (255,))
    draw.point((7, 7), fill=(255, 255, 255, 255))
    draw.point((8, 8), fill=glow + (255,))
    return image


def quarry_top(seed, glow):
    image = casing(seed, (80, 82, 92))
    draw = ImageDraw.Draw(image)
    draw.line([(3, 8), (12, 8)], fill=glow + (255,))
    draw.line([(8, 3), (8, 12)], fill=glow + (255,))
    draw.rectangle([6, 6, 9, 9], outline=glow + (255,))
    return image


def actuator_side(seed):
    image = casing(seed, (96, 98, 106))
    draw = ImageDraw.Draw(image)
    draw.polygon([(5, 3), (10, 3), (8, 12), (7, 12)], fill=(150, 220, 230, 255), outline=(60, 120, 130, 255))
    draw.line([(3, 13), (12, 13)], fill=(60, 62, 66, 255))
    return image


def mechanical_front(seed, kind):
    image = stone_casing(seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 3, 12, 12], fill=(40, 40, 44, 255), outline=(30, 30, 34, 255))
    if kind == 'miner':
        draw.line([(5, 11), (10, 6)], fill=(150, 100, 50, 255), width=2)
        draw.arc([4, 3, 13, 10], 200, 340, fill=(200, 200, 210, 255), width=2)
    else:
        draw.rectangle([6, 7, 9, 11], fill=(230, 180, 140, 255))
        for x in (5, 7, 9):
            draw.line([(x, 4), (x, 7)], fill=(230, 180, 140, 255))
        draw.line([(10, 7), (11, 6)], fill=(230, 180, 140, 255))
    return image


def upgrade(colour, mark):
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 3, 13, 12], fill=(60, 64, 72, 255), outline=(30, 32, 36, 255))
    draw.line([(4, 13), (4, 14)], fill=(200, 170, 60, 255))
    draw.line([(7, 13), (7, 14)], fill=(200, 170, 60, 255))
    draw.line([(10, 13), (10, 14)], fill=(200, 170, 60, 255))
    if mark == 'fortune':
        draw.polygon([(8, 4), (11, 8), (8, 11), (5, 8)], fill=colour + (255,), outline=(255, 255, 255, 255))
    else:
        draw.arc([4, 4, 11, 11], 0, 360, fill=colour + (255,))
        draw.line([(4, 8), (11, 8)], fill=colour + (255,))
    return image


def biome_marker():
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([3, 2, 12, 13], fill=(225, 215, 180, 255), outline=(120, 100, 70, 255))
    draw.rectangle([5, 4, 10, 8], fill=(70, 150, 70, 255))
    draw.rectangle([5, 8, 10, 9], fill=(60, 110, 200, 255))
    draw.point((7, 6), fill=(200, 40, 40, 255))
    draw.line([(5, 11), (10, 11)], fill=(120, 100, 70, 255))
    return image


def main():
    BLOCKS.mkdir(parents=True, exist_ok=True)
    ITEMS.mkdir(parents=True, exist_ok=True)
    textures = {
        BLOCKS / 'machine_side.png': casing(1),
        BLOCKS / 'machine_top.png': casing(2, (124, 128, 134)),
        BLOCKS / 'machine_bottom.png': casing(3, (90, 92, 98)),
        BLOCKS / 'survivalist_side.png': stone_casing(4),
        BLOCKS / 'survivalist_top.png': stone_casing(5),
        BLOCKS / 'resonator_front.png': resonator_front(6),
        BLOCKS / 'resonator_top.png': resonator_top(7),
        BLOCKS / 'solar_panel_top.png': panel_top(8, (30, 50, 110), (120, 150, 210)),
        BLOCKS / 'lunar_panel_top.png': panel_top(9, (30, 24, 50), (110, 100, 150), dot=(220, 220, 235)),
        BLOCKS / 'panel_side.png': panel_side(10),
        BLOCKS / 'panel_bottom.png': casing(11, (100, 100, 106)),
        BLOCKS / 'water_mill_side.png': mill_side(12, (150, 110, 60), (60, 110, 200)),
        BLOCKS / 'water_mill_top.png': planks(13, (150, 110, 60)),
        BLOCKS / 'wind_mill_side.png': mill_side(14, (190, 160, 110), (235, 235, 235)),
        BLOCKS / 'wind_mill_top.png': planks(15, (190, 160, 110)),
        BLOCKS / 'deep_dark_portal_side.png': portal_side(16),
        BLOCKS / 'deep_dark_portal_top.png': portal_top(17),
        ITEMS / 'grid_crystal.png': crystal(),
        ITEMS / 'machine_frame.png': frame(),
        BLOCKS / 'quantum_quarry_side.png': quarry_side(20, (190, 90, 255), (40, 20, 60)),
        BLOCKS / 'quantum_quarry_top.png': quarry_top(21, (190, 90, 255)),
        BLOCKS / 'quarry_actuator_side.png': actuator_side(22),
        BLOCKS / 'quarry_actuator_top.png': casing(23, (96, 98, 106)),
        BLOCKS / 'ender_quarry_side.png': quarry_side(24, (60, 230, 190), (10, 50, 44)),
        BLOCKS / 'ender_quarry_top.png': quarry_top(25, (60, 230, 190)),
        BLOCKS / 'mechanical_side.png': stone_casing(26),
        BLOCKS / 'mechanical_miner_front.png': mechanical_front(27, 'miner'),
        BLOCKS / 'mechanical_user_front.png': mechanical_front(28, 'user'),
        ITEMS / 'fortune_upgrade.png': upgrade((60, 90, 220), 'fortune'),
        ITEMS / 'silk_touch_upgrade.png': upgrade((240, 240, 250), 'silk'),
        ITEMS / 'biome_marker.png': biome_marker(),
        ROOT / 'icon.png': icon(),
    }
    for index, kind in enumerate(GENERATOR_COLOURS):
        textures[BLOCKS / f'{kind}_generator_front.png'] = generator_front(kind, False, 100 + index)
        textures[BLOCKS / f'{kind}_generator_front_on.png'] = generator_front(kind, True, 100 + index)
    textures.update(utility_textures.textures(BLOCKS, ITEMS))
    for path, image in textures.items():
        image.save(path, optimize=True)
    print(f'Wrote {len(textures)} textures.')


if __name__ == '__main__':
    main()
