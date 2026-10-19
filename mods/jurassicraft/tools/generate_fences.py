#!/usr/bin/env python3
"""Convert the enabled electric-fence parts and their upstream recipes."""
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
UP = ROOT / "upstream/src/main/resources/assets/jurassicraft"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
DATA = RES / "data/jurassicraft"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def copy(kind, name):
    path = ASSETS / f"textures/{kind}/{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP / f"textures/{kind}/{name}.png", path)


def convert(model, texture, destination, emissive_models=()):
    def load(name):
        with zipfile.ZipFile(UP / f"models/block/{name}.tbl") as archive:
            return json.loads(archive.read("model.json"))
    models = [load(name) for name in (model, *emissive_models)]
    tabula = models[0]
    elements = []
    tw, th = tabula["textureWidth"], tabula["textureHeight"]

    def face(u, v, w, h, emissive):
        result = {"uv": [round(u / tw * 16, 4), round(v / th * 16, 4),
                         round((u + w) / tw * 16, 4), round((v + h) / th * 16, 4)], "texture": "#fence"}
        if emissive:
            # Forge encodes full-bright lighting and disables corner darkening
            # per face, leaving the surrounding pole lit normally.
            result.update(emissivity=15, ambientocclusion=False)
        return result

    def visit(cube, emissive, parent=(0, 0, 0)):
        # Arbitrarily rotated Tabula parts cannot be represented by vanilla block
        # elements. Preserve their unrotated base geometry until a renderer exists.
        if any(cube["rotation"]):
            return
        pos = [parent[i] + cube["position"][i] for i in range(3)]
        start = [pos[i] + cube["offset"][i] for i in range(3)]
        w, h, d = cube["dimensions"]
        if w and h and d:
            u, v = cube["txOffset"]
            elements.append({"from": [start[0]+8, 24-start[1]-h, start[2]+8],
                "to": [start[0]+w+8, 24-start[1], start[2]+d+8], "faces": {
                    "east": face(u, v+d, d, h, emissive), "north": face(u+d, v+d, w, h, emissive),
                    "west": face(u+d+w, v+d, d, h, emissive), "south": face(u+2*d+w, v+d, w, h, emissive),
                    "up": face(u+d, v, w, d, emissive), "down": face(u+d+w, v, w, d, emissive)}})
        for child in cube.get("children", []):
            visit(child, emissive, pos)
    for index, part in enumerate(models):
        for cube in part["cubes"]:
            visit(cube, emissive=index > 0)
    write(ASSETS / f"models/block/{destination}.json", {"render_type": "minecraft:cutout",
        "textures": {"particle": f"jurassicraft:blocks/{texture}", "fence": f"jurassicraft:blocks/{texture}"},
        "elements": elements})


convert("low_security_fence_base", "low_security_fence_base", "low_security_fence_base")
convert("low_security_fence_pole_inactive", "low_security_fence_pole", "low_security_fence_pole")
convert("low_security_fence_pole_active", "low_security_fence_pole", "low_security_fence_pole_active", ("low_security_fence_pole_lights",))
convert("low_security_fence_pole_wires", "low_security_fence_pole", "low_security_fence_pole_arm")
convert("low_security_fence_wire", "low_security_fence_wire", "low_security_fence_wire_arm")
write(ASSETS / "models/block/low_security_fence_wire_center.json", {
    "render_type": "minecraft:cutout", "textures": {"particle": "jurassicraft:blocks/low_security_fence_wire", "fence": "jurassicraft:blocks/low_security_fence_wire"},
    "elements": [{"from": [7.5, 3.5, 7.5], "to": [8.5, 4.5, 8.5], "faces": {direction: {"texture": "#fence"} for direction in ("north", "south", "east", "west", "up", "down")}},
                 {"from": [7.5, 11.5, 7.5], "to": [8.5, 12.5, 8.5], "faces": {direction: {"texture": "#fence"} for direction in ("north", "south", "east", "west", "up", "down")}}]})
write(ASSETS / "models/block/low_security_fence_wire_diagonal.json", {
    "render_type": "minecraft:cutout", "textures": {"particle": "jurassicraft:blocks/low_security_fence_wire", "fence": "jurassicraft:blocks/low_security_fence_wire"},
    "elements": [{"from": [-14.627, y - .5, 7.5], "to": [8, y + .5, 8.5],
                  "rotation": {"origin": [8, y, 8], "axis": "z", "angle": -45},
                  "faces": {direction: {"texture": "#fence"} for direction in ("north", "south", "east", "west", "up", "down")}}
                 for y in (4, 12)]})
write(ASSETS / "blockstates/low_security_fence_wire.json", {"multipart": [
    {"apply": {"model": "jurassicraft:block/low_security_fence_wire_center"}},
    *[{"when": {direction: "true", "up": "down"}, "apply": {"model": "jurassicraft:block/low_security_fence_wire_arm", "y": rotation}}
      for direction, rotation in (("west", 0), ("north", 90), ("east", 180), ("south", 270))],
    *[{"when": {"up": direction}, "apply": {"model": "jurassicraft:block/low_security_fence_wire_diagonal", "y": rotation}}
      for direction, rotation in (("west", 0), ("north", 90), ("east", 180), ("south", 270))]]})
write(ASSETS / "blockstates/low_security_fence_pole.json", {"multipart": [
    {"when": {"active": "false"}, "apply": {"model": "jurassicraft:block/low_security_fence_pole"}},
    {"when": {"active": "true"}, "apply": {"model": "jurassicraft:block/low_security_fence_pole_active"}},
    *[{"when": {direction: "true"}, "apply": {"model": "jurassicraft:block/low_security_fence_pole_arm", "y": rotation}}
      for direction, rotation in (("west", 0), ("north", 90), ("east", 180), ("south", 270))]]})

for part, texture, label in (("base", "fence_base", "Low Security Fence Base"),
                             ("pole", "fence_pole", "Low Security Fence Pole"),
                             ("wire", "low_security_fence_wire", "Low Security Fence Wire")):
    name = f"low_security_fence_{part}"
    copy("blocks", name)
    copy("items", texture)
    write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jurassicraft:items/{texture}"}})
    if part == "base":
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"jurassicraft:block/{name}"}}})
    write(DATA / f"loot_tables/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"jurassicraft:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

write(ROOT / "tools/lang/fences.json", {
    "block.jurassicraft.low_security_fence_base": "Low Security Fence Base",
    "block.jurassicraft.low_security_fence_pole": "Low Security Fence Pole",
    "block.jurassicraft.low_security_fence_wire": "Low Security Fence Wire",
    "death.attack.jurassicraft_shock": "%1$s was electrocuted by a security fence"})
write(ROOT / "tools/tags/fences_pickaxe.json", {"replace": False, "values": [f"jurassicraft:low_security_fence_{part}" for part in ("base", "pole", "wire")]})


def recipe(name, pattern, key, count):
    write(DATA / f"recipes/{name}.json", {"type": "minecraft:crafting_shaped", "pattern": pattern,
        "key": {letter: ({"tag": value[1:]} if value.startswith("#") else {"item": value}) for letter, value in key.items()},
        "result": {"item": f"jurassicraft:{name}", "count": count}})


recipe("low_security_fence_base", ["ABA", "CCC", " D "], {"A": "#forge:nuggets/iron", "B": "minecraft:iron_ingot", "C": "minecraft:stone", "D": "minecraft:redstone"}, 6)
recipe("low_security_fence_pole", ["A", "B", "B"], {"A": "minecraft:redstone", "B": "minecraft:iron_ingot"}, 2)
recipe("low_security_fence_wire", ["AAA", " B ", "AAA"], {"A": "minecraft:iron_ingot", "B": "minecraft:redstone"}, 16)

for i in range(1, 5):
    destination = ASSETS / f"sounds/fence_shock_{i}.ogg"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP / f"sounds/fence_shock_{i}.ogg", destination)
write(ROOT / "tools/sounds/fence.json", {"fence_shock": {"subtitle": "subtitles.jurassicraft.fence_shock",
      "sounds": [f"jurassicraft:fence_shock_{i}" for i in range(1, 5)]}})
write(ROOT / "tools/lang/fence_sounds.json", {"subtitles.jurassicraft.fence_shock": "Fence shocks"})
