GLYPH_UV = [0, 0, 16, 16]
EDGE = (24, 36, 45, 255)
DARK = (12, 18, 24, 255)
METAL = (52, 67, 77, 255)
LIGHT = (150, 168, 176, 255)


class Profile:
    def __init__(self, id, rgb, mark, body, shape, particle, sound):
        self.id = id
        self.rgb = rgb
        self.mark = mark
        self.body = body
        self.shape = shape
        self.particle = particle
        self.sound = sound


def _mark(text):
    rows = text.strip("\n").split("\n")
    if len(rows) != 8 or any(len(row) != 8 for row in rows):
        raise ValueError("mark must be 8x8:\n" + text)
    return tuple(rows)


PROFILES = [
    Profile("condensed", (255, 168, 32), _mark("""
########
..####..
...##...
...##...
...##...
...##...
.######.
########
"""), "rocket", "burst", "flame", "explode"),
    Profile("shrapnel", (214, 220, 230), _mark("""
#..##..#
.#.##.#.
..#..#..
##....##
##....##
..#..#..
.#.##.#.
#..##..#
"""), "rocket", "burst", "flame", "explode"),
    Profile("incendiary", (255, 72, 0), _mark("""
...##...
..#++#..
.#+##+#.
##+##+##
.#+##+#.
..#++#..
...##...
....##..
"""), "flame", "column", "flame", "firecharge"),
    Profile("debilitation", (186, 72, 255), _mark("""
...##...
..####..
.######.
.######.
.######.
..####..
...##...
....#...
"""), "canister", "cloud", "sneeze", "explode"),
    Profile("chemical", (130, 255, 48), _mark("""
.######.
##.##.##
##.##.##
.######.
...##...
.######.
##....##
.######.
"""), "canister", "cloud", "sneeze", "explode"),
    Profile("anvil", (96, 108, 128), _mark("""
########
.######.
..####..
..####..
.######.
########
########
.######.
"""), "rocket", "burst", "flame", "explode"),
    Profile("repulsive", (0, 112, 255), _mark("""
#......#
##....##
.#....#.
..#..#..
..#..#..
.#....#.
##....##
#......#
"""), "rocket", "ring", "end_rod", "explode"),
    Profile("attractive", (255, 32, 144), _mark("""
..#..#..
.#.##.#.
#..##..#
...##...
...##...
#..##..#
.#.##.#.
..#..#..
"""), "rocket", "vortex", "portal", "explode"),
    Profile("fragmentation", (255, 208, 64), _mark("""
.######.
##.##.##
#..##..#
###..###
###..###
#..##..#
##.##.##
.######.
"""), "rocket", "burst", "flame", "explode"),
    Profile("contagious", (0, 176, 96), _mark("""
.##..##.
#..##..#
.##..##.
...##...
.##..##.
#..##..#
.##..##.
........
"""), "canister", "cloud", "sneeze", "explode"),
    Profile("sonic", (0, 255, 214), _mark("""
#......#
.#....#.
..#..#..
...##...
...##...
..#..#..
.#....#.
#......#
"""), "coil", "spark", "electric_spark", "sonic_boom"),
    Profile("breaching", (196, 112, 48), _mark("""
...##...
...##...
..####..
.######.
..####..
...##...
...##...
...##...
"""), "rocket", "column", "flame", "explode"),
    Profile("thermobaric", (176, 32, 0), _mark("""
...##...
...##...
.######.
########
.######.
...##...
...##...
...##...
"""), "warhead", "column", "flame", "explode"),
    Profile("nuclear", (196, 255, 48), _mark("""
.#....#.
##.##.##
.#.##.#.
..#..#..
.#.##.#.
##.##.##
.#....#.
...##...
"""), "warhead", "mushroom", "flame", "explode"),
    Profile("emp", (96, 196, 255), _mark("""
....##..
...##...
..##....
.#####..
...##...
..##....
.##.....
##......
"""), "coil", "spark", "electric_spark", "sonic_boom"),
    Profile("exothermic", (255, 128, 64), _mark("""
##....##
.######.
##.##.##
.######.
##.##.##
.######.
##....##
........
"""), "flame", "column", "flame", "firecharge"),
    Profile("endothermic", (168, 232, 255), _mark("""
#..##..#
.#.##.#.
########
#..##..#
########
.#.##.#.
#..##..#
...##...
"""), "rocket", "snow", "snowflake", "glass"),
    Profile("antigravitational", (80, 48, 255), _mark("""
...##...
..####..
.######.
...##...
...##...
...##...
...##...
..####..
"""), "rocket", "column", "end_rod", "anchor"),
    Profile("ender", (112, 0, 224), _mark("""
.######.
##....##
#..##..#
#.####.#
#.####.#
#..##..#
##....##
.######.
"""), "core", "portal", "reverse_portal", "teleport"),
    Profile("antimatter", (96, 32, 160), _mark("""
########
##....##
#.####.#
#.#..#.#
#.#..#.#
#.####.#
##....##
########
"""), "core", "portal", "reverse_portal", "explode"),
    Profile("redmatter", (255, 16, 48), _mark("""
######..
#....#..
#.#####.
#.#...#.
#.#.#.#.
#.#.#.#.
#.###.#.
#.....#.
"""), "core", "vortex", "reverse_portal", "anchor"),
    Profile("colors", (255, 96, 196), _mark("""
########
########
########
########
########
########
########
########
"""), "canister", "prismatic", "flame", "twinkle"),
    Profile("smoke", (144, 148, 156), _mark("""
.######.
########
##.##.##
########
.######.
..####..
...##...
...##...
"""), "canister", "cloud", "smoke", "explode"),
]

GRENADE_IDS = frozenset({
    "condensed", "shrapnel", "incendiary", "debilitation", "chemical", "anvil",
    "repulsive", "attractive", "colors", "smoke",
})
BODIES = ("rocket", "flame", "canister", "warhead", "core", "coil")
SHAPES = ("ring", "burst", "column", "mushroom", "vortex", "snow", "spark", "cloud", "portal", "prismatic")
PARTICLES = ("flame", "smoke", "snowflake", "reverse_portal", "electric_spark", "sneeze", "end_rod", "portal")
SOUNDS = ("explode", "sonic_boom", "anchor", "teleport", "twinkle", "firecharge", "glass")


def validate_profiles():
    ids = [profile.id for profile in PROFILES]
    if len(ids) != 23 or len(set(ids)) != 23:
        raise ValueError("expected 23 distinct payload ids")
    unknown = GRENADE_IDS.difference(ids)
    if unknown:
        raise ValueError("grenade id is not a payload: " + ", ".join(sorted(unknown)))
    if len({profile.rgb for profile in PROFILES}) != 23:
        raise ValueError("payload colors must be unique")
    for profile in PROFILES:
        if profile.body not in BODIES or profile.shape not in SHAPES:
            raise ValueError("bad body or shape for " + profile.id)
        if profile.particle not in PARTICLES or profile.sound not in SOUNDS:
            raise ValueError("bad particle or sound for " + profile.id)
    focus = [profile.body for profile in PROFILES if profile.id in ("nuclear", "chemical", "incendiary", "redmatter")]
    if len(set(focus)) != 4:
        raise ValueError("nuclear, chemical, incendiary, and redmatter must use different bodies")
    pixels = {tuple(paint_label(profile)) for profile in PROFILES}
    if len(pixels) != 23:
        raise ValueError("payload labels must not be pixel-identical")


def paint_label(profile):
    pixels = []
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                pixels.append(EDGE)
            elif (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                pixels.append(LIGHT)
            else:
                jitter = ((x * 11 + y * 7) % 3) * 4
                pixels.append(tuple(min(255, channel + jitter) for channel in METAL[:3]) + (255,))
    color = profile.rgb
    bright = tuple(min(255, channel + 80) for channel in color)
    shade = tuple(max(0, channel - 60) for channel in color)
    if profile.id == "colors":
        rainbow = ((255, 48, 48), (255, 160, 32), (255, 230, 48), (48, 220, 80), (48, 140, 255), (190, 64, 255))
        for index, stripe in enumerate(rainbow):
            for y in range(4, 12):
                pixels[y * 16 + 4 + index] = stripe + (255,)
        return pixels
    for dy, row in enumerate(profile.mark):
        for dx, cell in enumerate(row):
            if cell == " ":
                pixels[(4 + dy) * 16 + 4 + dx] = DARK
            elif cell == "#":
                pixels[(4 + dy) * 16 + 4 + dx] = color + (255,)
            elif cell == "+":
                pixels[(4 + dy) * 16 + 4 + dx] = bright + (255,)
            elif cell == ".":
                pixels[(4 + dy) * 16 + 4 + dx] = shade + (255,)
    return pixels


def catalog():
    return {
        profile.id: {
            "rgb": list(profile.rgb),
            "shape": profile.shape,
            "particle": profile.particle,
            "sound": profile.sound,
        }
        for profile in PROFILES
    }


def radiation_pixels():
    pixels = [(16, 18, 14, 255)] * 256
    mark = (
        "    # #     ",
        "   ## ##    ",
        "  # ### #   ",
        "    # #     ",
        "  # ### #   ",
        "   ## ##    ",
        "    # #     ",
        "    ###     ",
    )
    for dy, row in enumerate(mark):
        for dx, cell in enumerate(row):
            if cell == "#":
                pixels[(4 + dy) * 16 + 2 + dx] = (196, 255, 48, 255)
    return pixels


def core_pixels():
    pixels = []
    for y in range(16):
        for x in range(16):
            scar = (x * 3 + y) % 11 == 0 or (y * 5 - x) % 13 == 0
            pixels.append((90, 8, 16, 255) if scar else (6, 0, 2, 255))
    return pixels


def disc_pixels():
    pixels = []
    for y in range(16):
        for x in range(16):
            spoke = x % 4 == 0
            inner = y < 6
            if spoke and inner:
                pixels.append((255, 220, 220, 230))
            elif inner:
                pixels.append((255, 80, 90, 180))
            elif spoke:
                pixels.append((180, 20, 30, 90))
            else:
                pixels.append((0, 0, 0, 0))
    return pixels


validate_profiles()
