#!/usr/bin/env python3
"""Draws the port's machine textures: casings, frames, glass, controllers, ports, control and fuel rods,
rotor parts, the fuel column fill and the metal storage blocks.

Every machine is drawn from the same steel ramps, one frame profile and one port layout, so a reactor, its
turbines, the reprocessor and the fluidizer read as one plant. Each machine keeps an accent colour for its
frame corners, controller screen and glass: yellow for the reactor, blue for the turbine, green for the
reprocessor and violet for the fluidizer. Reinforced parts are darker and carry hazard stripes.

The script redraws the textures that exist under the asset root and refuses names it does not know, so the
set of files never changes. Ores, items and GUI textures are left alone. Requires Pillow and NumPy:

    python3 mods/extremereactors/scripts/generate_textures.py [--root <textures dir>]
"""

import argparse
import json
import math
import pathlib
import sys

import numpy as np
from PIL import Image

DEFAULT_ROOT = (pathlib.Path(__file__).resolve().parents[1]
                / "src/main/resources/assets/bigreactors/textures")


def rgb(value):
    return np.array([int(value[i:i + 2], 16) for i in (1, 3, 5)], dtype=float)


def ramp(*values):
    return [rgb(v) for v in values]


# Darkest to lightest. The basic tier is galvanized steel, the reinforced tier gunmetal.
STEEL_BASIC = ramp("#34383d", "#4f5459", "#6a6f75", "#82878d", "#9a9fa4", "#b4b8bc", "#d2d5d8")
STEEL_REINFORCED = ramp("#16181b", "#212428", "#2c3035", "#383d43", "#464c53", "#5a6169", "#79818a")

# Dark, mid, light.
ACCENTS = {
    "reactor": ramp("#6e5110", "#d6a425", "#f6d75e"),
    "turbine": ramp("#18476a", "#3487c0", "#7cc6f0"),
    "reprocessor": ramp("#1f5a27", "#45a84c", "#8fdc86"),
    "fluidizer": ramp("#4f2a73", "#8c55c4", "#c9a0ef"),
}
HAZARD_DARK = rgb("#1a1a1a")

IN_LIT, IN_DIM = rgb("#62e35a"), rgb("#2c5a2a")
OUT_LIT, OUT_DIM = rgb("#ff9b3d"), rgb("#6e4320")
MEKANISM = ramp("#4a2468", "#a24fe0", "#d29af5")
CREATIVE = ramp("#5a1850", "#d23cb8", "#f59ce4")

GLYPH_COLOURS = {
    "fuel": ramp("#5c4a0c", "#c9a226", "#ecd25a", "#fff3a8"),
    "waste": ramp("#123f57", "#2b88b5", "#5fc3e6", "#c4f0ff"),
    "blutonium": ramp("#14175a", "#3640c4", "#6e78f0", "#c8ccff"),
    "water": ramp("#152f6b", "#2f5fcf", "#5f93f2", "#bcd6ff"),
    "steam": ramp("#6d7378", "#b9bec2", "#e3e6e8", "#ffffff"),
    "energy": ramp("#6a3a06", "#e08a12", "#ffc93a", "#fff2a6"),
    "redstone": ramp("#3b2410", "#7a5230", "#d01e12", "#ff8a6a"),
    "terminal": ramp("#0d2a12", "#2e8a3a", "#5fe070", "#c8ffd0"),
    "steel": ramp("#3a3e44", "#7f858c", "#b9bec3", "#eef0f2"),
}

SCREEN_BG = {"active": rgb("#0c1f12"), "idle": rgb("#0d1419"), "error": rgb("#220a0a")}
LED = {"active": (rgb("#2e7a34"), rgb("#7cff74")), "idle": (rgb("#6e4a10"), rgb("#ffc44a")),
       "error": (rgb("#5c1010"), rgb("#ff4a3a"))}


# region canvas


def canvas(width=16, height=16):
    return np.zeros((height, width, 4))


def put(image, x, y, colour, alpha=255.0):
    if 0 <= y < image.shape[0] and 0 <= x < image.shape[1]:
        image[y, x, :3] = colour
        image[y, x, 3] = alpha


def blend(image, x, y, colour, alpha):
    """Draws colour over the pixel with the given opacity (0..1)."""
    if 0 <= y < image.shape[0] and 0 <= x < image.shape[1]:
        image[y, x, :3] = image[y, x, :3] * (1 - alpha) + np.asarray(colour) * alpha


def rect(image, x0, y0, x1, y1, colour, alpha=255.0):
    image[y0:y1 + 1, x0:x1 + 1, :3] = colour
    image[y0:y1 + 1, x0:x1 + 1, 3] = alpha


def shade(colour, factor):
    return np.clip(np.asarray(colour) * factor, 0, 255)


def mix(a, b, t):
    return np.asarray(a) * (1 - t) + np.asarray(b) * t


def dim(colour):
    grey = np.asarray(colour).mean()
    return shade(mix(colour, (grey, grey, grey), 0.45), 0.7)


def tileable_noise(seed, passes, size=16, height=None):
    height = height or size
    noise = np.random.default_rng(seed).normal(size=(height, size))
    for _ in range(passes):
        noise = (noise + np.roll(noise, 1, 0) + np.roll(noise, -1, 0) + np.roll(noise, 1, 1)
                 + np.roll(noise, -1, 1)) / 5
    return (noise - noise.mean()) / (noise.std() or 1)


def from_levels(levels, steel):
    """Colours each pixel from the ramp; half levels blend the two neighbouring steps."""
    image = canvas(levels.shape[1], levels.shape[0])
    for y in range(levels.shape[0]):
        for x in range(levels.shape[1]):
            level = float(levels[y, x])
            low = int(math.floor(level))
            put(image, x, y, mix(steel[low], steel[min(low + 1, len(steel) - 1)], level - low))
    return image


# endregion
# region casing


def plating(steel, seed):
    """Seamless sheet steel: no border, so assembled faces read as one surface."""
    field = (3.5 + tileable_noise(seed, 1) * 0.16 + tileable_noise(seed + 1, 3) * 0.22
             + np.random.default_rng(seed + 2).normal(size=(16, 1)) * 0.14)
    return from_levels(np.clip(np.rint(field * 2) / 2, 3, 4), steel)


FRAME_WIDTH = 4
# Steel levels from the outer edge inwards. Top and left beams catch the light on their outer side, bottom and
# right beams on their inner side; the plating under top and left beams gets a one pixel shadow.
PROFILE = {"U": (0, 5, 4, 2), "L": (0, 5, 4, 2), "D": (0, 2, 3, 5), "R": (0, 2, 3, 5)}
BOLTS = (5, 10)


def edge_pixels(side, depth, along, size=16):
    """(x, y) of the pixel `depth` pixels in from `side`, `along` pixels along it."""
    return {"U": (along, depth), "D": (along, size - 1 - depth),
            "L": (depth, along), "R": (size - 1 - depth, along)}[side]


def beam(image, steel, side):
    for along in range(16):
        for depth, level in enumerate(PROFILE[side]):
            put(image, *edge_pixels(side, depth, along), steel[level])
        if side in "UL":
            x, y = edge_pixels(side, FRAME_WIDTH, along)
            image[y, x, :3] = shade(image[y, x, :3], 0.78)
    for along in BOLTS:
        put(image, *edge_pixels(side, 2, along), steel[6])
        put(image, *edge_pixels(side, 2, along + 1), steel[1])


def corner_cap(image, steel, accent, hazard, corner):
    """A 4x4 cap where two beams meet: accent paint, or hazard stripes on the reinforced tier."""
    x0 = 0 if "L" in corner else 16 - FRAME_WIDTH
    y0 = 0 if "U" in corner else 16 - FRAME_WIDTH
    for dy in range(FRAME_WIDTH):
        for dx in range(FRAME_WIDTH):
            x, y = x0 + dx, y0 + dy
            if hazard:
                colour = accent[1] if (x + y) % 4 < 2 else HAZARD_DARK
            else:
                colour = accent[1]
            put(image, x, y, colour)
    # Light from the top left.
    for d in range(FRAME_WIDTH):
        blend(image, x0 + d, y0, accent[2] if not hazard else steel[6], 0.55)
        blend(image, x0, y0 + d, accent[2] if not hazard else steel[6], 0.55)
        blend(image, x0 + d, y0 + FRAME_WIDTH - 1, accent[0], 0.6)
        blend(image, x0 + FRAME_WIDTH - 1, y0 + d, accent[0], 0.6)
    # Outline on the block's outer edges.
    for d in range(FRAME_WIDTH):
        put(image, x0 + d, 0 if "U" in corner else 15, steel[0])
        put(image, 0 if "L" in corner else 15, y0 + d, steel[0])


def casing(steel, accent, hazard, sides, seed):
    image = plating(steel, seed)
    for side in sides:
        beam(image, steel, side)
    for corner in ("UL", "UR", "DL", "DR"):
        if corner[0] in sides and corner[1] in sides:
            corner_cap(image, steel, accent, hazard, corner)
    return image


CASING_SIDES = {
    "casing_single": "UDLR", "casing_up": "U", "casing_down": "D", "casing_left": "L", "casing_right": "R",
    "casing_left_up": "LU", "casing_left_down": "LD", "casing_right_up": "RU", "casing_right_down": "RD",
    "plating": "", "assembledplating": "",
}


# endregion
# region glass

GLASS_TINT = {
    "reactor": (rgb("#9fd8e6"), 46), "turbine": (rgb("#b7d4ef"), 40),
    "reprocessor": (rgb("#b6e6b0"), 44), "fluidizer": (rgb("#d6c2f0"), 44),
}


def glass(steel, machine, hazard, connections):
    """Connected glass. `connections` is ER2's mask: 1 top, 2 bottom, 4 left, 8 right; open sides get a frame."""
    tint, alpha = GLASS_TINT[machine]
    image = canvas()
    rect(image, 0, 0, 15, 15, tint, alpha)
    if hazard:
        # Wired glass: a sparse diagonal mesh that tiles across connected panes.
        for y in range(16):
            for x in range(16):
                if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                    put(image, x, y, steel[5], 70)
    # Two short glints.
    for x, y in ((3, 6), (4, 5), (5, 4), (6, 3), (5, 6), (6, 5)):
        put(image, x, y, rgb("#ffffff"), 120 if (x, y) not in ((5, 6), (6, 5)) else 70)
    for x, y in ((11, 13), (12, 12), (13, 11)):
        put(image, x, y, rgb("#ffffff"), 80)
    for bit, side in ((1, "U"), (2, "D"), (4, "L"), (8, "R")):
        if not connections & bit:
            for along in range(16):
                put(image, *edge_pixels(side, 0, along), steel[1])
                put(image, *edge_pixels(side, 1, along), steel[5 if side in "UL" else 3])
    accent = ACCENTS[machine]
    # ER2 always draws the four corner pixels, so inside a large window they must stay faint.
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        if image[y, x, 3] < 255:
            put(image, x, y, steel[2], 110)
    return image


# endregion
# region ports


def slim_frame(image, steel):
    """The two pixel bezel every port, controller and hatch shares."""
    for side in "UDLR":
        for along in range(16):
            put(image, *edge_pixels(side, 0, along), steel[0])
            put(image, *edge_pixels(side, 1, along), steel[5 if side in "UL" else 3])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(image, x, y, steel[6])


def recess(image, steel, tint=None):
    rect(image, 2, 2, 13, 13, steel[1] if tint is None else mix(steel[1], tint, 0.35))
    for d in range(2, 14):
        put(image, d, 2, steel[0])
        put(image, 2, d, steel[0])
        put(image, d, 13, steel[2] if tint is None else mix(steel[2], tint, 0.4))
        put(image, 13, d, steel[2] if tint is None else mix(steel[2], tint, 0.4))


def port_base(steel, seed, tint=None):
    image = plating(steel, seed)
    slim_frame(image, steel)
    recess(image, steel, tint)
    return image


GLYPHS = {
    # 0 transparent, 1..4 the glyph ramp from dark to light.
    "ingot": [
        "........",
        "........",
        "...4443.",
        "..43332.",
        ".433322.",
        "4333221.",
        "2222211.",
        "........",
    ],
    "droplet": [
        "...4....",
        "...43...",
        "..4332..",
        ".433332.",
        ".433322.",
        ".333222.",
        "..2221..",
        "...11...",
    ],
    "steam": [
        "........",
        "...44...",
        ".443344.",
        "43333334",
        "43333322",
        ".322222.",
        "..1..1..",
        ".1..1...",
    ],
    "bolt": [
        "....443.",
        "...443..",
        "..443...",
        ".444443.",
        "..34432.",
        "...432..",
        "..432...",
        "..32....",
    ],
    "torch": [
        "...44...",
        "..4334..",
        "..3443..",
        "...33...",
        "...22...",
        "...12...",
        "...12...",
        "...11...",
    ],
    "terminal": [
        "11111111",
        "1......1",
        "1.3....1",
        "1..3...1",
        "1.3....1",
        "1...44.1",
        "11111111",
        "...11...",
    ],
    "funnel": [
        "44444444",
        ".333333.",
        "..3333..",
        "...33...",
        "...22...",
        "...22...",
        "..2..2..",
        ".2....2.",
    ],
}


def draw_glyph(image, name, colours, x0=4, y0=4):
    for y, row in enumerate(GLYPHS[name]):
        for x, value in enumerate(row):
            if value != ".":
                put(image, x0 + x, y0 + y, colours[int(value) - 1])


ARROW = {"in": ((-2, 0), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1)),
         "out": ((-1, 0), (0, 0), (-2, 1), (-1, 1), (0, 1), (1, 1))}


def arrows(image, direction, colour):
    """Four small arrows on the bezel, pointing into the machine for inputs and out of it for outputs."""
    for side in "UDLR":
        for along, depth in ARROW[direction]:
            put(image, *edge_pixels(side, depth, 8 + along), colour)


def corner_marks(image, colour):
    """L-shaped marks in the recess corners: pumps (active ports), Mekanism and creative parts."""
    for x, y, dx, dy in ((3, 3, 1, 1), (12, 3, -1, 1), (3, 12, 1, -1), (12, 12, -1, -1)):
        put(image, x, y, colour)
        put(image, x + dx, y, colour)
        put(image, x, y + dy, colour)


def port(steel, seed, glyph, glyph_colours, lit, direction=None, marks=None, tint=None):
    image = port_base(steel, seed, tint)
    colours = glyph_colours if lit else [dim(c) for c in glyph_colours]
    draw_glyph(image, glyph, colours)
    if direction:
        arrows(image, direction, (IN_LIT if lit else IN_DIM) if direction == "in" else (OUT_LIT if lit else OUT_DIM))
    if marks is not None:
        corner_marks(image, marks if lit else dim(marks))
    return image


def charging_frames(steel, seed, accent):
    """A battery filling up, five frames."""
    frames = []
    for level in range(5):
        image = port_base(steel, seed)
        rect(image, 6, 4, 9, 4, steel[5])
        rect(image, 5, 5, 10, 12, steel[0])
        rect(image, 6, 6, 9, 11, rgb("#14181c"))
        for row in range(level + 1 if level < 4 else 6):
            colour = GLYPH_COLOURS["energy"][2] if level < 4 else IN_LIT
            rect(image, 6, 11 - row, 9, 11 - row, colour)
            put(image, 6, 11 - row, shade(colour, 1.2))
        arrows(image, "out", OUT_LIT)
        frames.append(image)
    return frames


def bearing(steel, seed, accent):
    image = port_base(steel, seed)
    centre = 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            r = math.hypot(x - centre, y - centre)
            if r < 1.6:
                put(image, x, y, steel[0])
            elif r < 2.6:
                put(image, x, y, steel[6] if x + y < 15 else steel[4])
            elif r < 3.6:
                put(image, x, y, accent[1] if (int(math.degrees(math.atan2(y - centre, x - centre))) // 45) % 2
                    else accent[2])
            elif r < 4.6:
                put(image, x, y, steel[5] if x + y < 15 else steel[2])
            elif r < 5.6:
                put(image, x, y, steel[1])
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(image, x, y, steel[5])
    return image


def control_rod(steel, seed, accent, hazard):
    """The drive hatch on the reactor roof: a bolted ring around the rod's drive shaft."""
    image = port_base(steel, seed)
    centre = 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            r = math.hypot(x - centre, y - centre)
            angle = (math.degrees(math.atan2(y - centre, x - centre)) + 360) % 360
            if r < 1.5:
                put(image, x, y, steel[6])
            elif r < 2.5:
                put(image, x, y, steel[4] if x + y < 15 else steel[2])
            elif r < 3.5:
                put(image, x, y, steel[0])
            elif r < 5.2:
                if hazard:
                    colour = accent[1] if int(angle // 30) % 2 else HAZARD_DARK
                else:
                    colour = accent[1] if r < 4.4 else accent[0]
                put(image, x, y, colour)
            elif r < 6.0:
                put(image, x, y, steel[0])
    return image


# The control rod drive the effects draw on a reactor's control rods: a housing on the hatch, a shaft that
# rises as the rod is withdrawn and a crosshead on the shaft. Regions are at 1 texel per 1/16 block and must
# match ControlRodDriveRenderer.
DRIVE_REGIONS = {"housing_top": (0, 0, 8, 8), "housing_side": (8, 0, 8, 4), "shaft_side": (16, 0, 4, 12),
                 "cap_top": (0, 8, 6, 6), "cap_side": (8, 8, 6, 2)}


def control_rod_drive(steel, accent, hazard):
    image = canvas(32, 32)

    x0, y0, w, h = DRIVE_REGIONS["housing_top"]
    rect(image, x0, y0, x0 + w - 1, y0 + h - 1, steel[3])
    rect(image, x0, y0, x0 + w - 1, y0, steel[5])
    rect(image, x0, y0, x0, y0 + h - 1, steel[5])
    rect(image, x0 + 2, y0 + 2, x0 + 5, y0 + 5, steel[0])
    rect(image, x0 + 3, y0 + 3, x0 + 4, y0 + 4, steel[1])
    for x, y in ((1, 1), (6, 1), (1, 6), (6, 6)):
        put(image, x0 + x, y0 + y, steel[6])

    x0, y0, w, h = DRIVE_REGIONS["housing_side"]
    rect(image, x0, y0, x0 + w - 1, y0 + h - 1, steel[2])
    rect(image, x0, y0, x0 + w - 1, y0, steel[5])
    rect(image, x0, y0 + h - 1, x0 + w - 1, y0 + h - 1, steel[0])
    for x in range(w):
        put(image, x0 + x, y0 + 2, accent[1] if x % 4 < 2 or not hazard else HAZARD_DARK)

    x0, y0, w, h = DRIVE_REGIONS["shaft_side"]
    for x, level in enumerate((4, 6, 5, 2)):
        rect(image, x0 + x, y0, x0 + x, y0 + h - 1, steel[level])
    for y in range(2, h, 3):
        rect(image, x0, y0 + y, x0 + w - 1, y0 + y, steel[1])

    x0, y0, w, h = DRIVE_REGIONS["cap_top"]
    rect(image, x0, y0, x0 + w - 1, y0 + h - 1, accent[1])
    rect(image, x0 + 1, y0 + 1, x0 + w - 2, y0 + h - 2, accent[2])
    rect(image, x0 + 2, y0 + 2, x0 + 3, y0 + 3, steel[0])

    x0, y0, w, h = DRIVE_REGIONS["cap_side"]
    for x in range(w):
        striped = hazard and (x // 2) % 2
        rect(image, x0 + x, y0, x0 + x, y0 + h - 1, HAZARD_DARK if striped else accent[1])
    return image


# endregion
# region controllers


SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1 = 4, 4, 11, 9


def controller_frames(steel, seed, machine, state):
    accent = ACCENTS[machine]
    count = {"active": 4, "idle": 2, "error": 2}[state]
    frames = []
    for frame in range(count):
        image = port_base(steel, seed)
        rect(image, 3, 3, 12, 10, steel[0])
        rect(image, SCREEN_X0, SCREEN_Y0, SCREEN_X1, SCREEN_Y1, SCREEN_BG[state])
        screen(image, machine, accent, state, frame)
        # Two buttons and the status lamp under the screen.
        rect(image, 4, 12, 5, 12, steel[4])
        rect(image, 7, 12, 8, 12, steel[4])
        lamp_off, lamp_on = LED[state]
        lit = state == "active" or frame == 0
        rect(image, 10, 12, 11, 12, lamp_on if lit else lamp_off)
        frames.append(image)
    return frames


def screen(image, machine, accent, state, frame):
    if state == "error":
        if frame == 0:
            for y in range(SCREEN_Y0 + 1, SCREEN_Y1 - 1):
                put(image, 7, y, LED["error"][1])
                put(image, 8, y, LED["error"][1])
            put(image, 7, SCREEN_Y1, LED["error"][1])
            put(image, 8, SCREEN_Y1, LED["error"][1])
        return
    ink = accent[2] if state == "active" else dim(accent[2])
    if state == "idle":
        rect(image, SCREEN_X0 + 1, 7, SCREEN_X1 - 1, 7, ink)
        if frame == 0:
            put(image, SCREEN_X1 - 1, 5, accent[2])
        return
    if machine == "reactor":
        heights = [(2, 4, 5, 3, 4, 2), (3, 5, 4, 4, 3, 3), (4, 3, 5, 5, 2, 4), (3, 4, 4, 3, 4, 3)][frame]
        for i, height in enumerate(heights):
            for h in range(height):
                put(image, SCREEN_X0 + 1 + i, SCREEN_Y1 - h, ink if h < 4 else accent[1])
    elif machine == "turbine":
        spokes = [((0, -2), (0, -1), (0, 1), (0, 2), (-2, 0), (-1, 0), (1, 0), (2, 0)),
                  ((-2, -2), (-1, -1), (1, 1), (2, 2), (2, -2), (1, -1), (-1, 1), (-2, 2))][frame % 2]
        cx, cy = 7, 6
        for dx, dy in spokes:
            put(image, cx + dx, cy + dy, ink)
        put(image, cx, cy, accent[1])
        rect(image, SCREEN_X0, SCREEN_Y1, SCREEN_X0 + 2 + frame, SCREEN_Y1, accent[1])
    else:
        rect(image, SCREEN_X0 + 1, 5, SCREEN_X1 - 1, 7, accent[0])
        rect(image, SCREEN_X0 + 1, 5, SCREEN_X0 + 1 + frame * 2, 7, ink)
        rect(image, SCREEN_X0 + 1, 9, SCREEN_X0 + 2, 9, ink)


# endregion
# region rods and rotor


def fuel_rod_side(steel, accent, vertical):
    """The fuel rod cage: three posts per face (columns 0-2, 6-9, 13-15) and banded rings (rows 0-1, 14-15)."""
    image = canvas()
    rect(image, 0, 0, 15, 15, steel[1])
    for x0, x1 in ((0, 2), (6, 9), (13, 15)):
        width = x1 - x0 + 1
        for x in range(x0, x1 + 1):
            level = (6, 5, 3, 2)[min(3, (x - x0) * 4 // width)]
            for y in range(16):
                put(image, x, y, steel[level])
    for y, level in ((0, 0), (1, 4), (14, 4), (15, 0)):
        for x in range(16):
            put(image, x, y, steel[level])
    for x in range(16):
        put(image, x, 1, accent[1] if x % 4 < 2 else accent[0])
        put(image, x, 14, accent[1] if x % 4 < 2 else accent[0])
    if not vertical:
        image = np.transpose(image, (1, 0, 2)).copy()
    return image


def fuel_rod_cap(steel, accent):
    image = canvas()
    rect(image, 0, 0, 15, 15, steel[3])
    for d in range(3):
        for along in range(16):
            for side in "UDLR":
                put(image, *edge_pixels(side, d, along), steel[(1, 5, 3)[d]])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (8, 14), (1, 8), (14, 7)):
        put(image, x, y, accent[1])
    rect(image, 3, 3, 12, 12, steel[0])
    return image


def rotor_shaft(steel):
    """Only rows 0-3 are sampled, lengthwise along u; machined bands every eight pixels."""
    image = canvas()
    rows = (4, 6, 4, 2)
    for y in range(16):
        for x in range(16):
            level = rows[y % 4]
            if x % 8 == 0:
                level = max(0, level - 2)
            put(image, x, y, steel[level])
    return image


def rotor_blade(steel, accent):
    """Only rows 0-1 are sampled, root (u 0) to tip (u 15): a lit leading edge and a painted tip."""
    image = canvas()
    for y in range(16):
        for x in range(16):
            level = 6 if y % 2 == 0 else 4
            if x < 2:
                level -= 2
            colour = steel[level]
            if x >= 14:
                colour = accent[2] if y % 2 == 0 else accent[1]
            put(image, x, y, colour)
    return image


# endregion
# region fuel column and storage blocks


def fuel_column_frames(width, frames, seed):
    """Stacked pellets in light greys: the reactant colour tints them in game."""
    noise = tileable_noise(seed, 1, width, width)
    result = []
    for frame in range(frames):
        image = canvas(width, width)
        for y in range(width):
            for x in range(width):
                pellet_seam = y % 4 == 3
                glow = math.sin(2 * math.pi * (y / width - frame / frames))
                value = 228 + 12 * glow + 5 * noise[y, x] - (34 if pellet_seam else 0)
                put(image, x, y, (value, value, value), 225)
        result.append(image)
    return result


def fuel_column_top_frames(frames):
    result = []
    for frame in range(frames):
        image = canvas()
        pulse = math.sin(2 * math.pi * frame / frames)
        for y in range(16):
            for x in range(16):
                # Pellet ends on a 4 px pitch, like the seams on the sides.
                r = math.hypot((x % 4) - 1.5, (y % 4) - 1.5)
                value = 236 + 10 * pulse - (36 if r > 1.9 else 0) - (10 if r > 1.2 else 0)
                put(image, x, y, (value, value, value), 225)
        result.append(image)
    return result


def storage_block(base, seed):
    """A vanilla-style metal block: bevelled rim and two riveted plates, in the block's own colour."""
    levels = [shade(base, f) for f in (0.42, 0.6, 0.76, 0.9, 1.0, 1.12, 1.26)]
    noise = tileable_noise(seed, 2)
    image = canvas()
    for y in range(16):
        for x in range(16):
            put(image, x, y, levels[int(np.clip(np.rint(3.6 + noise[y, x] * 0.45), 3, 4))])
    for along in range(16):
        put(image, along, 0, levels[6])
        put(image, 0, along, levels[6])
        put(image, along, 15, levels[1])
        put(image, 15, along, levels[1])
    for along in range(1, 15):
        put(image, along, 1, levels[5])
        put(image, 1, along, levels[5])
        put(image, along, 14, levels[2])
        put(image, 14, along, levels[2])
    for along in range(2, 14):
        put(image, along, 7, levels[2])
        put(image, along, 8, levels[5])
    for x, y in ((3, 3), (12, 3), (3, 11), (12, 11)):
        put(image, x, y, levels[6])
        put(image, x + 1, y + 1, levels[1])
    return image


def graphite_block(seed):
    levels = ramp("#1d1e20", "#2a2b2e", "#36383b", "#43464a", "#52565b", "#676c72")
    noise = tileable_noise(seed, 1)
    image = canvas()
    for y in range(16):
        for x in range(16):
            put(image, x, y, levels[int(np.clip(np.rint(2.6 + noise[y, x] * 0.6), 1, 4))])
    # Stacked graphite bricks, offset every other course.
    for y in (0, 8):
        for x in range(16):
            put(image, x, y, levels[0])
            put(image, x, y + 1, levels[5])
    for y0, x in ((1, 0), (9, 8)):
        for y in range(y0, y0 + 7):
            put(image, x, y, levels[0])
            put(image, (x + 1) % 16, y, levels[4])
    return image


# Each material's colour, from the mid tone of its ER2 texture.
STORAGE_BLOCKS = {
    "yellorium_block": rgb("#ece259"),
    "cyanite_block": rgb("#71b2d1"),
    "blutonium_block": rgb("#2121bf"),
    "magentite_block": rgb("#ec29ec"),
    "ludicrite_block": rgb("#8408b8"),
    "ridiculite_block": rgb("#dac4dd"),
    "inanite_block": rgb("#f73589"),
    "insanite_block": rgb("#4efbb7"),
}


# endregion
# region output


def save(path, frames, frametime=None, frame_order=None):
    frames = frames if isinstance(frames, list) else [frames]
    sheet = np.concatenate(frames, axis=0)
    Image.fromarray(np.clip(np.rint(sheet), 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)
    meta = path.with_name(path.name + ".mcmeta")
    if len(frames) > 1:
        animation = {"frametime": frametime or 1}
        if frame_order is not None:
            animation["frames"] = frame_order
        meta.write_text(json.dumps({"animation": animation}, indent=2) + "\n")
    elif meta.exists():
        meta.unlink()


class Drawer:
    """Draws the textures of one machine folder (reactor/basic, turbine/reinforced, reprocessor, ...)."""

    def __init__(self, machine, steel, hazard):
        self.machine, self.steel, self.hazard = machine, steel, hazard
        self.accent = ACCENTS[machine]

    def seed(self, name):
        return sum(ord(c) * (i + 1) for i, c in enumerate(f"{self.machine}/{name}")) % 100000

    def draw(self, name):
        """Returns (frames, frametime, frame order) for a texture name, or raises KeyError."""
        steel, accent, seed = self.steel, self.accent, self.seed(name)
        hot_is_input = self.machine == "turbine"

        if name in CASING_SIDES:
            return casing(steel, accent, self.hazard, CASING_SIDES[name], seed), None, None
        if name.startswith("glass."):
            return glass(steel, self.machine, self.hazard, int(name.split(".")[1])), None, None
        if name == "controller":
            return controller_frames(steel, seed, self.machine, "error"), 10, None
        if name == "controller_off":
            return controller_frames(steel, seed, self.machine, "idle"), 25, None
        if name == "controller_on":
            return controller_frames(steel, seed, self.machine, "active"), 8, None
        if name == "charging":
            return charging_frames(steel, seed, accent), 3, [0, 1, 2, 3, 4, 4, 4]
        if name == "controlrod":
            return control_rod(steel, seed, accent, self.hazard), None, None
        if name == "fuelrod_vertical":
            return fuel_rod_side(steel, accent, True), None, None
        if name == "fuelrod_horizontal":
            return fuel_rod_side(steel, accent, False), None, None
        if name == "fuelrod_cap":
            return fuel_rod_cap(steel, accent), None, None
        if name == "bearing":
            return bearing(steel, seed, accent), None, None
        if name == "rotorshaft":
            return rotor_shaft(steel), None, None
        if name == "rotorblade":
            return rotor_blade(steel, accent), None, None
        if name == "platingio":
            image = plating(steel, seed)
            slim_frame(image, steel)
            return image, None, None

        connected = name.endswith("_connected") or name.endswith("_on")
        base = name.removesuffix("_connected").removesuffix("_on")
        glyph = GLYPH_COLOURS

        ports = {
            "accessport_solid_in": ("ingot", glyph["fuel"], "in", None, None),
            "accessport_solid_out": ("ingot", glyph["waste"], "out", None, None),
            "accessport_fluid_in": ("droplet", glyph["fuel"], "in", None, None),
            "accessport_fluid_out": ("droplet", glyph["waste"], "out", None, None),
            "computerport": ("terminal", glyph["terminal"], None, None, None),
            "redstoneport": ("torch", glyph["redstone"], None, None, None),
            "creative_water_generator": ("droplet", glyph["water"], "in", CREATIVE[1], CREATIVE[0]),
            "creative_steam_generator": ("steam", glyph["steam"], "in", CREATIVE[1], CREATIVE[0]),
            "wasteinjector": ("ingot", glyph["waste"], "in", None, None),
            "solidinjector": ("ingot", glyph["fuel"], "in", None, None),
            "fluidinjector": ("droplet", glyph["water"], "in", None, None),
            "collector": ("funnel", glyph["blutonium"], "out", None, None),
            "outputport": (("ingot", glyph["blutonium"]) if self.machine == "reprocessor"
                           else ("droplet", glyph["fuel"])) + ("out", None, None),
        }
        if base in ports:
            shape, colours, direction, marks, tint = ports[base]
            always_lit = base.startswith("creative") or base == "collector"
            return port(steel, seed, shape, colours, connected or always_lit, direction, marks, tint), None, None

        if base in ("powertap_fe_active", "powertap_fe_passive", "powerport"):
            marks = accent[2] if base.endswith("_active") else None
            direction = "in" if base == "powerport" else "out"
            return port(steel, seed, "bolt", glyph["energy"], True, direction, marks), None, None

        if base.startswith("fluidport_"):
            _, api, mode, temperature = base.split("_")
            hot = temperature == "hot"
            is_input = hot == hot_is_input
            marks = MEKANISM[1] if api == "mekanism" else (accent[2] if mode == "active" else None)
            shape, colours = ("steam", glyph["steam"]) if hot else ("droplet", glyph["water"])
            return port(steel, seed, shape, colours, connected, "in" if is_input else "out", marks), None, None

        raise KeyError(name)


FOLDERS = {
    "block/reactor/basic": Drawer("reactor", STEEL_BASIC, False),
    "block/reactor/reinforced": Drawer("reactor", STEEL_REINFORCED, True),
    "block/turbine/basic": Drawer("turbine", STEEL_BASIC, False),
    "block/turbine/reinforced": Drawer("turbine", STEEL_REINFORCED, True),
    "block/reprocessor": Drawer("reprocessor", STEEL_BASIC, False),
    "block/fluidizer": Drawer("fluidizer", STEEL_BASIC, False),
}


def generate(root):
    written = 0
    for folder, drawer in FOLDERS.items():
        for path in sorted((root / folder).glob("*.png")):
            frames, frametime, order = drawer.draw(path.stem)
            save(path, frames, frametime, order)
            written += 1

    for name, colour in STORAGE_BLOCKS.items():
        save(root / "block" / f"{name}.png", storage_block(colour, sum(map(ord, name))))
        written += 1
    save(root / "block" / "graphite_block.png", graphite_block(7))
    written += 1

    save(root / "fluid" / "fluid.fuelcolumn.flowing.png", fuel_column_frames(32, 16, 11), 3)
    save(root / "fluid" / "fluid.fuelcolumn.still.png", fuel_column_top_frames(16), 3)
    written += 2

    (root / "effects").mkdir(exist_ok=True)
    save(root / "effects" / "control_rod_drive_basic.png", control_rod_drive(STEEL_BASIC, ACCENTS["reactor"], False))
    save(root / "effects" / "control_rod_drive_reinforced.png",
         control_rod_drive(STEEL_REINFORCED, ACCENTS["reactor"], True))
    return written + 2


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", type=pathlib.Path, default=DEFAULT_ROOT)
    args = parser.parse_args()
    try:
        count = generate(args.root)
    except KeyError as error:
        print(f"No drawing for texture {error}", file=sys.stderr)
        return 1
    print(f"Wrote {count} textures under {args.root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
