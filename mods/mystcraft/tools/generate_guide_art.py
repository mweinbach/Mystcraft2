#!/usr/bin/env python3
"""Draw original pixel art for the port-specific Agewriter's Guide.

Requires Pillow; run from any working directory. No external art is read.
"""
from pathlib import Path
from random import Random

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/mystcraft'
TEAL_DARK = '#142D30'
TEAL = '#254B4B'
TEAL_LIGHT = '#386260'
BRASS_DARK = '#806443'
BRASS = '#B49961'
BRASS_LIGHT = '#D7BE82'
PAPER = '#F1E5C8'


def book_background():
    image = Image.new('RGBA', (300, 220))
    draw = ImageDraw.Draw(image)
    # Stepped silhouettes remain crisp at Minecraft's integer GUI scales.
    draw.polygon([(5, 2), (294, 2), (298, 6), (298, 215), (295, 219),
                  (5, 219), (1, 215), (1, 6)], fill='#101A1EAA')
    draw.polygon([(4, 0), (294, 0), (297, 3), (297, 214), (293, 217),
                  (4, 217), (0, 213), (0, 4)], fill=TEAL_DARK)
    draw.rectangle((3, 3, 294, 213), fill=TEAL)
    draw.line([(4, 211), (4, 4), (293, 4)], fill=TEAL_LIGHT)
    draw.line([(5, 6), (292, 6), (292, 210), (6, 210), (6, 7)], fill=BRASS_DARK)
    # Quiet leather grain at the edges and under the controls.
    random = Random(0xA6E)
    for _ in range(480):
        x, y = random.randrange(4, 294), random.randrange(4, 214)
        if x < 9 or x > 289 or y > 199:
            draw.point((x, y), fill=random.choice([TEAL, TEAL_LIGHT, '#214242']))
    # Layered page edges under the manuscript.
    draw.rectangle((10, 12, 290, 198), fill='#AC9469')
    draw.line((12, 196, 288, 196), fill='#D8C59C')
    draw.line((13, 198, 287, 198), fill='#796544')
    draw.rectangle((9, 8, 290, 193), fill='#DAC69E')
    draw.rectangle((10, 9, 289, 192), fill=PAPER)
    draw.rectangle((11, 45, 118, 191), fill='#E9DCB9')
    draw.rectangle((130, 45, 288, 191), fill='#F4E9CD')
    # Sparse deterministic fibre marks stay near the paper colour for legibility.
    for _ in range(780):
        x, y = random.randrange(11, 289), random.randrange(10, 191)
        if y < 43 or x >= 130:
            shade = random.choice(['#EEE1C2', '#F1E5C8', '#F2E7CC'])
        elif x < 118:
            shade = random.choice(['#E6D8B4', '#EADDBC', '#E9DCB9'])
        else:
            continue
        draw.point((x, y), fill=shade)
    # Paper is brighter toward the outside, shaded into the inner binding.
    for x, color in [(114, '#E4D6B3'), (115, '#E0D0AB'), (116, '#D8C59E'),
                     (117, '#C6AF87'), (118, '#AA906A'), (129, '#BDA47C'),
                     (130, '#D3BE96'), (131, '#E7D7B3'), (132, '#EEDFC1')]:
        draw.line((x, 45, x, 191), fill=color)
    # Teal cloth spine, with a repeating hand-bound stitch.
    draw.rectangle((119, 45, 128, 193), fill='#644D36')
    draw.rectangle((121, 45, 126, 193), fill=TEAL_DARK)
    draw.line((122, 45, 122, 193), fill=TEAL_LIGHT)
    for y in range(54, 189, 16):
        draw.line((120, y, 127, y + 3), fill='#B09A70')
        draw.point((120, y), fill='#D8C49A')
        draw.point((127, y + 3), fill='#3A342A')
    # Ruled header, small diamonds, and a compass/portal colophon.
    draw.line((16, 42, 283, 42), fill='#C4AB79')
    draw.line((16, 44, 283, 44), fill='#FAF1D9')
    for x in (16, 283):
        draw.polygon([(x, 40), (x + 2, 42), (x, 44), (x - 2, 42)], fill=BRASS_DARK)
    cx, cy = 278, 24
    draw.polygon([(cx, cy - 12), (cx + 9, cy - 3), (cx + 9, cy + 4),
                  (cx, cy + 12), (cx - 9, cy + 4), (cx - 9, cy - 3)], outline='#B9A171')
    draw.line((cx - 12, cy, cx + 12, cy), fill='#C9B388')
    draw.line((cx, cy - 14, cx, cy + 14), fill='#C9B388')
    draw.line([(cx - 5, cy + 6), (cx - 5, cy - 4), (cx - 2, cy - 7),
               (cx + 2, cy - 7), (cx + 5, cy - 4), (cx + 5, cy + 6)], fill=TEAL)
    draw.line((cx - 5, cy + 6, cx + 5, cy + 6), fill=TEAL)
    draw.polygon([(cx, cy - 3), (cx + 2, cy), (cx, cy + 3), (cx - 2, cy)], fill=BRASS_DARK)
    # Brass cover fittings, kept entirely outside the reading areas.
    for x, y, sx, sy in [(3, 3, 1, 1), (294, 3, -1, 1),
                          (3, 213, 1, -1), (294, 213, -1, -1)]:
        draw.line((x, y, x + sx * 10, y), fill=BRASS_LIGHT, width=2)
        draw.line((x, y, x, y + sy * 10), fill=BRASS_LIGHT, width=2)
        draw.point((x + sx * 3, y + sy * 3), fill=BRASS)
    # A ribbon emerging from the binding breaks the otherwise straight edge.
    draw.polygon([(121, 194), (127, 194), (127, 215), (124, 212), (121, 215)], fill='#8B4B3E')
    draw.line((122, 195, 122, 211), fill='#B77955')
    return image


def item_icon():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    # A three-quarter closed volume, with visible page block and cloth spine.
    draw.polygon([(3, 1), (11, 1), (14, 4), (14, 13), (12, 15),
                  (4, 15), (1, 12), (1, 3)], fill='#15282B')
    draw.polygon([(4, 4), (12, 4), (13, 5), (13, 12), (11, 14),
                  (4, 14), (3, 12)], fill='#C1A67A')
    draw.line((5, 13, 11, 13), fill='#F3E5C6')
    draw.line((12, 6, 12, 12), fill='#EBDBB8')
    draw.polygon([(3, 2), (10, 2), (12, 4), (12, 11), (10, 12),
                  (3, 12), (2, 11), (2, 3)], fill=TEAL)
    draw.line((3, 3, 3, 11), fill=TEAL_LIGHT)
    draw.line((4, 3, 10, 3), fill='#4D7970')
    draw.line((4, 11, 10, 11), fill=TEAL_DARK)
    draw.line((4, 3, 4, 11), fill='#1C393D')
    # Gilt corner mounts and the same small portal emblem as the reader.
    for x, y in [(5, 3), (10, 4), (5, 10), (10, 10)]:
        draw.point((x, y), fill=BRASS_LIGHT)
    draw.line([(6, 9), (6, 6), (7, 5), (8, 5), (9, 6), (9, 9)], fill=BRASS)
    draw.line((6, 9, 9, 9), fill=BRASS_LIGHT)
    draw.point((7, 7), fill='#D9D4A3')
    draw.line((8, 13, 8, 15), fill='#A65C45')
    draw.point((7, 13), fill='#D29164')
    return image


def main():
    images = {
        'textures/gui/guide_book.png': book_background(),
        'textures/item/guide_book.png': item_icon(),
    }
    for path, image in images.items():
        destination = ASSETS / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, optimize=True)
        print(f'{destination.relative_to(ASSETS)}: {image.width}x{image.height}')


if __name__ == '__main__':
    main()
