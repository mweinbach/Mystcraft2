#!/usr/bin/env python3
"""Renders explosive block models and faces to a contact sheet PNG without a game client.

A small software rasteriser for Minecraft JSON block models: axis-aligned elements, per-face
textures and UVs, a z-buffer, back-face culling and vanilla-like directional face shading. It
draws each explosive as its inventory icon (the item models inherit the block model) and as flat
front, top and bottom views, so drawn bevels or fake depth are easy to spot.

    python3 tools/render_contact_sheet.py --assets src/main/resources/assets/icbm_port \\
        --out docs/images/explosives-after.png --title "After"

Needs Pillow. It is a documentation helper and is not part of CI.
"""
import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from payload_visuals import PROFILES  # noqa: E402

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
NORMAL = {
    "up": (0, 1, 0),
    "down": (0, -1, 0),
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "west": (-1, 0, 0),
    "east": (1, 0, 0),
}
VANILLA_CUBES = {
    "minecraft:block/cube_bottom_top": {"up": "#top", "down": "#bottom", "side": "#side"},
    "minecraft:block/cube_all": {"up": "#all", "down": "#all", "side": "#all"},
    "minecraft:block/cube_column": {"up": "#end", "down": "#end", "side": "#side"},
}


def corners(side, lo, hi):
    """Face origin, +s corner and +t corner, matching vanilla UV orientation."""
    x1, y1, z1 = lo
    x2, y2, z2 = hi
    return {
        "up": ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        "down": ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
        "north": ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        "south": ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        "west": ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        "east": ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
    }[side]


def default_uv(side, lo, hi):
    x1, y1, z1 = lo
    x2, y2, z2 = hi
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[side]


class Assets:
    def __init__(self, root):
        self.root = Path(root)
        self.cache = {}

    def texture(self, ref):
        if ref not in self.cache:
            namespace, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
            if namespace != "icbm_port":
                raise SystemExit("only icbm_port textures are rendered: " + ref)
            image = Image.open(self.root / "textures" / (path + ".png")).convert("RGBA")
            # Animated strips: first frame only.
            self.cache[ref] = image.crop((0, 0, image.width, image.width))
        return self.cache[ref]

    def model(self, name):
        data = json.loads((self.root / "models" / (name + ".json")).read_text())
        textures = dict(data.get("textures", {}))
        parent = data.get("parent", "")
        elements = data.get("elements")
        if elements is None and parent in VANILLA_CUBES:
            faces = VANILLA_CUBES[parent]
            elements = [
                {
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {
                        side: {"texture": faces.get(side, faces["side"])} for side in NORMAL
                    },
                }
            ]
        elif elements is None and parent.startswith("icbm_port:"):
            parent_elements, parent_textures = self.model(parent.split(":", 1)[1])
            elements = parent_elements
            textures = {**parent_textures, **textures}
        return elements, textures

    def resolve(self, ref, textures):
        seen = 0
        while ref.startswith("#") and seen < 8:
            ref = textures[ref[1:]]
            seen += 1
        return ref


def rotate(point, yaw, pitch):
    x, y, z = point
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    x, z = x * cy + z * sy, -x * sy + z * cy
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    y, z = y * cp - z * sp, y * sp + z * cp
    return x, y, z


def render(assets, model, size, yaw, pitch, scale):
    elements, textures = assets.model(model)
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pixels = image.load()
    depth = [[-math.inf] * size for _ in range(size)]
    unit = size * scale / 16

    def project(point):
        x, y, z = rotate((point[0] - 8, point[1] - 8, point[2] - 8), yaw, pitch)
        return size / 2 + x * unit, size / 2 - y * unit, z

    for element in elements:
        lo, hi = element["from"], element["to"]
        for side, face in element["faces"].items():
            normal = rotate(NORMAL[side], yaw, pitch)
            if normal[2] <= 1e-6:
                continue
            texture = assets.texture(assets.resolve(face["texture"], textures))
            tw, th = texture.size
            tex = texture.load()
            u0, v0, u1, v1 = face.get("uv", default_uv(side, lo, hi))
            p0, p1, p3 = (project(c) for c in corners(side, lo, hi))
            ax, ay = p1[0] - p0[0], p1[1] - p0[1]
            bx, by = p3[0] - p0[0], p3[1] - p0[1]
            det = ax * by - ay * bx
            if abs(det) < 1e-9:
                continue
            xs = [p0[0], p1[0], p3[0], p1[0] + bx]
            ys = [p0[1], p1[1], p3[1], p1[1] + by]
            shade = SHADE[side]
            for py in range(max(0, int(min(ys))), min(size, int(math.ceil(max(ys))) + 1)):
                for px in range(max(0, int(min(xs))), min(size, int(math.ceil(max(xs))) + 1)):
                    qx, qy = px + 0.5 - p0[0], py + 0.5 - p0[1]
                    s = (qx * by - qy * bx) / det
                    t = (ax * qy - ay * qx) / det
                    if not (0 <= s <= 1 and 0 <= t <= 1):
                        continue
                    d = p0[2] + s * (p1[2] - p0[2]) + t * (p3[2] - p0[2])
                    if d <= depth[py][px]:
                        continue
                    u = u0 + s * (u1 - u0)
                    v = v0 + t * (v1 - v0)
                    tx = min(tw - 1, max(0, int(u * tw / 16)))
                    ty = min(th - 1, max(0, int(v * th / 16)))
                    r, g, b, a = tex[tx, ty]
                    if a < 128:
                        continue
                    depth[py][px] = d
                    pixels[px, py] = (int(r * shade), int(g * shade), int(b * shade), 255)
    return image


def sheet(assets, title, out, ids, columns=4, icon=72, view=48, upscale=2):
    font = ImageFont.load_default()
    cell_w = (icon + 3 * view + 4 * 6) * upscale
    cell_h = (icon + 18) * upscale
    rows = math.ceil(len(ids) / columns)
    header = 40
    canvas = Image.new("RGBA", (columns * cell_w + 16, rows * cell_h + header + 16), (198, 198, 198, 255))
    draw = ImageDraw.Draw(canvas)
    draw.text((12, 12), title + "  (inventory icon | front | top | bottom)", fill=(20, 20, 20, 255), font=font)
    for index, payload in enumerate(ids):
        model = "block/" + payload + "_explosive"
        x0 = 8 + (index % columns) * cell_w
        y0 = header + (index // columns) * cell_h
        tiles = [
            render(assets, model, icon, 225, 30, 0.625),
            render(assets, model, view, 0, 0, 0.94),
            render(assets, model, view, 0, 90, 0.94),
            render(assets, model, view, 0, -90, 0.94),
        ]
        x = x0
        for n, tile in enumerate(tiles):
            big = tile.resize((tile.width * upscale, tile.height * upscale), Image.NEAREST)
            y = y0 + (0 if n == 0 else (icon - view) * upscale // 2)
            canvas.alpha_composite(big, (x, y))
            x += big.width + 6 * upscale
        draw.text((x0, y0 + icon * upscale + 2), payload, fill=(20, 20, 20, 255), font=font)
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out, optimize=True)


def compare(before, after, out, ids, icon=96, upscale=2):
    font = ImageFont.load_default()
    pad = 10
    width = len(ids) * (icon * upscale + pad) + pad + 60
    height = 2 * (icon * upscale + 20) + 30
    canvas = Image.new("RGBA", (width, height), (198, 198, 198, 255))
    draw = ImageDraw.Draw(canvas)
    for row, (label, assets) in enumerate((("Before", before), ("After", after))):
        y = 20 + row * (icon * upscale + 20)
        draw.text((pad, y + icon * upscale // 2), label, fill=(20, 20, 20, 255), font=font)
        for column, payload in enumerate(ids):
            tile = render(assets, "block/" + payload + "_explosive", icon, 225, 30, 0.625)
            x = 60 + pad + column * (icon * upscale + pad)
            canvas.alpha_composite(tile.resize((icon * upscale, icon * upscale), Image.NEAREST), (x, y))
            if row == 0:
                draw.text((x, 6), payload, fill=(20, 20, 20, 255), font=font)
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out, optimize=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", required=True, help="assets/icbm_port directory")
    parser.add_argument("--out", required=True)
    parser.add_argument("--title", default="Explosives")
    parser.add_argument("--before", help="assets directory of the earlier art, for --compare")
    parser.add_argument("--compare", help="comma-separated payload ids for a before/after strip")
    args = parser.parse_args()
    assets = Assets(args.assets)
    if args.compare:
        compare(Assets(args.before), assets, Path(args.out), args.compare.split(","))
    else:
        sheet(assets, args.title, Path(args.out), [profile.id for profile in PROFILES])


if __name__ == "__main__":
    main()
