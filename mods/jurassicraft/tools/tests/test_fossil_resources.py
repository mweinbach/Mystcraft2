"""Regression checks for the port's upstream fossil data and resource links.

These tests use the preserved 1.12.2 source as the independent baseline and do
not run the generator. They validate resource links but are not a substitute
for loading the resulting data packs and exercising gameplay in Minecraft.
"""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/jurassicraft"
DATA = RESOURCES / "data/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def upstream_species():
    handler = (UPSTREAM / "java/org/jurassicraft/server/entity/EntityHandler.java").read_text(encoding="utf-8")
    # Ignore commented-out registrations such as the unreleased Stegosaurus.
    registrations = re.findall(r"^\s*registerDinosaur\((\d+),\s*([A-Z]+)\);", handler, re.MULTILINE)
    classes = dict(re.findall(
        r"^\s*public static final Dinosaur\s+([A-Z]+)\s*=\s*new\s+(\w+Dinosaur)\(\);",
        handler, re.MULTILINE,
    ))
    result = {}
    for legacy_id, name in registrations:
        source = (UPSTREAM / f"java/org/jurassicraft/server/dinosaur/{classes[name]}.java").read_text(encoding="utf-8")
        period = re.search(r"\.setTimePeriod\(TimePeriod\.([A-Z]+)\)", source)
        bones = re.search(r"\.setBones\((.*?)\)", source, re.DOTALL)
        if not period or not bones:
            raise ValueError(f"Cannot read upstream period or bones for {name}")
        result[name.lower()] = (int(legacy_id), period.group(1).lower(), set(re.findall(r'"([a-z_]+)"', bones.group(1))))
    if not result:
        raise ValueError("No active upstream species found")
    return result


def port_species():
    source = (ROOT / "src/main/java/ports/jurassicraft/content/Species.java").read_text(encoding="utf-8")
    constants = source.split("private final int legacyId", 1)[0]
    entries = re.findall(r'\b([A-Z]+)\s*\(\s*(\d+)\s*,\s*"([a-z]+)"\s*,(.*?)\)\s*[,;]', constants, re.DOTALL)
    result = {}
    for name, legacy_id, period, bones in entries:
        result[name.lower()] = (int(legacy_id), period, set(re.findall(r'"([a-z_]+)"', bones)))
    if not result:
        raise ValueError("No port species found")
    return result


class FossilResourcesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upstream = upstream_species()
        cls.species = port_species()

    def test_legacy_ids_periods_and_bones_match_upstream(self):
        self.assertEqual(set(self.upstream), set(self.species))
        for name, expected in self.upstream.items():
            with self.subTest(species=name):
                self.assertEqual(expected, self.species[name])

    def test_species_assets_resolve_to_real_models_and_textures(self):
        translations = read_json(ASSETS / "lang/en_us.json")
        for name, (_, _, bones) in self.upstream.items():
            for block in (f"{name}_fossil_block", f"{name}_encased_fossil"):
                with self.subTest(block=block):
                    self.assertIn(f"block.jurassicraft.{block}", translations)
                    state = read_json(ASSETS / f"blockstates/{block}.json")
                    variants = state["variants"][""]
                    for variant in variants if isinstance(variants, list) else [variants]:
                        self.assert_model_exists(variant["model"])
                    self.assert_model_exists(f"jurassicraft:item/{block}")
            for bone in bones:
                with self.subTest(species=name, bone=bone):
                    item = f"{name}_fossil_{bone}"
                    self.assertIn(f"item.jurassicraft.{item}", translations)
                    self.assert_model_exists(f"jurassicraft:item/{item}")
                    texture = ASSETS / f"textures/items/bones/{name}_{bone}.png"
                    original = UPSTREAM / f"resources/assets/jurassicraft/textures/items/bones/{name}_{bone}.png"
                    self.assertEqual(original.read_bytes(), texture.read_bytes())

    def assert_model_exists(self, location):
        namespace, path = location.split(":", 1)
        if namespace != "jurassicraft":
            return
        model = read_json(ASSETS / f"models/{path}.json")
        if "parent" in model:
            self.assert_model_exists(model["parent"])
        for texture in model.get("textures", {}).values():
            if texture.startswith("jurassicraft:"):
                self.assertTrue((ASSETS / f"textures/{texture.split(':', 1)[1]}.png").is_file(), texture)

    def test_species_worldgen_and_mining_resources_are_connected(self):
        modifier = read_json(DATA / "forge/biome_modifier/ores.json")
        self.assertEqual(modifier["type"], "forge:add_features")
        self.assertEqual(modifier["biomes"], "#minecraft:is_overworld")
        self.assertEqual(modifier["step"], "underground_ores")
        pickaxe = set(read_json(RESOURCES / "data/minecraft/tags/blocks/mineable/pickaxe.json")["values"])
        stone_tier = set(read_json(RESOURCES / "data/minecraft/tags/blocks/needs_stone_tool.json")["values"])
        for name in self.upstream:
            fossil = f"{name}_fossil_block"
            encased = f"{name}_encased_fossil"
            with self.subTest(species=name):
                self.assertIn(f"jurassicraft:{fossil}", modifier["features"])
                placed = read_json(DATA / f"worldgen/placed_feature/{fossil}.json")
                self.assertEqual(placed["feature"], f"jurassicraft:{fossil}")
                placement_types = {entry["type"] for entry in placed["placement"]}
                self.assertTrue({"minecraft:in_square", "minecraft:height_range", "minecraft:biome"} <= placement_types)
                configured = read_json(DATA / f"worldgen/configured_feature/{fossil}.json")
                self.assertEqual(configured["type"], "minecraft:ore")
                self.assertTrue(any(target["state"]["Name"] == f"jurassicraft:{fossil}"
                                    for target in configured["config"]["targets"]))
                for block in (fossil, encased):
                    self.assertIn(f"jurassicraft:{block}", pickaxe)
                    self.assertIn(f"jurassicraft:{block}", stone_tier)
                loot = read_json(DATA / f"loot_tables/blocks/{encased}.json")
                self.assertEqual(loot["type"], "minecraft:block")
                self.assertIn(f"jurassicraft:{encased}", json.dumps(loot))


class FossilLootTests(unittest.TestCase):
    def test_silk_and_fortune_entries_are_connected(self):
        # Actual no-Fortune and Fortune distributions are checked by the
        # in-game FossilLootGameTests against the loaded loot tables.
        for name, kind in (("amber_ore", "amber"), ("gypsum_stone", "gypsum"),
                           ("ice_shard", "ice"), ("plant_fossil_block", "plant")):
            with self.subTest(block=name):
                loot = read_json(DATA / f"loot_tables/blocks/{name}.json")
                self.assertEqual(loot["type"], "minecraft:block")
                self.assertEqual(len(loot["pools"]), 1)
                pool = loot["pools"][0]
                self.assertEqual(pool["rolls"], 1)
                choices = pool["entries"][0]["children"]
                self.assertEqual(choices[0]["name"], f"jurassicraft:{name}")
                self.assertEqual(choices[0]["conditions"][0]["condition"], "minecraft:match_tool")
                self.assertEqual(choices[1]["children"], [{"type": "jurassicraft:fossil_drops", "kind": kind}])


if __name__ == "__main__":
    unittest.main()
