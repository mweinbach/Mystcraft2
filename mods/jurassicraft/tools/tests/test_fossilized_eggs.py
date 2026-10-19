"""Verify the three egg variants use the original textures and have names."""

import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main/resources/assets/jurassicraft"


class FossilizedEggAssetsTests(unittest.TestCase):
    def test_all_upstream_egg_variants_have_models_textures_and_names(self):
        names = json.loads((ROOT / "tools/lang/fossilized_eggs.json").read_text(encoding="utf-8"))
        for index in range(1, 4):
            item = "fossilized_egg" if index == 1 else f"fossilized_egg_{index}"
            texture = f"fossilized_egg_{index}"
            with self.subTest(item=item):
                model = json.loads((ASSETS / f"models/item/{item}.json").read_text(encoding="utf-8"))
                self.assertEqual(model["parent"], "minecraft:item/generated")
                self.assertEqual(model["textures"]["layer0"], f"jurassicraft:items/{texture}")
                self.assertEqual((ASSETS / f"textures/items/{texture}.png").read_bytes(),
                                 (UPSTREAM / f"textures/items/{texture}.png").read_bytes())
                self.assertEqual(names[f"item.jurassicraft.{item}"], "Fossilized Egg")


if __name__ == "__main__":
    unittest.main()
