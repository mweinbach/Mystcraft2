import io
import json
from pathlib import Path
import sys
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from resource_tags import TagIndex


def tag(values, replace=False):
    return {"replace": replace, "values": values}


class TagResolutionTests(unittest.TestCase):
    def index(self, local, base=None):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            for name, content in (base or {}).items():
                archive.writestr(name, json.dumps(content))
        archive = zipfile.ZipFile(data)
        self.addCleanup(archive.close)
        return TagIndex(local, archive)

    def test_additive_nested_members_and_optional_missing_tags(self):
        index = self.index({
            "data/c/tags/items/steel_ingots.json": tag(["other:steel"]),
            "data/icbm_port/tags/items/steel.json": tag([
                "#c:steel_ingots", {"id": "#forge:ingots/steel", "required": False}]),
        }, {"data/c/tags/items/steel_ingots.json": tag(["icbm_port:steel_ingot"])})
        self.assertEqual(index.resolve("icbm_port:steel"), {"icbm_port:steel_ingot", "other:steel"})

    def test_replace_discards_lower_pack_members(self):
        path = "data/c/tags/items/steel_ingots.json"
        index = self.index({path: tag(["other:steel"], True)}, {path: tag(["icbm_port:steel_ingot"])})
        self.assertEqual(index.resolve("c:steel_ingots"), {"other:steel"})

    def test_required_missing_reference_is_an_error(self):
        index = self.index({"data/c/tags/items/steel.json": tag(["#c:missing"])})
        with self.assertRaisesRegex(ValueError, "Missing item tag"):
            index.resolve("c:steel")

    def test_optional_cycle_is_still_an_error(self):
        index = self.index({
            "data/c/tags/items/a.json": tag([{ "id": "#c:b", "required": False}]),
            "data/c/tags/items/b.json": tag(["#c:a"]),
        })
        with self.assertRaisesRegex(ValueError, "Cyclic item tags"):
            index.resolve("c:a")

    def test_empty_optional_material_switches_fallback_on_reload(self):
        path = "data/c/tags/items/uranium_ingots.json"
        condition = {"condition": "fabric:tags_populated", "values": ["c:uranium_ingots"]}
        fallback = {"fabric:load_conditions": [{"condition": "fabric:not", "value": condition}]}
        base = self.index({path: tag([])})
        provider = self.index({path: tag(["other:uranium"])})
        self.assertFalse(base.condition(condition))
        self.assertTrue(base.enabled(fallback))
        self.assertTrue(provider.condition(condition))
        self.assertFalse(provider.enabled(fallback))

    def test_mekanism_alternatives_swap_with_vanilla_fallbacks(self):
        recipes = Path(__file__).resolve().parents[2] / "src/main/resources/data/icbm_port/recipes"
        uranium = "data/icbm_port/tags/items/materials/uranium_ingots.json"
        pellets = "data/icbm_port/tags/items/materials/uranium_235_pellets.json"
        local = {uranium: tag([]), pellets: tag([])}
        standalone = TagIndex(local, self.index({}).vanilla)
        with_mekanism = TagIndex(local, self.index({}).vanilla, loaded_mods={"mekanism"})
        for payload in ("nuclear", "antimatter", "redmatter"):
            gated = json.loads((recipes / f"{payload}_explosive_mekanism.json").read_text())
            fallback = json.loads((recipes / f"{payload}_explosive.json").read_text())
            self.assertIn("mekanism:alloy_atomic", json.dumps(gated["key"]))
            self.assertFalse(standalone.enabled(gated), payload)
            self.assertTrue(standalone.enabled(fallback), payload)
            self.assertTrue(with_mekanism.enabled(gated), payload)
            self.assertFalse(with_mekanism.enabled(fallback), payload)
        # A real uranium provider keeps selecting the uranium recipe even with Mekanism present.
        provider = TagIndex({uranium: tag(["other:uranium"]), pellets: tag([])},
                            self.index({}).vanilla, loaded_mods={"mekanism"})
        nuclear = json.loads((recipes / "nuclear_explosive_mekanism.json").read_text())
        self.assertFalse(provider.enabled(nuclear))


if __name__ == "__main__":
    unittest.main()
