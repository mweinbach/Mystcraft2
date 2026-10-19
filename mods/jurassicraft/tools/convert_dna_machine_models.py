#!/usr/bin/env python3
"""Convert upstream Tabula block geometry and cube UVs into Forge OBJ models.

The transformation order and per-face UV mapping match the 1.12.2
JurassicraftVanillaTabulaModel baker. The OBJ loader bakes these static faces;
the original extractor and sequencer item overlays are rendered separately.
"""
from pathlib import Path
from zipfile import ZipFile
import json
import math
import shutil

root = Path(__file__).resolve().parents[1]
upstream = root / "upstream/src/main/resources/assets/jurassicraft"
assets = root / "src/main/resources/assets/jurassicraft"

MODELS = {
    "dna_extractor": ("dna_extractor", "dna_extractor"),
    "dna_sequencer": ("dna_sequencer", "dna_sequencer"),
    "dna_combinator_hybridizer": ("dna_combinator", "dna_combinator_hybridizer"),
    "dna_synthesizer": ("dna_synthesizer", "dna_synthesizer"),
    "embryonic_machine": ("embryonic_machine", "embryonic_machine"),
    "embryo_calcification_machine": ("embryo_calcification_machine", "embryo_calcification_machine"),
    "incubator": ("incubator", "incubator"),
    "cleaning_station": ("cleaning_station", "cleaning_station"),
    "feeder": ("feeder", "feeder"),
}

def multiply(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]

def identity():
    return [[float(i == j) for j in range(4)] for i in range(4)]

def translate(x, y, z):
    matrix = identity()
    matrix[0][3], matrix[1][3], matrix[2][3] = x, y, z
    return matrix

def scale(x, y, z):
    matrix = identity()
    matrix[0][0], matrix[1][1], matrix[2][2] = x, y, z
    return matrix

def rotate(axis, degrees):
    angle = math.radians(degrees)
    c, s = math.cos(angle), math.sin(angle)
    matrix = identity()
    a, b = ((1, 2), (2, 0), (0, 1))[axis]
    matrix[a][a], matrix[a][b], matrix[b][a], matrix[b][b] = c, -s, s, c
    return matrix

def point(matrix, point):
    return tuple(sum(matrix[i][j] * (point[j] if j < 3 else 1) for j in range(4)) for i in range(3))

def convert(name, tbl_name, texture_name):
    with ZipFile(upstream / "models/block" / (tbl_name + ".tbl")) as archive:
        model = json.loads(archive.read("model.json"))
    width, height = model["textureWidth"], model["textureHeight"]
    destination = assets / "models/block"
    destination.mkdir(parents=True, exist_ok=True)
    image = assets / "textures/blocks" / (texture_name + ".png")
    image.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(upstream / "textures/blocks" / (texture_name + ".png"), image)
    (destination / (name + ".mtl")).write_text(
        "newmtl machine\nKd 1 1 1\nmap_Kd jurassicraft:blocks/" + texture_name + "\n", encoding="utf8")
    # These settings avoid culling parts against neighboring full blocks and retain alpha.
    block_model = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "jurassicraft:models/block/" + name + ".obj",
                   "automatic_culling": False, "emissive_ambient": False,
                   "render_type": "minecraft:translucent" if name == "dna_synthesizer" else "minecraft:cutout",
                   "textures": {"particle": "minecraft:block/iron_block"}}
    (destination / (name + ".json")).write_text(json.dumps(block_model, indent=2) + "\n", encoding="utf8")
    lines = ["# Converted from JurassiCraft Tabula model: " + tbl_name,
             "mtllib jurassicraft:models/block/" + name + ".mtl", "o machine", "usemtl machine"]
    vertex_count = 0
    # The feeder was rendered by a tile renderer whose X axis was not mirrored.
    feeder = name == "feeder"
    initial = multiply(translate(.5, 1.5, .5), scale((1 if feeder else -1) / 16, -1 / 16, 1 / 16))

    def visit(cube, parent):
        nonlocal vertex_count
        m = multiply(parent, translate(*cube["position"]))
        m = multiply(m, scale(*cube["scale"]))
        for axis in (2, 1, 0):
            m = multiply(m, rotate(axis, cube["rotation"][axis]))
        x, y, z = cube["offset"]
        w, h, d = cube["dimensions"]
        inflate = cube.get("mcScale", 0.0)
        x0, y0, z0 = x - inflate, y - inflate, z - inflate
        x1, y1, z1 = x + w + inflate, y + h + inflate, z + d + inflate
        mirrored = cube.get("txMirror", False)
        if mirrored: x0, x1 = x1, x0
        local = [(x0,y0,z0), (x1,y0,z0), (x1,y1,z0), (x0,y1,z0),
                 (x0,y0,z1), (x1,y0,z1), (x1,y1,z1), (x0,y1,z1)]
        vertices = [point(m, p) for p in local]
        u, v = cube["txOffset"]
        # Indices and UV corners mirror the original model baker's buildQuad calls.
        faces = [((5,1,2,6), (u+d+w,v+d), (u+d+w+d,v+d+h)),
                 ((0,4,7,3), (u,v+d), (u+d,v+d+h)),
                 ((5,4,0,1), (u+d,v), (u+d+w,v+d)),
                 ((2,3,7,6), (u+d+w,v+d), (u+d+w+w,v)),
                 ((1,0,3,2), (u+d,v+d), (u+d+w,v+d+h)),
                 ((4,5,6,7), (u+d+w+d,v+d), (u+d+w+d+w,v+d+h))]
        # Feeder lids are rendered separately so they can rotate when the feeder serves food.
        if not cube.get("hidden", False) and not (feeder and cube["name"] in ("Lid 1", "Lid 2")):
            for indices, (u0,v0), (u1,v1) in faces:
                corners = [vertices[i] for i in indices]
                # Degenerate faces are skipped by the upstream baker too.
                if any(all(abs(a-b) < 1e-5 for a,b in zip(corners[i], corners[j]))
                       for i in range(4) for j in range(i+1, 4)):
                    continue
                uvs = [(u1,v0), (u0,v0), (u0,v1), (u1,v1)]
                if mirrored != feeder:
                    corners.reverse()
                    uvs.reverse()
                for p in corners: lines.append("v %.8f %.8f %.8f" % p)
                for uu,vv in uvs: lines.append("vt %.8f %.8f" % (uu / width, vv / height))
                lines.append("f " + " ".join(f"{vertex_count+i}/{vertex_count+i}" for i in range(1,5)))
                vertex_count += 4
        for child in cube.get("children", []): visit(child, m)

    for cube in model["cubes"]: visit(cube, initial)
    (destination / (name + ".obj")).write_text("\n".join(lines) + "\n", encoding="utf8")
    if not vertex_count: raise ValueError("Empty Tabula model " + tbl_name)
    return vertex_count // 4

def main():
    for name, (tbl, texture) in MODELS.items():
        print(name, convert(name, tbl, texture), "faces")

if __name__ == "__main__":
    main()
