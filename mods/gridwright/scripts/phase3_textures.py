"""Gridwright's phase 3 production textures, drawn natively at 32x32.

Imported by generate_textures.py. Phase 3 is the first content drawn in the approved 32px direction
(DESIGN.md), so it reuses the study's drawing source, scripts/texture_study_assets.py, rather than the
16px helpers: the eight phase 3 generator fronts, the energy transfer node and the mechanical crafter
are the study's own functions, and the chunk loading ward and cursed lasso are drawn here with the
same palette and helpers. Everything is drawn from flat shapes; the output is reproducible and
contains nothing from any other mod. The older blocks keep their 16px art until the study is promoted.
"""
from PIL import ImageDraw

from texture_study_assets import (blank, c, crafter, diamond, generator, line, node, poly, rect, rivet,
                                  stone)

GENERATORS = ['tnt', 'pink', 'nether_star', 'death', 'enchant', 'slime', 'frosty', 'halitosis']


def chunk_ward():
    """A stone ward with a copper frame, a 3x3 chunk map and the GP crystal it runs on."""
    im = stone(); d = ImageDraw.Draw(im)
    rect(d, (4, 4, 27, 27), 'copper_dark', 'copper')
    rect(d, (6, 6, 25, 25), 'recess')
    for row in range(3):
        for column in range(3):
            x, y = 7 + column * 6, 7 + row * 6
            rect(d, (x, y, x + 4, y + 4), 'stone_dark', 'steel_dark')
    # The loaded chunk in the middle carries the crystal.
    diamond(d, (13, 12, 18, 19), 'gp')
    line(d, [(15, 13), (15, 15)], 'gp_light')
    for x, y in [(2, 2), (27, 2), (2, 27), (27, 27)]:
        rivet(d, x, y)
    return im


def cursed_lasso():
    """A dark rope loop with a red-bound knot: the golden lasso's shape in cursed materials."""
    im = blank(transparent=True); d = ImageDraw.Draw(im)
    d.ellipse((6, 2, 26, 18), outline=c('ink'), width=4)
    d.ellipse((7, 3, 25, 17), outline=c('violet_dark'), width=2)
    d.arc((7, 3, 25, 17), 200, 300, fill=c('violet'), width=1)
    line(d, [(11, 16), (8, 22), (11, 29)], 'ink', 3)
    line(d, [(11, 16), (8, 22), (11, 29)], 'violet_dark', 1)
    rect(d, (10, 14, 14, 18), 'ink')
    rect(d, (11, 15, 13, 17), 'red')
    poly(d, [(18, 4), (20, 4), (19, 6)], 'violet_light')
    return im


def textures(blocks, items):
    """Every phase 3 texture, keyed by output path."""
    out = {}
    for kind in GENERATORS:
        out[blocks / f'{kind}_generator_front.png'] = generator(kind, False)
        out[blocks / f'{kind}_generator_front_on.png'] = generator(kind, True)
    out[blocks / 'energy_transfer_node_front.png'] = node(energy=True)
    out[blocks / 'mechanical_crafter_side.png'] = crafter()
    out[blocks / 'mechanical_crafter_top.png'] = crafter(True)
    out[blocks / 'chunk_loading_ward.png'] = chunk_ward()
    out[items / 'cursed_lasso.png'] = cursed_lasso()
    return out
