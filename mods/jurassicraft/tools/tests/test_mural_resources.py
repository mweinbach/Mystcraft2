import json
import struct
import unittest
from pathlib import Path


MOD = Path(__file__).resolve().parents[2]
ASSETS = MOD / "src/main/resources/assets/jurassicraft"
UPSTREAM = MOD / "upstream/src/main/resources/assets/jurassicraft"
SIZES = {
    "creation_lab": (64, 32), "fossils": (128, 64), "hunt_left": (64, 64),
    "hunt_middle": (64, 64), "hunt_right": (64, 64), "mosasaurus": (64, 32),
    "park_entrance": (64, 32), "riding": (64, 32), "sketch": (64, 32),
    "triceratops": (128, 32), "valley": (64, 32),
}


class MuralResourcesTest(unittest.TestCase):
    def test_original_images_and_sizes(self):
        for name, dimensions in SIZES.items():
            with self.subTest(name=name):
                relative = Path("textures/murals") / (name + ".png")
                data = (ASSETS / relative).read_bytes()
                self.assertEqual(data, (UPSTREAM / relative).read_bytes())
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), dimensions)

    def test_original_recipe(self):
        recipe = json.loads((MOD / "src/main/resources/data/jurassicraft/recipes/mural.json").read_text())
        self.assertEqual(recipe["pattern"], ["ABA", "BCB", "ABA"])
        self.assertEqual({key: entry["item"] for key, entry in recipe["key"].items()},
                         {"A": "minecraft:iron_ingot", "B": "minecraft:white_wool", "C": "minecraft:painting"})
        self.assertEqual(recipe["result"]["item"], "jurassicraft:mural")


if __name__ == "__main__":
    unittest.main()
