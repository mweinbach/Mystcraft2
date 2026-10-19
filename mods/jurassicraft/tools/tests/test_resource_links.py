"""Find broken cross-references in all locally packaged models and worldgen."""

import json
import math
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
DATA = RES / "data/jurassicraft"


def local_path(location):
    return location.split(":", 1)[1] if isinstance(location, str) and location.startswith("jurassicraft:") else None


def descend(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from descend(child)
    elif isinstance(value, list):
        for child in value:
            yield from descend(child)


class ResourceLinkTests(unittest.TestCase):
    def test_json_and_local_model_texture_references(self):
        errors = []
        for path in RES.rglob("*.json"):
            try:
                document = json.loads(path.read_text(encoding="utf-8"))
            except (ValueError, UnicodeError) as exc:
                errors.append(f"{path.relative_to(RES)}: invalid JSON: {exc}")
                continue
            if ASSETS not in path.parents:
                continue
            for node in descend(document):
                for key in ("model", "parent"):
                    location = local_path(node.get(key))
                    if location and key == "model" and node.get("loader") == "forge:obj":
                        target = ASSETS / location
                    else:
                        target = ASSETS / f"models/{location}.json"
                    if location and not target.is_file():
                        errors.append(f"{path.relative_to(RES)}: missing model {node[key]}")
                textures = node.get("textures")
                if isinstance(textures, dict):
                    for value in textures.values():
                        location = local_path(value)
                        if location and not (ASSETS / f"textures/{location}.png").is_file():
                            errors.append(f"{path.relative_to(RES)}: missing texture {value}")
        self.assertFalse(errors, "\n".join(errors))

    def test_forge_obj_geometry_materials_and_textures(self):
        errors = []
        for model_file in (ASSETS / "models").rglob("*.json"):
            model = json.loads(model_file.read_text(encoding="utf-8"))
            if model.get("loader") != "forge:obj":
                continue
            location = local_path(model.get("model"))
            if not location:
                continue
            obj_file = ASSETS / location
            if not obj_file.is_file():
                errors.append(f"{model_file.name}: missing OBJ {location}")
                continue
            vertices, uvs, faces, used, materials = [], [], [], set(), set()
            for line in obj_file.read_text(encoding="utf-8").splitlines():
                fields = line.split()
                if not fields or fields[0].startswith("#"):
                    continue
                if fields[0] in ("v", "vt"):
                    try:
                        values = [float(n) for n in fields[1:]]
                        if len(values) < (3 if fields[0] == "v" else 2) or not all(map(math.isfinite, values)):
                            raise ValueError()
                        (vertices if fields[0] == "v" else uvs).append(values)
                    except ValueError:
                        errors.append(f"{obj_file.name}: invalid vertex/UV {line}")
                elif fields[0] == "f":
                    faces.append(fields[1:])
                elif fields[0] == "usemtl" and len(fields) == 2:
                    used.add(fields[1])
                elif fields[0] == "mtllib" and len(fields) == 2:
                    name = local_path(fields[1])
                    material_file = ASSETS / name if name else obj_file.parent / fields[1]
                    if not material_file.is_file():
                        errors.append(f"{obj_file.name}: missing material library {fields[1]}")
                        continue
                    for material_line in material_file.read_text(encoding="utf-8").splitlines():
                        tokens = material_line.split()
                        if len(tokens) == 2 and tokens[0] == "newmtl":
                            materials.add(tokens[1])
                        if len(tokens) >= 2 and tokens[0] == "map_Kd":
                            texture = local_path(tokens[-1])
                            if texture and not (ASSETS / f"textures/{texture}.png").is_file():
                                errors.append(f"{material_file.name}: missing texture {tokens[-1]}")
            if not vertices or not uvs or not faces:
                errors.append(f"{obj_file.name}: missing geometry")
            if not used or not used <= materials:
                errors.append(f"{obj_file.name}: missing used materials {used - materials}")
            for face in faces:
                if len(face) not in (3, 4):
                    errors.append(f"{obj_file.name}: OBJ face must have 3 or 4 vertices")
                    continue
                for pair in face:
                    try:
                        vertex, uv = (int(n) for n in pair.split("/")[:2])
                        if not 1 <= vertex <= len(vertices) or not 1 <= uv <= len(uvs):
                            raise ValueError()
                    except (ValueError, TypeError):
                        errors.append(f"{obj_file.name}: invalid face index {pair}")
        self.assertFalse(errors, "\n".join(errors))

    def test_placed_features_and_biome_modifiers_reference_existing_data(self):
        errors = []
        placed_root = DATA / "worldgen/placed_feature"
        configured_root = DATA / "worldgen/configured_feature"
        for path in placed_root.rglob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            location = local_path(data.get("feature"))
            if location and not (configured_root / f"{location}.json").is_file():
                errors.append(f"{path.relative_to(RES)}: missing configured feature {data['feature']}")
        for path in (DATA / "forge/biome_modifier").rglob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            features = data.get("features", [])
            if isinstance(features, str):
                features = [features]
            for feature in features:
                location = local_path(feature)
                if location and not (placed_root / f"{location}.json").is_file():
                    errors.append(f"{path.relative_to(RES)}: missing placed feature {feature}")
        self.assertFalse(errors, "\n".join(errors))

    def test_recipe_and_loot_items_and_worldgen_blocks_have_client_resources(self):
        errors = []

        def check(source, location, subdirectory):
            name = local_path(location)
            if name and not (ASSETS / subdirectory / f"{name}.json").is_file():
                errors.append(f"{source.relative_to(RES)}: {location} has no {subdirectory} resource")

        for path in (DATA / "recipes").rglob("*.json"):
            for node in descend(json.loads(path.read_text(encoding="utf-8"))):
                for key in ("item", "result"):
                    check(path, node.get(key), "models/item")
        for path in (DATA / "loot_tables").rglob("*.json"):
            for node in descend(json.loads(path.read_text(encoding="utf-8"))):
                if node.get("type") in ("item", "minecraft:item"):
                    check(path, node.get("name"), "models/item")
        for path in (DATA / "worldgen/configured_feature").rglob("*.json"):
            for node in descend(json.loads(path.read_text(encoding="utf-8"))):
                for key in ("Name", "block"):
                    check(path, node.get(key), "blockstates")
        self.assertFalse(errors, "\n".join(errors))

    def test_crafting_recipes_fit_the_1192_crafting_grid(self):
        for path in (DATA / "recipes").rglob("*.json"):
            recipe = json.loads(path.read_text(encoding="utf-8"))
            with self.subTest(recipe=path.name):
                if recipe.get("type") == "minecraft:crafting_shaped":
                    pattern = recipe["pattern"]
                    self.assertTrue(1 <= len(pattern) <= 3)
                    self.assertTrue(all(isinstance(row, str) and 1 <= len(row) <= 3 for row in pattern))
                    self.assertEqual(len({len(row) for row in pattern}), 1)
                    used = set("".join(pattern)) - {" "}
                    self.assertTrue(used)
                    self.assertEqual(set(recipe["key"]), used)
                    self.assertTrue(all(len(key) == 1 and key != " " for key in recipe["key"]))
                elif recipe.get("type") == "minecraft:crafting_shapeless":
                    self.assertTrue(1 <= len(recipe["ingredients"]) <= 9)
                if recipe.get("type", "").startswith("minecraft:crafting_"):
                    result = recipe["result"]
                    self.assertTrue(isinstance(result["item"], str))
                    self.assertTrue(1 <= result.get("count", 1) <= 64)


if __name__ == "__main__":
    unittest.main()
