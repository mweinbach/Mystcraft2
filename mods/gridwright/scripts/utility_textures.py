"""Programmer art for Gridwright's phase 2b utility blocks and items.

Imported by generate_textures.py; every image is drawn from flat colours and simple shapes with
fixed seeds, so the output is reproducible and contains nothing from any other mod.
"""
import random

from PIL import Image, ImageDraw

COMPRESSED = {
    # kind: (tiers, base colour, noise spread)
    'cobblestone': (8, (122, 122, 122), 22),
    'dirt': (4, (134, 96, 67), 16),
    'gravel': (2, (128, 122, 120), 26),
    'sand': (2, (219, 207, 163), 10),
    'netherrack': (6, (112, 52, 50), 18),
}
PREFIXES = ['', 'double_', 'triple_', 'quadruple_', 'quintuple_', 'sextuple_', 'septuple_', 'octuple_']

SPIKES = {
    'wooden': (150, 110, 60),
    'iron': (205, 205, 210),
    'golden': (245, 205, 60),
    'diamond': (100, 225, 215),
}

DRUMS = {
    # tier: (body colour, hoop colour)
    'stone': ((125, 125, 125), (80, 80, 80)),
    'iron': ((190, 190, 196), (110, 110, 118)),
    'reinforced': ((92, 92, 112), (220, 180, 60)),
    'gargantuan': ((64, 24, 74), (200, 90, 230)),
}

TRASH = {
    'trash_can': (150, 150, 150),
    'fluid_trash_can': (60, 110, 220),
    'energy_trash_can': (220, 60, 50),
}


def clamp(value):
    return max(0, min(255, value))


def noisy(base, spread, seed, size=16):
    rng = random.Random(seed)
    image = Image.new('RGBA', (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            shift = rng.randint(-spread, spread)
            pixels[x, y] = tuple(clamp(c + shift) for c in base) + (255,)
    return image


def shade(colour, factor):
    return tuple(clamp(int(c * factor)) for c in colour[:3]) + (255,)


def compressed(base, spread, tier, seed):
    """Darker and more tightly framed with each tier; tier pips run along the bottom edge."""
    colour = shade(base, 1.0 - 0.06 * tier)
    image = noisy(colour[:3], spread, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=shade(base, 0.45))
    draw.rectangle([1, 1, 14, 14], outline=shade(base, 0.75 - 0.03 * tier))
    for i in range(tier):
        x = 2 + (i % 4) * 3
        y = 12 if i < 4 else 2
        draw.rectangle([x, y, x + 1, y + 1], fill=shade(base, 1.35))
    return image


def spike_side(metal, seed):
    image = noisy((52, 52, 56), 6, seed)
    draw = ImageDraw.Draw(image)
    for left in range(0, 16, 4):
        draw.polygon([(left, 15), (left + 1, 3), (left + 2, 3), (left + 3, 15)], fill=shade(metal, 0.85))
        draw.line([(left + 1, 4), (left + 1, 14)], fill=shade(metal, 1.15))
    draw.line([(0, 15), (15, 15)], fill=(36, 36, 38, 255))
    return image


def spike_top(metal, seed):
    image = noisy((52, 52, 56), 6, seed)
    draw = ImageDraw.Draw(image)
    for y in range(1, 16, 4):
        for x in range(1, 16, 4):
            draw.rectangle([x, y, x + 1, y + 1], fill=shade(metal, 0.9))
            draw.point((x, y), fill=shade(metal, 1.25))
    return image


def muffler(seed):
    image = noisy((228, 228, 222), 8, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=(170, 170, 165, 255))
    # A note with a bar through it.
    draw.rectangle([6, 4, 7, 10], fill=(50, 50, 60, 255))
    draw.rectangle([7, 4, 10, 5], fill=(50, 50, 60, 255))
    draw.ellipse([3, 9, 7, 12], fill=(50, 50, 60, 255))
    draw.line([(2, 13), (13, 2)], fill=(200, 40, 40, 255), width=1)
    return image


def drum_side(body, hoop, seed):
    image = noisy(body, 8, seed)
    draw = ImageDraw.Draw(image)
    for x in range(1, 16, 3):
        draw.line([(x, 0), (x, 15)], fill=shade(body, 0.8))
    for y in (2, 13):
        draw.rectangle([0, y, 15, y + 1], fill=hoop + (255,))
    return image


def drum_top(body, hoop, seed):
    image = noisy(shade(body, 0.9)[:3], 6, seed)
    draw = ImageDraw.Draw(image)
    draw.ellipse([0, 0, 15, 15], outline=hoop + (255,))
    draw.ellipse([2, 2, 13, 13], outline=shade(body, 0.7))
    draw.rectangle([10, 4, 11, 5], fill=hoop + (255,))
    return image


def trash_side(accent, seed):
    image = noisy((70, 72, 76), 5, seed)
    draw = ImageDraw.Draw(image)
    for x in (3, 7, 11):
        draw.line([(x, 2), (x, 13)], fill=(48, 50, 54, 255))
        draw.line([(x + 1, 2), (x + 1, 13)], fill=(96, 98, 104, 255))
    draw.rectangle([0, 0, 15, 1], fill=accent + (255,))
    draw.line([(0, 15), (15, 15)], fill=(40, 40, 44, 255))
    return image


def trash_top(accent, seed):
    image = noisy((60, 62, 66), 5, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=accent + (255,))
    for y in range(3, 13, 3):
        draw.line([(3, y), (12, y)], fill=(20, 20, 22, 255))
    return image


def blank():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def angel_ring():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(1, 5), (5, 7), (5, 10), (2, 9)], fill=(240, 240, 250, 255), outline=(180, 190, 210, 255))
    draw.polygon([(14, 5), (10, 7), (10, 10), (13, 9)], fill=(240, 240, 250, 255), outline=(180, 190, 210, 255))
    draw.ellipse([4, 5, 11, 12], outline=(230, 180, 40, 255), width=2)
    draw.point((6, 6), fill=(255, 240, 150, 255))
    return image


def golden_lasso():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 1, 13, 9], outline=(230, 180, 40, 255), width=2)
    draw.line([(5, 8), (3, 12), (5, 15)], fill=(200, 150, 30, 255), width=1)
    draw.point((7, 2), fill=(255, 240, 150, 255))
    return image


def wand(tip):
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.line([(2, 13), (11, 4)], fill=(110, 80, 40, 255), width=2)
    draw.rectangle([11, 2, 13, 4], fill=tip + (255,))
    draw.point((12, 2), fill=(255, 255, 255, 255))
    return image


def watering_can():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 6, 11, 13], fill=(170, 175, 185, 255), outline=(100, 104, 112, 255))
    draw.line([(11, 8), (15, 4)], fill=(140, 145, 155, 255), width=2)
    draw.arc([2, 3, 9, 10], 180, 360, fill=(100, 104, 112, 255))
    draw.rectangle([5, 9, 10, 12], fill=(70, 120, 220, 255))
    return image


def bag():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 5), (11, 5), (14, 14), (1, 14)], fill=(110, 50, 140, 255), outline=(60, 25, 80, 255))
    draw.rectangle([5, 2, 10, 5], outline=(60, 25, 80, 255))
    draw.rectangle([7, 7, 8, 9], fill=(240, 200, 60, 255))
    return image


def pocket_workbench():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 4, 13, 14], fill=(150, 105, 60, 255), outline=(90, 60, 30, 255))
    for x in (5, 9):
        draw.line([(x, 5), (x, 13)], fill=(90, 60, 30, 255))
    for y in (7, 11):
        draw.line([(3, y), (12, y)], fill=(90, 60, 30, 255))
    draw.arc([4, 0, 11, 7], 180, 360, fill=(70, 50, 30, 255))
    return image


WARDS = {
    # name: (frame colour, emblem colour)
    'spawn_ward': ((214, 178, 70), (255, 236, 150)),
    'greater_spawn_ward': ((150, 110, 220), (255, 236, 150)),
    'ender_ward': ((30, 26, 40), (60, 200, 150)),
}


def ward(frame, emblem, seed):
    """A framed tile with a diamond-shaped emblem and a dot in the middle."""
    image = noisy(shade(frame, 0.55)[:3], 6, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=shade(frame, 1.0))
    draw.rectangle([1, 1, 14, 14], outline=shade(frame, 0.8))
    draw.polygon([(7, 3), (12, 8), (8, 12), (3, 7)], outline=shade(emblem, 1.0))
    draw.rectangle([7, 7, 8, 8], fill=shade(emblem, 1.1))
    return image


def textures(blocks, items):
    """Every phase 2b texture, keyed by output path."""
    out = {}
    seed = 2000
    for kind, (tiers, base, spread) in COMPRESSED.items():
        for tier in range(1, tiers + 1):
            seed += 1
            out[blocks / f'{PREFIXES[tier - 1]}compressed_{kind}.png'] = compressed(base, spread, tier, seed)
    for kind, metal in SPIKES.items():
        seed += 2
        out[blocks / f'{kind}_spike_side.png'] = spike_side(metal, seed)
        out[blocks / f'{kind}_spike_top.png'] = spike_top(metal, seed + 1)
    out[blocks / 'sound_muffler.png'] = muffler(seed + 3)
    for tier, (body, hoop) in DRUMS.items():
        seed += 4
        out[blocks / f'{tier}_drum_side.png'] = drum_side(body, hoop, seed)
        out[blocks / f'{tier}_drum_top.png'] = drum_top(body, hoop, seed + 1)
    for name, accent in TRASH.items():
        seed += 4
        out[blocks / f'{name}_side.png'] = trash_side(accent, seed)
        out[blocks / f'{name}_top.png'] = trash_top(accent, seed + 1)
    out[items / 'angel_ring.png'] = angel_ring()
    out[items / 'golden_lasso.png'] = golden_lasso()
    out[items / 'builders_wand.png'] = wand((240, 200, 60))
    out[items / 'destruction_wand.png'] = wand((100, 225, 215))
    out[items / 'watering_can.png'] = watering_can()
    out[items / 'bag_of_holding.png'] = bag()
    out[items / 'pocket_workbench.png'] = pocket_workbench()
    for name, (frame, emblem) in WARDS.items():
        seed += 4
        out[blocks / f'{name}.png'] = ward(frame, emblem, seed)
    return out
