"""Checks the isolated fossil nest resources and their upstream artwork."""

import json
import unittest

from layout import placements
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
DATA = RES / "data/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main/resources/assets/jurassicraft"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


class FossilNestResourcesTests(unittest.TestCase):
    def test_nests_and_trackways_have_linked_models_artwork_and_drops(self):
        labels = load(ROOT / "tools/lang/fossil_nests.json")
        pickaxe = set(load(ROOT / "tools/tags/fossil_nests_pickaxe.json")["values"])
        stone = set(load(ROOT / "tools/tags/fossil_nests_needs_stone_tool.json")["values"])
        for index in range(1, 4):
            raw = "nest_fossil" if index == 1 else f"nest_fossil_{index}"
            encased = "encased_nest_fossil" if index == 1 else f"encased_nest_fossil_{index}"
            for block in (raw, encased):
                with self.subTest(block=block):
                    self.assertIn(f"block.jurassicraft.{block}", labels)
                    self.assertIn(f"jurassicraft:{block}", pickaxe)
                    self.assertIn(f"jurassicraft:{block}", stone)
                    state = load(ASSETS / f"blockstates/{block}.json")
                    model_id = state["variants"][""]["model"]
                    self.assert_model(model_id)
                    self.assert_model(f"jurassicraft:item/{block}")
                    loot = load(DATA / f"loot_tables/blocks/{block}.json")
                    self.assertEqual(loot["pools"][0]["entries"][0]["name"], f"jurassicraft:{block}")
                    if block == raw:
                        self.assertEqual(loot["pools"][0]["conditions"][0]["condition"], "minecraft:match_tool")
            texture = f"nest_fossil_top_{index}"
            self.assertEqual((ASSETS / f"textures/blocks/{texture}.png").read_bytes(),
                             (UPSTREAM / f"textures/blocks/{texture}.png").read_bytes())
        for suffix in ("biped_medium", "biped_small", "raptor"):
            block = f"fossilized_trackway_{suffix}"
            with self.subTest(block=block):
                self.assertIn(f"block.jurassicraft.{block}", labels)
                self.assertIn(f"jurassicraft:{block}", pickaxe)
                variants = load(ASSETS / f"blockstates/{block}.json")["variants"]
                self.assertEqual(set(variants), {f"facing={direction}" for direction in ("north", "south", "east", "west")})
                for variant in variants.values():
                    self.assert_model(variant["model"])
                self.assert_model(f"jurassicraft:item/{block}")
                self.assertEqual((ASSETS / f"textures/blocks/{block}.png").read_bytes(),
                                 (UPSTREAM / f"textures/blocks/{block}.png").read_bytes())

    def assert_model(self, location):
        namespace, path = location.split(":", 1)
        if namespace != "jurassicraft":
            return
        model = load(ASSETS / f"models/{path}.json")
        if "parent" in model:
            self.assert_model(model["parent"])
        for texture in model.get("textures", {}).values():
            if texture.startswith("jurassicraft:"):
                self.assertTrue((ASSETS / f"textures/{texture.split(':', 1)[1]}.png").is_file(), texture)

    def test_nest_worldgen_resources_connect_to_custom_feature(self):
        configured = load(DATA / "worldgen/configured_feature/fossil_nest.json")
        self.assertEqual(configured, {"type": "jurassicraft:fossil_nest", "config": {}})
        placed = load(DATA / "worldgen/placed_feature/fossil_nest.json")
        self.assertEqual(placed["feature"], "jurassicraft:fossil_nest")
        self.assertIn("minecraft:biome", {entry["type"] for entry in placed["placement"]})
        height = next(entry["height"] for entry in placed["placement"] if entry["type"] == "minecraft:height_range")
        self.assertEqual(height["min_inclusive"], {"absolute": 30})
        self.assertEqual(height["max_inclusive"], {"absolute": 49})
        self.assertEqual(("#minecraft:is_overworld", "underground_ores"), placements()["fossil_nest"])
        replaceable = {value if isinstance(value, str) else value["id"]
                       for value in load(DATA / "tags/blocks/fossil_nest_replaceable.json")["values"]}
        self.assertIn("#minecraft:base_stone_overworld", replaceable)
        for variant in ("biped_medium", "biped_small", "raptor"):
            self.assertIn(f"jurassicraft:fossilized_trackway_{variant}", replaceable)


if __name__ == "__main__":
    unittest.main()
