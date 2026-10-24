"""Programmer art for Gridwright's phase 2d power blocks and items.

Imported by generate_textures.py; every image is drawn from flat colours and simple shapes with
fixed seeds, so the output is reproducible and contains nothing from any other mod.
"""
import random

from PIL import Image, ImageDraw

MILLS = {
    # kind: (casing colour, accent colour)
    'fire': ((112, 72, 60), (250, 140, 40)),
    'lava': ((60, 56, 64), (230, 90, 20)),
    'manual': ((150, 110, 60), (200, 200, 205)),
    'dragon_egg': ((36, 28, 48), (190, 90, 230)),
}

RAINBOW = [(230, 60, 60), (240, 150, 40), (240, 220, 60), (80, 200, 80), (60, 140, 230), (120, 80, 220),
           (210, 90, 200)]


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


def framed(base, seed):
    image = noisy(base, 8, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, 15, 15], outline=shade(base, 0.6))
    draw.line([1, 1, 14, 1], fill=shade(base, 1.3))
    draw.line([1, 1, 1, 14], fill=shade(base, 1.3))
    return image


def mill_side(casing, accent, seed):
    """A casing with a four-bladed rotor in the accent colour."""
    image = framed(casing, seed)
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 3, 12, 12], outline=shade(casing, 0.5))
    draw.line([7, 4, 8, 11], fill=accent + (255,), width=2)
    draw.line([4, 8, 11, 7], fill=accent + (255,), width=2)
    draw.rectangle([7, 7, 8, 8], fill=shade(accent, 0.6))
    return image


def mill_top(casing, accent, seed):
    image = framed(casing, seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 4, 11, 11], fill=shade(casing, 0.7), outline=accent + (255,))
    return image


def manual_mill_top(casing, accent, seed):
    """A crank handle seen from above."""
    image = framed(casing, seed)
    draw = ImageDraw.Draw(image)
    draw.ellipse([5, 5, 10, 10], fill=shade(accent, 0.8))
    draw.line([8, 8, 13, 3], fill=shade(casing, 0.5), width=2)
    draw.rectangle([12, 2, 13, 3], fill=(200, 40, 40, 255))
    return image


def rainbow_side(seed):
    image = framed((70, 70, 78), seed)
    draw = ImageDraw.Draw(image)
    for index, colour in enumerate(RAINBOW):
        x = 2 + index * 2
        draw.line([x, 3, x, 12], fill=colour + (255,))
    return image


def rainbow_top(seed):
    image = framed((70, 70, 78), seed)
    draw = ImageDraw.Draw(image)
    for index, colour in enumerate(RAINBOW):
        inset = 1 + index
        if inset >= 8:
            break
        draw.rectangle([inset, inset, 15 - inset, 15 - inset], outline=colour + (255,))
    return image


def converter_side(arrow, seed):
    """Grey casing with a copper band and an arrow showing which way power flows."""
    image = framed((120, 124, 130), seed)
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 6, 14, 9], fill=(190, 110, 70, 255))
    if arrow == 'up':
        draw.polygon([(8, 2), (4, 6), (12, 6)], fill=(90, 200, 230, 255))
        draw.rectangle([7, 6, 8, 12], fill=(90, 200, 230, 255))
    else:
        draw.polygon([(8, 13), (4, 9), (12, 9)], fill=(240, 200, 60, 255))
        draw.rectangle([7, 3, 8, 9], fill=(240, 200, 60, 255))
    return image


def converter_top(seed):
    image = framed((120, 124, 130), seed)
    draw = ImageDraw.Draw(image)
    draw.ellipse([4, 4, 11, 11], fill=(190, 110, 70, 255), outline=(90, 200, 230, 255))
    return image


def overclock_upgrade():
    image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 3, 13, 12], fill=(40, 110, 60, 255), outline=(20, 60, 30, 255))
    draw.rectangle([4, 5, 11, 10], fill=(30, 30, 34, 255))
    draw.polygon([(9, 5), (6, 8), (8, 8), (6, 11), (10, 7), (8, 7)], fill=(250, 210, 60, 255))
    for x in (3, 6, 9, 12):
        draw.point([x, 13], fill=(220, 180, 60, 255))
    return image


def textures(blocks, items):
    """Every phase 2d texture, keyed by output path."""
    out = {}
    seed = 3000
    for kind, (casing, accent) in MILLS.items():
        seed += 2
        out[blocks / f'{kind}_mill_side.png'] = mill_side(casing, accent, seed)
        top = manual_mill_top if kind == 'manual' else mill_top
        out[blocks / f'{kind}_mill_top.png'] = top(casing, accent, seed + 1)
    out[blocks / 'rainbow_generator_side.png'] = rainbow_side(seed + 2)
    out[blocks / 'rainbow_generator_top.png'] = rainbow_top(seed + 3)
    out[blocks / 'power_converter_to_energy.png'] = converter_side('down', seed + 4)
    out[blocks / 'power_converter_to_grid.png'] = converter_side('up', seed + 5)
    out[blocks / 'power_converter_top.png'] = converter_top(seed + 6)
    out[items / 'overclock_upgrade.png'] = overclock_upgrade()
    return out
