#!/usr/bin/env python3
import json
import math
import re
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from payload_visuals import GLYPH_UV, GRENADE_IDS, PARTICLES, PROFILES, SHAPES, SOUNDS, validate_profiles

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


PLACARD = (4, 4, 12, 12)
FACE = {"north": (2, -1, 0, True), "south": (2, 1, 0, False), "west": (0, -1, 2, False), "east": (0, 1, 2, True)}


def _to_face(element, side, s, t):
    lo, hi = element["from"], element["to"]
    _, _, axis, high = FACE[side]
    span = hi[axis] - lo[axis]
    horizontal = hi[axis] - s * span if high else lo[axis] + s * span
    vertical = hi[1] - t * (hi[1] - lo[1])
    return horizontal, vertical


def _prism(element, side, uv):
    u0, v0, u1, v1 = uv
    s_values = sorted(min(max((mark - u0) / (u1 - u0), 0), 1) for mark in (PLACARD[0], PLACARD[2]))
    t_values = sorted(min(max((mark - v0) / (v1 - v0), 0), 1) for mark in (PLACARD[1], PLACARD[3]))
    normal, sign, axis, _ = FACE[side]
    corners = [_to_face(element, side, s, t) for s in s_values for t in t_values]
    lo, hi = [-math.inf, -math.inf, -math.inf], [math.inf, math.inf, math.inf]
    lo[axis], hi[axis] = min(corner[0] for corner in corners), max(corner[0] for corner in corners)
    lo[1], hi[1] = min(corner[1] for corner in corners), max(corner[1] for corner in corners)
    if sign > 0:
        lo[normal] = element["to"][normal]
    else:
        hi[normal] = element["from"][normal]
    return lo, hi


def label_is_clear(path):
    model = json.loads(path.read_text())
    for index, element in enumerate(model["elements"]):
        for side, face in element["faces"].items():
            if side not in FACE or face.get("texture") != "#label":
                continue
            lo, hi = _prism(element, side, face.get("uv", GLYPH_UV))
            for other_index, other in enumerate(model["elements"]):
                if other_index == index:
                    continue
                if all(min(a, b) - max(c, d) > 1e-6 for a, b, c, d in zip(hi, other["to"], lo, other["from"])):
                    fail(path.name + " " + side + " glyph is covered")


def flight_axis(path):
    model = json.loads(path.read_text())
    exhaust_at_tail = False
    tip_at_nose = False
    for element in model["elements"]:
        textures = {face["texture"] for face in element["faces"].values()}
        if "#exhaust" in textures and element["from"][1] == 0:
            exhaust_at_tail = True
        if element["to"][1] == 16:
            tip_at_nose = True
    if not exhaust_at_tail or not tip_at_nose:
        fail(path.name + " does not keep exhaust at y=0 and a tip at y=16")


def client_tokens():
    source = Path(__file__).resolve().parents[1] / "src/main/java/dev/icbm/port/client/PayloadVisuals.java"
    text = source.read_text()
    found = {}
    for name in ("Shape", "Particle", "Sound"):
        match = re.search(r"enum " + name + r" \{([^}]*)}", text)
        if not match:
            fail("missing client enum " + name)
        found[name] = {token.lower() for token in re.findall(r"\b[A-Z][A-Z0-9_]*\b", match.group(1))}
    expected = {"Shape": set(SHAPES), "Particle": set(PARTICLES), "Sound": set(SOUNDS)}
    for name, tokens in expected.items():
        if found[name] != tokens:
            fail(name + " drifted from the profile table")


def main():
    validate_profiles()
    client_tokens()
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
        missile = ROOT / "models/item" / f"{profile.id}_missile.json"
        label_uvs(missile)
        flight_axis(missile)
        label_is_clear(missile)
        explosive = ROOT / "models/block" / f"{profile.id}_explosive.json"
        label_uvs(explosive)
        label_is_clear(explosive)
        cart = ROOT / "models/item" / f"{profile.id}_bomb_cart.json"
        label_uvs(cart)
        label_is_clear(cart)
        for suffix in ("_grenade", "_bomblet"):
            thrown = ROOT / "models/item" / f"{profile.id}{suffix}.json"
            if (profile.id in GRENADE_IDS) != thrown.exists():
                fail(profile.id + suffix + " does not match the grenade set")
            if thrown.exists():
                label_uvs(thrown)
                label_is_clear(thrown)
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
    print(f"PASS: {len(PROFILES)} payload visuals, flight axis, and client token parity.")


def png_size(path):
    data = path.read_bytes()
    return struct.unpack(">II", data[16:24])


if __name__ == "__main__":
    main()
