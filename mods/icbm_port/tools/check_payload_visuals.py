#!/usr/bin/env python3
"""Prove payload labels, missile UVs, silhouettes, and blast colors stay distinct."""
import json
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from payload_visuals import GLYPH_UV, PROFILES, validate_profiles

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/icbm_port"
FOCUS = ("nuclear", "chemical", "incendiary", "redmatter")


def png_bytes(path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit("not a png: " + str(path))
    return data


def fail(message):
    raise SystemExit(message)


def geometry(path):
    model = json.loads(path.read_text())
    boxes = []
    for element in model["elements"]:
        textures = tuple(face["texture"] for face in element["faces"].values())
        boxes.append((tuple(element["from"]), tuple(element["to"]), textures))
    return tuple(boxes)


def label_uvs(path):
    model = json.loads(path.read_text())
    found = False
    for element in model["elements"]:
        for face in element["faces"].values():
            if face.get("texture") != "#label":
                continue
            found = True
            if face.get("uv") != GLYPH_UV:
                fail(path.name + " label face does not use the full glyph uv")
    if not found:
        fail(path.name + " has no label face")


def main():
    validate_profiles()
    catalog = json.loads((ROOT / "payload_visuals.json").read_text())
    if set(catalog) != {profile.id for profile in PROFILES}:
        fail("payload_visuals.json does not match the profile table")
    colors = []
    for profile in PROFILES:
        entry = catalog[profile.id]
        rgb = tuple(entry["rgb"])
        if rgb != profile.rgb or entry["shape"] != profile.shape:
            fail("catalog drifted from " + profile.id)
        colors.append(rgb)
        png_bytes(ROOT / "textures/block" / f"payload_{profile.id}.png")
        label_uvs(ROOT / "models/item" / f"{profile.id}_missile.json")
        label_uvs(ROOT / "models/block" / f"{profile.id}_explosive.json")
        label_uvs(ROOT / "models/item" / f"{profile.id}_bomb_cart.json")
    if len(set(colors)) != len(colors):
        fail("blast colors are not unique")
    bodies = {}
    pixels = {}
    shapes = {}
    for profile in PROFILES:
        raw = png_bytes(ROOT / "textures/block" / f"payload_{profile.id}.png")
        pixels.setdefault(raw, []).append(profile.id)
        shape = geometry(ROOT / "models/item" / f"{profile.id}_missile.json")
        shapes.setdefault(shape, []).append(profile.id)
        bodies[profile.id] = profile.body
    twins = [group for group in pixels.values() if len(group) > 1]
    if twins:
        fail("pixel-identical labels: " + str(twins))
    if len({bodies[name] for name in FOCUS}) != 4:
        fail("focus payloads share a missile body")
    for name in FOCUS:
        mates = [other for other in shapes[geometry(ROOT / "models/item" / f"{name}_missile.json")] if other != name]
        if any(other in FOCUS for other in mates):
            fail(name + " shares missile geometry with another focus payload")
    for name in ("redmatter_core.png", "redmatter_disc.png"):
        png_bytes(ROOT / "textures/entity" / name)
    png_bytes(ROOT / "textures/block/radiation.png")
    exhaust = ROOT / "textures/block/exhaust.png"
    meta = json.loads(exhaust.with_suffix(".png.mcmeta").read_text())
    width, height = png_size(exhaust)
    if height != width * 4 or meta["animation"]["frametime"] < 1:
        fail("exhaust strip is not a four-frame animation")
    print(f"PASS: {len(PROFILES)} payload visuals, distinct focus silhouettes, full glyph uvs.")


def png_size(path):
    data = path.read_bytes()
    return struct.unpack(">II", data[16:24])


if __name__ == "__main__":
    main()
