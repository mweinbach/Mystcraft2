"""Original 32x32 pixel art and cuboid models for the Gridwright design proposal.

All pixels are authored at 32px. No resized 16px images, external art or model inputs.
Textures use 32px; Minecraft model coordinates and UV coordinates remain in 0..16 units.
"""
from PIL import Image, ImageDraw


P = {
    'ink': '#24292d', 'recess': '#343a3c', 'steel_dark': '#555f61',
    'steel': '#7c8987', 'steel_light': '#aeb9ad', 'edge': '#d5d6bb',
    'copper_dark': '#725040', 'copper': '#b37b50', 'copper_light': '#dfab69',
    'stone_dark': '#565b57', 'stone': '#85877b', 'stone_light': '#adb09b',
    'wood_dark': '#594332', 'wood': '#96754b', 'wood_light': '#c0a16b',
    'gp_dark': '#28565b', 'gp': '#5bb2b0', 'gp_light': '#c0ebcf',
    'energy_dark': '#7a4b30', 'energy': '#e6a448', 'energy_light': '#ffe1a1',
    'fluid_dark': '#294962', 'fluid': '#5d9dc4', 'fluid_light': '#b1d6dd',
    'violet_dark': '#4a3f61', 'violet': '#9885b8', 'violet_light': '#e0c8e6',
    'leaf': '#8cae64', 'red': '#c56950',
    # Phase 3 fuel accents: same shading steps as the materials above.
    'leaf_dark': '#4f6e3c', 'pink_dark': '#8a4a63', 'pink': '#cf7f9c', 'pink_light': '#f0c3d2',
}
GENS = ['survivalist', 'furnace', 'culinary', 'lava', 'potion', 'ender', 'redstone',
        'tnt', 'pink', 'nether_star', 'death', 'enchant', 'slime', 'frosty', 'halitosis']


def c(key):
    return P.get(key, key)


def blank(fill='ink', transparent=False):
    return Image.new('RGBA', (32, 32), (0, 0, 0, 0) if transparent else c(fill))


def rect(d, box, fill, outline=None, width=1):
    d.rectangle(box, fill=c(fill), outline=c(outline) if outline else None, width=width)


def line(d, points, fill, width=1):
    d.line(points, fill=c(fill), width=width)


def poly(d, pts, fill):
    d.polygon(pts, fill=c(fill))


def diamond(d, bounds, fill):
    x0, y0, x1, y1 = bounds
    poly(d, [((x0+x1)//2, y0), (x1, (y0+y1)//2),
             ((x0+x1)//2, y1), (x0, (y0+y1)//2)], fill)


def rivet(d, x, y):
    rect(d, (x, y, x+2, y+2), 'recess')
    line(d, [(x, y), (x+1, y)], 'edge')
    rect(d, (x+1, y+1, x+1, y+1), 'steel_light')


def metal(face='side'):
    im = blank('ink'); d = ImageDraw.Draw(im)
    rect(d, (1, 1, 30, 30), 'steel_dark')
    rect(d, (2, 2, 29, 29), 'steel')
    line(d, [(2, 28), (2, 2), (29, 2)], 'steel_light')
    line(d, [(3, 29), (29, 29), (29, 3)], 'recess')
    # Isolated one-pixel wear, short highlights and plate breaks use the extra resolution.
    line(d, [(8, 3), (12, 3)], 'edge')
    line(d, [(23, 27), (26, 27)], 'steel_dark')
    rect(d, (4, 13, 4, 15), 'steel_light')
    for x, y in [(4, 4), (25, 4), (4, 25), (25, 25)]:
        rivet(d, x, y)
    if face == 'side':
        rect(d, (8, 9, 23, 23), 'recess', 'steel_dark')
        for y in (11, 15, 19):
            rect(d, (10, y, 21, y+1), 'ink')
            line(d, [(10, y+2), (21, y+2)], 'steel_light')
        line(d, [(8, 7), (23, 7)], 'copper')
    elif face == 'top':
        rect(d, (8, 8, 23, 23), 'steel_dark', 'recess')
        line(d, [(9, 22), (9, 9), (22, 9)], 'steel_light')
        rect(d, (14, 16, 17, 19), 'copper_dark', 'copper')
    return im


def stone():
    im = blank('stone_dark'); d = ImageDraw.Draw(im)
    for x, y, w, h in [(0, 0, 14, 8), (17, 0, 14, 8), (0, 11, 7, 8),
                        (10, 11, 21, 8), (0, 22, 17, 9), (20, 22, 11, 9)]:
        rect(d, (x, y, x+w, y+h), 'stone')
        line(d, [(x+1, y+1), (x+w-2, y+1)], 'stone_light')
        line(d, [(x+2, y+3), (x+4, y+3)], 'stone_light')
        line(d, [(x+w-3, y+h-1), (x+w-1, y+h-1)], 'stone_dark')
        rect(d, (x+w-1, y, x+w, y+1), 'stone_dark')
    return im


def wood():
    im = blank('wood'); d = ImageDraw.Draw(im)
    for y in (0, 8, 16, 24, 31):
        line(d, [(0, y), (31, y)], 'wood_dark')
        if y < 31:
            line(d, [(1, y+1), (30, y+1)], 'wood_light')
    for x, y, w in [(3, 4, 8), (19, 5, 6), (8, 13, 10), (2, 20, 7), (20, 28, 9)]:
        line(d, [(x, y), (x+w, y)], 'wood_dark')
        line(d, [(x+2, y+1), (x+w-1, y+1)], 'wood_light')
    return im


def generator(kind, active):
    im = stone() if kind == 'survivalist' else metal('front'); d = ImageDraw.Draw(im)
    rect(d, (7, 7, 24, 25), 'ink')
    line(d, [(7, 26), (24, 26)], 'steel_dark')
    rect(d, (8, 8, 23, 24), 'recess')
    low, high = ('energy_dark', 'copper') if not active else ('energy', 'energy_light')
    if kind in ('survivalist', 'furnace'):
        rect(d, (9, 16, 22, 23), low)
        if kind == 'survivalist':
            poly(d, [(10, 23), (11, 18), (14, 20), (16, 14), (18, 18), (20, 16), (22, 23)], high)
            line(d, [(11, 11), (20, 11)], 'stone_light')
            rect(d, (13, 21, 17, 23), 'energy_dark')
        else:
            for x in (10, 15, 20):
                rect(d, (x, 9, x+1, 23), 'steel_dark')
                line(d, [(x, 9), (x, 23)], 'steel_light')
            line(d, [(9, 14), (22, 14)], 'ink')
    elif kind == 'culinary':
        stalk = 'leaf' if active else 'steel'
        line(d, [(16, 10), (16, 22)], stalk, 2)
        for y in (10, 15):
            poly(d, [(14, y+4), (9, y), (12, y), (16, y+4)], stalk)
            poly(d, [(18, y+4), (23, y), (20, y), (16, y+4)], stalk)
        line(d, [(10, 24), (22, 24)], high)
        rect(d, (11, 12, 11, 12), 'edge')
    elif kind == 'lava':
        poly(d, [(16, 9), (22, 18), (22, 21), (19, 24), (12, 24), (9, 21), (10, 18)], low)
        line(d, [(16, 13), (19, 18), (19, 21)], high, 2)
        rect(d, (11, 20, 12, 21), 'energy_dark')
    elif kind == 'potion':
        rect(d, (13, 9, 18, 11), 'copper_dark', 'copper')
        rect(d, (14, 12, 17, 14), 'steel_light')
        poly(d, [(14, 14), (10, 17), (10, 23), (21, 23), (21, 17), (17, 14)], 'steel_light')
        rect(d, (12, 18, 19, 22), 'violet' if active else 'violet_dark')
        rect(d, (12, 18, 13, 19), 'violet_light' if active else 'steel')
        rect(d, (18, 20, 18, 20), 'edge')
    elif kind == 'ender':
        poly(d, [(9, 16), (14, 11), (18, 11), (23, 16), (18, 21), (14, 21)], 'gp' if active else 'gp_dark')
        rect(d, (15, 12, 17, 20), 'ink')
        rect(d, (19, 14, 20, 15), 'gp_light' if active else 'steel')
        line(d, [(11, 23), (13, 23)], 'copper')
    elif kind == 'tnt':
        # A bundled charge: three sticks, a strap and a fuse that sparks when burning.
        for x in (10, 14, 18):
            rect(d, (x, 13, x+3, 23), 'red' if active else 'copper_dark')
            line(d, [(x, 13), (x, 23)], 'energy_light' if active else 'copper')
        rect(d, (9, 17, 22, 18), 'steel_light')
        line(d, [(16, 13), (16, 11), (19, 9)], 'stone_light')
        rect(d, (19, 8, 20, 9), high if active else 'steel')
    elif kind == 'pink':
        # A five-petal flower on a short stem.
        petal, centre = ('pink', 'pink_light') if active else ('pink_dark', 'steel')
        for box in [(14, 9, 18, 13), (9, 13, 13, 17), (19, 13, 23, 17), (11, 18, 15, 22), (17, 18, 21, 22)]:
            d.ellipse(box, fill=c(petal))
        rect(d, (15, 15, 17, 17), centre)
        line(d, [(16, 22), (16, 24)], 'leaf' if active else 'steel_dark')
    elif kind == 'nether_star':
        # A four-pointed star with a bright core.
        star = 'edge' if active else 'steel'
        poly(d, [(16, 8), (18, 14), (24, 16), (18, 18), (16, 24), (14, 18), (8, 16), (14, 14)], star)
        rect(d, (15, 15, 17, 17), 'energy_light' if active else 'steel_light')
        line(d, [(16, 9), (16, 13)], 'steel_light')
    elif kind == 'death':
        # A skull: dome, two eye sockets and a row of teeth.
        bone = 'edge' if active else 'stone_light'
        d.ellipse((10, 9, 22, 20), fill=c(bone))
        rect(d, (12, 18, 20, 22), bone)
        rect(d, (12, 13, 14, 15), 'ink'); rect(d, (18, 13, 20, 15), 'ink')
        rect(d, (15, 17, 16, 18), 'recess')
        for x in (13, 15, 17, 19):
            line(d, [(x, 21), (x, 22)], 'recess')
    elif kind == 'enchant':
        # An open book with glyph lines; the glyphs glow while it burns.
        rect(d, (9, 12, 15, 22), 'stone_light'); rect(d, (17, 12, 23, 22), 'stone_light')
        line(d, [(16, 11), (16, 23)], 'copper_dark', 2)
        glyph = 'violet_light' if active else 'violet_dark'
        for y in (14, 17, 20):
            line(d, [(10, y), (14, y)], glyph); line(d, [(18, y), (22, y)], glyph)
        rect(d, (15, 9, 17, 10), 'violet' if active else 'steel')
    elif kind == 'slime':
        # A cube within a cube, with two eyes.
        outer, inner = ('leaf', 'leaf_dark') if active else ('leaf_dark', 'steel_dark')
        rect(d, (9, 10, 22, 23), outer)
        rect(d, (12, 13, 19, 20), inner)
        rect(d, (13, 14, 14, 15), 'ink'); rect(d, (17, 14, 18, 15), 'ink')
        line(d, [(10, 11), (13, 11)], 'edge')
    elif kind == 'frosty':
        # A six-armed snowflake.
        flake = 'fluid_light' if active else 'steel'
        line(d, [(16, 8), (16, 24)], flake, 2)
        line(d, [(9, 12), (23, 20)], flake, 2)
        line(d, [(9, 20), (23, 12)], flake, 2)
        for x, y in [(16, 8), (16, 24), (9, 12), (23, 20), (9, 20), (23, 12)]:
            rect(d, (x-1, y-1, x+1, y+1), 'edge' if active else 'steel_light')
    elif kind == 'halitosis':
        # A breath cloud rising out of a stoppered bottle.
        rect(d, (14, 18, 18, 19), 'copper_dark', 'copper')
        poly(d, [(14, 20), (12, 22), (12, 24), (20, 24), (20, 22), (18, 20)], 'steel_light')
        cloud = 'violet' if active else 'violet_dark'
        for box in [(9, 10, 15, 16), (13, 8, 20, 15), (18, 10, 23, 16)]:
            d.ellipse(box, fill=c(cloud))
        rect(d, (14, 10, 15, 11), 'violet_light' if active else 'steel')
    else:
        for points in [[(10, 10), (10, 16), (16, 16), (16, 22)], [(22, 10), (22, 19), (16, 19)]]:
            line(d, points, 'red' if active else 'copper_dark', 2)
        for x, y in [(10, 10), (22, 10), (16, 22)]:
            rect(d, (x-1, y-1, x+1, y+1), high if active else 'steel')
        rect(d, (15, 15, 17, 17), 'red' if active else 'copper_dark')
    rect(d, (23, 3, 26, 4), 'energy_light' if active else 'ink')
    return im


def resonator(top=False):
    im = metal('front'); d = ImageDraw.Draw(im)
    rect(d, (7, 7, 24, 24), 'ink')
    for pts in [[(9, 13), (9, 9), (13, 9)], [(18, 9), (22, 9), (22, 13)],
                [(9, 18), (9, 22), (13, 22)], [(18, 22), (22, 22), (22, 18)]]:
        line(d, pts, 'gp', 2)
    diamond(d, (13, 11, 18, 21), 'gp_dark')
    poly(d, [(15, 12), (17, 15), (15, 20)], 'gp_light')
    line(d, [(5, 15), (7, 15)], 'copper')
    line(d, [(24, 16), (26, 16)], 'copper')
    if top:
        line(d, [(1, 15), (6, 15)], 'gp_dark')
        line(d, [(25, 16), (30, 16)], 'gp_dark')
    return im


def crystal_surface():
    im = blank('gp_dark'); d = ImageDraw.Draw(im)
    poly(d, [(1, 1), (17, 0), (25, 11), (17, 31), (5, 26)], 'gp')
    poly(d, [(17, 0), (17, 31), (25, 11)], 'gp_light')
    line(d, [(6, 3), (4, 17)], 'edge')
    return im


def sail():
    im = blank('edge'); d = ImageDraw.Draw(im)
    for y in range(2, 32, 4):
        line(d, [(0, y), (31, y)], 'steel_light')
    for x in range(3, 32, 8):
        for y in range(1, 32, 4):
            rect(d, (x, y, x, y+1), 'steel_light')
    line(d, [(1, 0), (1, 31)], 'wood_light')
    line(d, [(30, 0), (30, 31)], 'wood_light')
    return im


def trim():
    im = blank('steel_dark'); d = ImageDraw.Draw(im)
    for y in (0, 8, 16, 24):
        line(d, [(0, y), (31, y)], 'steel_light')
        line(d, [(0, y+6), (31, y+6)], 'recess')
    return im


def panel(lunar=False):
    im = metal('front'); d = ImageDraw.Draw(im)
    for x in (4, 12, 20):
        for y in (4, 12, 20):
            rect(d, (x, y, x+6, y+6), 'violet_dark' if lunar else 'fluid_dark')
            line(d, [(x, y), (x+6, y)], 'violet' if lunar else 'fluid')
            for dx in (2, 5):
                line(d, [(x+dx, y+2), (x+dx, y+5)], 'gp_dark' if lunar else 'steel_dark')
    if lunar:
        d.ellipse((20, 5, 26, 12), fill=c('violet_light'))
        d.ellipse((23, 3, 28, 10), fill=c('violet_dark'))
    else:
        rect(d, (22, 5, 25, 8), 'energy_light')
        for pts in [[(20, 6), (20, 7)], [(27, 6), (27, 7)], [(23, 3), (24, 3)]]:
            line(d, pts, 'copper_light')
    return im


def mill(kind, top=False):
    timber = kind in ('water', 'wind', 'manual')
    im = wood() if timber else metal('front'); d = ImageDraw.Draw(im)
    if top:
        rect(d, (8, 8, 23, 23), 'recess', 'steel_light')
        rect(d, (13, 13, 18, 18), 'steel', 'edge')
        if kind == 'manual':
            line(d, [(16, 16), (25, 7)], 'edge', 3)
            rect(d, (23, 4, 27, 8), 'wood_dark', 'copper')
        elif kind == 'dragon_egg':
            for x, y in [(8, 8), (21, 8), (8, 21), (21, 21)]:
                rect(d, (x, y, x+2, y+2), 'violet')
        return im
    if kind == 'water':
        rect(d, (4, 4, 27, 27), 'wood_dark')
        rect(d, (6, 6, 25, 25), 'wood_light')
        rect(d, (10, 10, 21, 21), 'wood_dark')
        for x in (6, 14, 22):
            rect(d, (x, 4, x+2, 27), 'wood')
        for y in (6, 14, 22):
            rect(d, (4, y, 27, y+2), 'wood')
        rect(d, (14, 14, 17, 17), 'steel_light')
        for x, y in [(6, 6), (23, 6), (6, 23), (23, 23)]:
            rivet(d, x, y)
    elif kind in ('wind', 'manual'):
        for pts in [[(14, 14), (6, 6), (14, 6)], [(17, 14), (25, 6), (25, 14)],
                    [(17, 17), (25, 25), (17, 25)], [(14, 17), (6, 25), (6, 17)]]:
            poly(d, pts, 'edge' if kind == 'wind' else 'steel')
        rect(d, (14, 14, 17, 17), 'copper')
        rect(d, (15, 15, 16, 16), 'edge')
    elif kind in ('fire', 'lava'):
        rect(d, (7, 7, 24, 25), 'ink')
        for x in (9, 14, 19):
            rect(d, (x, 12, x+2, 23), 'energy_dark')
            line(d, [(x, 13), (x, 21)], 'energy')
        if kind == 'fire':
            for y in (10, 16, 22):
                rect(d, (7, y, 24, y+1), 'steel_dark')
                line(d, [(7, y), (24, y)], 'steel_light')
        else:
            rect(d, (7, 7, 24, 9), 'stone_dark')
    else:
        rect(d, (7, 7, 24, 24), 'violet_dark')
        diamond(d, (11, 9, 21, 22), 'violet')
        line(d, [(14, 11), (14, 15)], 'violet_light')
    return im


def node(fluid=False, retrieval=False, energy=False):
    im = metal('front'); d = ImageDraw.Draw(im)
    rect(d, (7, 7, 24, 24), 'ink')
    accent = 'fluid' if fluid else 'energy' if energy else 'copper_light'
    pts = [(13, 10), (19, 16), (13, 22), (13, 18), (9, 18), (9, 14), (13, 14)]
    if retrieval:
        pts = [(31-x, y) for x, y in pts]
    poly(d, pts, accent)
    line(d, [(9, 25), (22, 25)], 'steel_light')
    if fluid:
        poly(d, [(22, 8), (20, 11), (22, 13), (24, 11)], 'fluid_light')
    elif energy:
        # The FE/TR bolt, not the GP crystal: the node moves stored energy, never Grid Power.
        poly(d, [(23, 8), (20, 11), (22, 11), (20, 14), (24, 10), (22, 10)], 'energy_light')
    else:
        rect(d, (20, 9, 23, 12), 'copper', 'copper_light')
    return im


def crafter(top=False):
    """Timber body on an iron band; the top is a 3x3 grid of wells with a crate mark for items."""
    im = wood(); d = ImageDraw.Draw(im)
    if top:
        rect(d, (3, 3, 28, 28), 'ink')
        for row in range(3):
            for column in range(3):
                x, y = 4+column*8, 4+row*8
                rect(d, (x, y, x+7, y+7), 'recess')
                line(d, [(x, y+7), (x+7, y+7)], 'wood_dark')
        rect(d, (14, 14, 17, 17), 'copper', 'copper_light')
    else:
        rect(d, (0, 13, 31, 18), 'steel_dark')
        line(d, [(0, 13), (31, 13)], 'steel_light')
        for x in (3, 28):
            rivet(d, x, 15)
        # Item crate mark on the band, and a small GP crystal: it is a Grid Power consumer.
        rect(d, (11, 11, 18, 20), 'copper_dark', 'copper')
        line(d, [(12, 12), (17, 19)], 'copper_light')
        diamond(d, (21, 11, 26, 20), 'gp')
    return im


def pipe():
    im = blank('steel'); d = ImageDraw.Draw(im)
    for y in (0, 12, 24):
        rect(d, (0, y, 31, y+2), 'steel_dark')
        line(d, [(0, y), (31, y)], 'steel_light')
    rect(d, (10, 3, 21, 11), 'recess')
    line(d, [(11, 4), (20, 4)], 'copper')
    return im


def converter(to_grid=False, top=False):
    im = metal('front'); d = ImageDraw.Draw(im)
    rect(d, (5, 9, 26, 22), 'ink')
    diamond(d, (7, 12, 13, 19), 'gp')
    line(d, [(10, 12), (10, 15)], 'gp_light')
    poly(d, [(23, 10), (19, 16), (23, 16), (20, 22), (26, 14), (22, 14)], 'energy')
    if top:
        line(d, [(15, 10), (17, 10), (17, 21), (15, 21)], 'copper')
    elif to_grid:
        poly(d, [(18, 12), (14, 16), (18, 20)], 'gp_light')
    else:
        poly(d, [(14, 12), (18, 16), (14, 20)], 'energy_light')
    return im


def quarry(ender=False, top=False):
    im = metal('front'); d = ImageDraw.Draw(im)
    rect(d, (7, 7, 24, 24), 'ink')
    accent = 'gp' if ender else 'violet'
    if top:
        rect(d, (9, 9, 22, 22), 'recess', accent)
        for x in (12, 18):
            line(d, [(x, 10), (x, 21)], 'steel')
    elif ender:
        poly(d, [(11, 9), (23, 9), (17, 15), (17, 23), (13, 19)], accent)
        line(d, [(9, 25), (22, 25)], 'copper')
    else:
        rect(d, (11, 9, 21, 23), 'violet_dark', accent)
        line(d, [(15, 9), (15, 23)], accent)
        line(d, [(11, 15), (21, 15)], accent)
        rect(d, (17, 11, 19, 13), 'violet_light')
        rect(d, (12, 19, 13, 20), 'violet')
    return im


def drum(top=False):
    im = blank('steel_dark'); d = ImageDraw.Draw(im)
    if top:
        rect(d, (2, 2, 29, 29), 'steel', 'recess')
        rect(d, (6, 6, 25, 25), 'steel_light', 'steel_dark')
        rect(d, (18, 10, 23, 15), 'ink', 'copper')
        line(d, [(19, 11), (22, 11)], 'copper_light')
    else:
        rect(d, (2, 0, 29, 31), 'steel')
        rect(d, (6, 0, 10, 31), 'steel_light')
        line(d, [(11, 0), (11, 31)], 'edge')
        for y in (3, 25):
            rect(d, (0, y, 31, y+3), 'recess')
            line(d, [(1, y), (30, y)], 'steel_light')
            for x in (4, 24):
                rivet(d, x, y)
        # Engraved capacity plate, not a fictitious live fluid gauge.
        rect(d, (18, 11, 25, 20), 'ink', 'copper_dark')
        for y in (13, 16, 19):
            line(d, [(20, y), (23, y)], 'edge')
    return im


def portal(top=False):
    im = blank('recess'); d = ImageDraw.Draw(im)
    for box in [(0, 0, 31, 4), (0, 27, 31, 31), (0, 5, 4, 26), (27, 5, 31, 26)]:
        rect(d, box, 'stone_dark')
    rect(d, (5, 5, 26, 26), 'ink')
    if top:
        line(d, [(9, 10), (21, 10), (21, 21), (13, 21), (13, 15), (17, 15)], 'violet', 2)
        rect(d, (16, 14, 18, 16), 'violet_light')
    else:
        for x, y in [(8, 10), (19, 8), (23, 17), (10, 21), (16, 23)]:
            rect(d, (x, y, x+1, y+1), 'violet')
        rect(d, (15, 15, 16, 16), 'violet_light')
    for x, y in [(2, 10), (29, 19), (12, 2), (20, 29)]:
        line(d, [(x, y), (x, y+2)], 'violet')
    return im


def compressed(tier):
    im = stone(); d = ImageDraw.Draw(im)
    rect(d, (0, 0, 31, 4), 'recess')
    rect(d, (0, 27, 31, 31), 'recess')
    line(d, [(0, 5), (31, 5)], 'stone_light')
    for i in range(tier):
        x, y = 5+(i % 4)*6, 28 if i < 4 else 1
        rect(d, (x, y, x+2, y+2), 'edge')
        rect(d, (x+2, y+2, x+2, y+2), 'steel')
    return im


def item(kind):
    im = blank(transparent=True); d = ImageDraw.Draw(im)
    if kind == 'grid_crystal':
        diamond(d, (6, 2, 25, 29), 'ink')
        poly(d, [(15, 3), (23, 13), (15, 27), (8, 14)], 'gp_dark')
        poly(d, [(15, 3), (15, 27), (10, 14)], 'gp')
        poly(d, [(15, 3), (15, 17), (22, 13)], 'gp_light')
        line(d, [(9, 14), (15, 26)], 'steel_light')
        rect(d, (14, 6, 14, 9), 'edge')
    elif kind == 'machine_frame':
        for box in [(4, 4, 27, 7), (4, 24, 27, 27), (4, 8, 7, 23), (24, 8, 27, 23)]:
            rect(d, box, 'steel', 'steel_light')
        for x, y in [(4, 4), (24, 4), (4, 24), (24, 24)]:
            rect(d, (x, y, x+3, y+3), 'copper_dark', 'copper')
            rect(d, (x+1, y+1, x+1, y+1), 'copper_light')
    elif kind == 'angel_ring':
        for pts in [[(2, 7), (9, 12), (9, 21), (3, 17)], [(29, 7), (22, 12), (22, 21), (28, 17)]]:
            poly(d, pts, 'steel_light')
        for x in (3, 6, 24, 27):
            line(d, [(x, 11), (x, 16)], 'edge')
        d.ellipse((8, 10, 23, 25), outline=c('copper_dark'), width=4)
        d.arc((9, 11, 22, 24), 160, 350, fill=c('copper'), width=2)
        d.arc((9, 11, 22, 24), 210, 280, fill=c('energy_light'), width=1)
        diamond(d, (13, 10, 18, 15), 'gp_light')
    else:
        rect(d, (4, 6, 27, 25), 'ink', 'steel')
        rect(d, (6, 8, 25, 23), 'recess')
        for x in (7, 14, 21):
            rect(d, (x, 26, x+2, 28), 'copper')
            line(d, [(x, 26), (x, 28)], 'copper_light')
        rivet(d, 6, 8); rivet(d, 23, 21)
        if kind == 'speed_upgrade':
            for x in (9, 17):
                poly(d, [(x, 10), (x+6, 16), (x, 22), (x, 18), (x+2, 16), (x, 14)], 'energy_light')
        elif kind == 'stack_upgrade':
            for y in (10, 15, 20):
                rect(d, (10, y, 21, y+2), 'steel_light')
                line(d, [(10, y), (20, y)], 'edge')
        elif kind == 'overclock_upgrade':
            poly(d, [(19, 9), (10, 17), (16, 17), (12, 24), (23, 14), (17, 14)], 'energy')
            line(d, [(18, 10), (13, 15)], 'energy_light')
        else:
            line(d, [(10, 21), (20, 11)], 'wood_light', 3)
            line(d, [(12, 11), (23, 11), (23, 18)], 'gp_light', 2)
    return im


def samples():
    out = {}
    def add(name, im, category='block'):
        out[f'{category}/{name}.png'] = im
    for suffix, face in [('side', 'side'), ('top', 'top'), ('bottom', 'front')]:
        add(f'machine_{suffix}', metal(face))
    for suffix in ('side', 'top'):
        add(f'survivalist_{suffix}', stone())
    for kind in GENS:
        for active in (False, True):
            add(f'{kind}_generator_front'+('_on' if active else ''), generator(kind, active))
    add('resonator_front', resonator()); add('resonator_top', resonator(True))
    add('study_crystal', crystal_surface())
    add('study_timber', wood())
    add('study_sail', sail())
    add('study_trim', trim())
    add('solar_panel_top', panel()); add('lunar_panel_top', panel(True))
    side = metal('front'); d = ImageDraw.Draw(side)
    rect(d, (0, 0, 31, 19), '#00000000')
    line(d, [(0, 20), (31, 20)], 'steel_light')
    line(d, [(0, 29), (31, 29)], 'recess')
    add('panel_side', side); add('panel_bottom', metal('front'))
    for kind in ['water', 'wind', 'fire', 'lava', 'manual', 'dragon_egg']:
        add(f'{kind}_mill_side', mill(kind)); add(f'{kind}_mill_top', mill(kind, True))
    for name in ['transfer_node', 'retrieval_node', 'fluid_transfer_node', 'fluid_retrieval_node']:
        add(f'{name}_front', node(name.startswith('fluid'), 'retrieval' in name))
    add('energy_transfer_node_front', node(energy=True))
    add('mechanical_crafter_side', crafter()); add('mechanical_crafter_top', crafter(True))
    add('node_side', metal('front')); add('transfer_pipe', pipe())
    add('power_converter_to_grid', converter(True)); add('power_converter_to_energy', converter())
    add('power_converter_top', converter(top=True))
    for ender in (False, True):
        name = 'ender' if ender else 'quantum'
        add(f'{name}_quarry_side', quarry(ender)); add(f'{name}_quarry_top', quarry(ender, True))
    add('iron_drum_side', drum()); add('iron_drum_top', drum(True))
    add('deep_dark_portal_side', portal()); add('deep_dark_portal_top', portal(True))
    for i, prefix in enumerate(['', 'double_', 'triple_', 'quadruple_', 'quintuple_', 'sextuple_', 'septuple_', 'octuple_']):
        add(f'{prefix}compressed_cobblestone', compressed(i+1))
    for name in ['grid_crystal', 'machine_frame', 'angel_ring', 'speed_upgrade', 'stack_upgrade',
                 'world_interaction_upgrade', 'overclock_upgrade']:
        add(name, item(name), 'item')
    return out


def box(start, end, texture, uv=None, top=None):
    """Visible geometry uses no neighbor cullface because it can be inset or hollow."""
    faces = {face: {'texture': '#'+texture} for face in ['north', 'south', 'east', 'west', 'up', 'down']}
    if uv:
        for data in faces.values():
            data['uv'] = uv
    if top:
        faces['up']['texture'] = '#'+top
        faces['down']['texture'] = '#'+top
    return {'from': list(start), 'to': list(end), 'faces': faces}


def model(textures, elements):
    return {'parent': 'minecraft:block/block', 'ambientocclusion': True,
            'textures': {'particle': next(iter(textures.values())), **textures}, 'elements': elements}


def proposed_models():
    """Static design candidates. Runtime shapes/occlusion require a later gameplay PR."""
    out = {}
    base = {'side': 'gridwright:block/machine_side', 'top': 'gridwright:block/machine_top',
            'trim': 'gridwright:block/study_trim', 'wood': 'gridwright:block/study_timber'}
    def add(name, textures, elements):
        out[name] = model(textures, elements)
    for kind in GENS:
        for active in (False, True):
            name = kind+'_generator'+('_on' if active else '')
            tex = {**base, 'front': 'gridwright:block/'+kind+'_generator_front'+('_on' if active else '')}
            if kind == 'survivalist':
                tex.update(side='gridwright:block/survivalist_side', top='gridwright:block/survivalist_top')
            body = box((1, 1, 1), (15, 13, 15), 'side', [0, 0, 16, 16], top='top')
            body['faces']['north']['texture'] = '#front'
            els = [body, box((0, 0, 0), (16, 1, 16), 'trim'),
                   box((2, 13, 2), (14, 14, 14), 'trim')]
            if kind in ('survivalist', 'furnace'):
                els += [box((10, 14, 7), (13, 16, 12), 'side')]
            elif kind in ('potion', 'halitosis'):
                els += [box((6, 14, 6), (10, 16, 10), 'top')]
            add(name, tex, els)
    tex = {**base, 'front': 'gridwright:block/resonator_front', 'top': 'gridwright:block/resonator_top',
           'core': 'gridwright:block/study_crystal'}
    body = box((1, 1, 1), (15, 11, 15), 'side', [0, 0, 16, 16], top='top')
    body['faces']['north']['texture'] = '#front'
    add('resonator', tex, [body, box((0, 0, 0), (16, 1, 16), 'trim'),
        box((5, 11, 5), (11, 12, 11), 'trim'), box((6, 12, 6), (10, 15, 10), 'core', [0, 0, 16, 16]),
        box((7, 15, 7), (9, 16, 9), 'core', [0, 0, 16, 4])])
    for lunar in (False, True):
        name = ('lunar' if lunar else 'solar')+'_panel'
        tex = {**base, 'cells': 'gridwright:block/'+name+'_top', 'rim': 'gridwright:block/panel_bottom'}
        slab = box((0, 2, 0), (16, 4, 16), 'rim', top='cells')
        slab['faces']['up']['uv'] = [0, 0, 16, 16]
        add(name, tex, [box((2, 0, 2), (4, 2, 14), 'trim'), box((12, 0, 2), (14, 2, 14), 'trim'), slab])
    # Water wheel and wind rotor occupy one block and use cuboids with gaps between blades.
    tex = {**base, 'hub': 'gridwright:block/water_mill_top'}
    els = [box((2, 0, 6), (14, 2, 12), 'wood'), box((2, 2, 8), (4, 10, 10), 'wood'),
           box((12, 2, 8), (14, 10, 10), 'wood'), box((1, 7, 7), (15, 9, 9), 'trim')]
    for start, end in [((4, 2, 4), (12, 4, 10)), ((4, 12, 4), (12, 14, 10)),
                       ((2, 4, 4), (4, 12, 10)), ((12, 4, 4), (14, 12, 10)),
                       ((7, 4, 6), (9, 12, 8)), ((4, 7, 6), (12, 9, 8))]:
        els.append(box(start, end, 'wood'))
    els.append(box((6, 6, 3), (10, 10, 5), 'trim'))
    add('water_mill', tex, els)
    tex = {**base, 'sail': 'gridwright:block/study_sail'}
    els = [box((4, 0, 7), (12, 2, 13), 'wood'), box((7, 2, 9), (9, 15, 11), 'wood'),
           box((6, 7, 6), (10, 11, 9), 'trim')]
    for start, end in [((2, 10, 5), (7, 13, 6)), ((9, 11, 5), (12, 16, 6)),
                       ((10, 5, 5), (15, 8, 6)), ((5, 2, 5), (8, 7, 6))]:
        els.append(box(start, end, 'sail', [0, 0, 16, 16]))
    els += [box((7, 6, 5), (9, 12, 6), 'trim'), box((5, 8, 5), (11, 10, 6), 'trim')]
    add('wind_mill', tex, els)
    for kind in ('fire', 'lava', 'manual', 'dragon_egg'):
        tex = {**base, 'mill': f'gridwright:block/{kind}_mill_side', 'cap': f'gridwright:block/{kind}_mill_top'}
        if kind == 'manual':
            els = [box((2, 0, 2), (14, 8, 14), 'wood'), box((6, 8, 6), (10, 10, 10), 'trim'),
                   box((7, 10, 7), (14, 11, 9), 'trim'), box((12, 11, 7), (14, 15, 9), 'wood')]
        elif kind == 'dragon_egg':
            els = [box((1, 0, 1), (15, 2, 15), 'trim'), box((4, 2, 4), (12, 11, 12), 'mill', [0, 0, 16, 16]),
                   box((1, 11, 1), (15, 13, 15), 'trim'), box((0, 13, 0), (16, 16, 16), 'cap', [0, 0, 16, 16])]
        else:
            els = [box((1, 0, 1), (15, 2, 15), 'trim'), box((2, 2, 2), (14, 10, 14), 'mill', [0, 0, 16, 16]),
                   box((1, 10, 1), (15, 11, 15), 'trim')]
            for x in (3, 7, 11):
                els.append(box((x, 11, 2), (x+1, 12, 14), 'trim'))
        add(kind+'_mill', tex, els)
    for name in ('transfer_node', 'retrieval_node', 'fluid_transfer_node', 'fluid_retrieval_node', 'energy_transfer_node'):
        tex = {**base, 'front': f'gridwright:block/{name}_front', 'side': 'gridwright:block/node_side'}
        plate = box((1, 1, 0), (15, 15, 3), 'side')
        plate['faces']['north'].update(texture='#front', uv=[0, 0, 16, 16])
        add(name, tex, [plate, box((3, 3, 3), (13, 13, 6), 'trim'), box((5, 5, 6), (11, 11, 16), 'side')])
    # Pipe dimensions stay exactly matched to the existing multipart collision geometry.
    add('transfer_pipe_core', {'pipe': 'gridwright:block/transfer_pipe'}, [box((6, 6, 6), (10, 10, 10), 'pipe')])
    add('transfer_pipe_arm', {'pipe': 'gridwright:block/transfer_pipe'}, [box((6, 6, 0), (10, 10, 6), 'pipe')])
    for to_grid in (False, True):
        name = 'power_converter_to_'+('grid' if to_grid else 'energy')
        tex = {**base, 'mode': f'gridwright:block/{name}', 'top': 'gridwright:block/power_converter_top'}
        add(name, tex, [box((2, 1, 2), (14, 12, 14), 'mode', [0, 0, 16, 16], top='top'),
            box((1, 0, 1), (15, 1, 15), 'trim'), box((4, 12, 4), (12, 14, 12), 'top', [0, 0, 16, 16])])
    for ender in (False, True):
        name = ('ender' if ender else 'quantum')+'_quarry'
        tex = {**base, 'core': f'gridwright:block/{name}_side', 'top': f'gridwright:block/{name}_top'}
        els = [box((3, 2, 3), (13, 13, 13), 'core', [0, 0, 16, 16], top='top'),
               box((0, 0, 0), (16, 2, 16), 'trim'), box((0, 14, 0), (16, 16, 16), 'trim')]
        for x, z in [(0, 0), (14, 0), (0, 14), (14, 14)]:
            els.append(box((x, 2, z), (x+2, 14, z+2), 'trim'))
        add(name, tex, els)
    tex = {**base, 'body': 'gridwright:block/iron_drum_side', 'lid': 'gridwright:block/iron_drum_top'}
    els = []
    # Stepped octagonal silhouette without rotations or extra texture sizes.
    for start, end in [((3, 1, 1), (13, 14, 15)), ((1, 1, 3), (3, 14, 13)), ((13, 1, 3), (15, 14, 13))]:
        els.append(box(start, end, 'body', top='lid'))
    for y in (1, 12):
        for start, end in [((3, y, 0), (13, y+1, 16)), ((0, y, 3), (3, y+1, 13)), ((13, y, 3), (16, y+1, 13))]:
            els.append(box(start, end, 'trim'))
    els.append(box((9, 14, 5), (12, 15, 8), 'trim'))
    add('iron_drum', tex, els)
    tex = {**base, 'side': 'gridwright:block/deep_dark_portal_side', 'pool': 'gridwright:block/deep_dark_portal_top'}
    els = [box((0, 0, 0), (16, 2, 16), 'trim')]
    for start, end in [((1, 2, 1), (15, 4, 3)), ((1, 2, 13), (15, 4, 15)),
                       ((1, 2, 3), (3, 4, 13)), ((13, 2, 3), (15, 4, 13))]:
        els.append(box(start, end, 'side'))
    els.append(box((3, 2, 3), (13, 2.25, 13), 'pool', [0, 0, 16, 16]))
    add('deep_dark_portal', tex, els)
    return out
