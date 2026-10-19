"""Keep upstream ore-dictionary inputs interoperable with Forge and Fabric convention tags."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/data"
DATA = ROOT / "jurassicraft/recipes"


class ModpackIngredientsTest(unittest.TestCase):
    def test_iron_nuggets_accept_convention_tagged_items(self):
        nuggets = json.loads((ROOT / "jurassicraft/tags/items/iron_nuggets.json").read_text())["values"]
        self.assertIn("minecraft:iron_nugget", nuggets)
        for tag in ("#forge:nuggets/iron", "#c:iron_nuggets"):
            self.assertIn({"id": tag, "required": False}, nuggets)
        for recipe, key in (("storage_disc", "I"), ("embryonic_machine", "A"),
                            ("paddock_sign", "A"), ("bug_crate", "A"),
                            ("bug_crate_red_sandstone", "A")):
            with self.subTest(recipe=recipe):
                data = json.loads((DATA / f"{recipe}.json").read_text())
                self.assertEqual(data["key"][key], {"tag": "jurassicraft:iron_nuggets"})

    def test_wooden_gates_and_fossils_are_exposed_to_modpacks(self):
        trees = ("ginkgo", "calamites", "psaronius", "phoenix", "araucaria")
        for kind in ("blocks", "items"):
            gates = json.loads((ROOT / f"forge/tags/{kind}/fence_gates/wooden.json").read_text())["values"]
            self.assertEqual({f"jurassicraft:{tree}_fence_gate" for tree in trees}, set(gates))
            fossils = json.loads((ROOT / f"forge/tags/{kind}/fossils.json").read_text())["values"]
            self.assertEqual(10, len(fossils))
            self.assertIn("jurassicraft:tyrannosaurus_fossil_block", fossils)
