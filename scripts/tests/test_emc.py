from fractions import Fraction
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

spec = importlib.util.spec_from_file_location("emc", Path(__file__).resolve().parents[1] / "emc.py")
emc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(emc)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data) if not isinstance(data, str) else data, encoding="utf-8")


def shaped(pattern, key, result, count=1, **extra):
    return {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"item": result, "count": count}, **extra}


class MapperTests(unittest.TestCase):
    def test_hidden_fraction_keeps_values_below_one_and_floors_the_rest(self):
        self.assertEqual(emc.floor_value(Fraction(3, 8)), Fraction(3, 8))
        self.assertEqual(emc.floor_value(Fraction(5, 2)), 2)

    def test_shaped_recipes_count_every_pattern_cell(self):
        recipe = shaped(["aba", " a "], {"a": {"item": "minecraft:stick"}, "b": {"item": "minecraft:dirt"}}, "x:y")
        ingredients = emc.recipe_ingredients(recipe)
        self.assertEqual(sorted(i["item"] for i in ingredients), ["minecraft:dirt"] + ["minecraft:stick"] * 3)

    def test_paxel_recipes_count_as_shaped(self):
        recipe = shaped(["APS", " R ", " R "],
                        {"A": {"item": "mod:axe"}, "P": {"item": "mod:pickaxe"}, "S": {"item": "mod:shovel"},
                         "R": {"item": "minecraft:stick"}}, "mod:paxel")
        recipe["type"] = "mekanismtools:paxel"
        ingredients = emc.recipe_ingredients(recipe)
        self.assertEqual(sorted(i["item"] for i in ingredients),
                         ["minecraft:stick", "minecraft:stick", "mod:axe", "mod:pickaxe", "mod:shovel"])

    def test_unknown_recipe_types_are_not_mapped(self):
        self.assertIsNone(emc.recipe_ingredients({"type": "mod:machine", "input": {"item": "a:b"}}))

    def test_resolution_takes_the_cheapest_recipe_and_group_member(self):
        group = frozenset({"a:cheap", "a:dear"})
        conversions = [("r1", "a:out", 1, {group: 2}), ("r2", "a:out", 1, {"a:dear": 1})]
        values = emc.resolve(conversions, {"a:cheap": Fraction(3), "a:dear": Fraction(10)}, set(), {})
        self.assertEqual(values["a:out"], 6)

    def test_fixed_and_blocked_outputs_are_never_derived(self):
        conversions = [("r", "a:fixed", 1, {"a:in": 1}), ("r", "a:blocked", 1, {"a:in": 1})]
        values = emc.resolve(conversions, {"a:in": Fraction(1), "a:fixed": Fraction(50)}, {"a:blocked"}, {})
        self.assertEqual(values["a:fixed"], 50)
        self.assertNotIn("a:blocked", values)

    def test_container_remainders_are_subtracted(self):
        conversions = emc.conversions_from(
            {"r": {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:water_bucket"}],
                   "result": {"item": "a:out"}}}, emc.Tags({}), emc.REMAINDERS)
        self.assertEqual(conversions[0][3], {"minecraft:water_bucket": 1, "minecraft:bucket": -1})


class ConditionTests(unittest.TestCase):
    tags = emc.Tags({"c:full": ["a:b"], "c:empty": []})

    def holds(self, condition, loader="fabric"):
        return emc.condition_holds(condition, loader, self.tags, {"minecraft", "fabric-api"})

    def test_fabric_conditions(self):
        populated = {"condition": "fabric:tags_populated", "values": ["c:full"]}
        empty = {"condition": "fabric:tags_populated", "values": ["c:empty"]}
        self.assertTrue(self.holds(populated))
        self.assertFalse(self.holds(empty))
        self.assertTrue(self.holds({"condition": "fabric:not", "value": empty}))
        self.assertTrue(self.holds({"condition": "fabric:or", "values": [empty, populated]}))
        self.assertFalse(self.holds({"condition": "fabric:and", "values": [empty, populated]}))
        self.assertTrue(self.holds({"condition": "fabric:all_mods_loaded", "values": ["fabric-api"]}))

    def test_forge_conditions(self):
        self.assertFalse(self.holds({"type": "forge:mod_loaded", "modid": "jei"}, "forge"))
        self.assertTrue(self.holds({"type": "forge:tag_empty", "tag": "c:empty"}, "forge"))

    def test_unknown_conditions_are_errors(self):
        with self.assertRaises(emc.PolicyError):
            self.holds({"condition": "othermod:something"})

    def test_port_conditions_take_the_policy_outcome(self):
        custom = {"a:config": False}
        condition = {"condition": "fabric:not", "value": {"condition": "a:config", "config_name": "x"}}
        self.assertTrue(emc.condition_holds(condition, "fabric", self.tags, set(), custom))
        self.assertFalse(emc.condition_holds({"type": "a:config"}, "forge", self.tags, set(), custom))
        base = {"namespace": "a", "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}}
        emc.validate_policy({**base, "conditions": {"a:config": {"holds": True, "why": "w"}}})
        for bad in ({"b:config": {"holds": True, "why": "w"}}, {"a:config": {"holds": "yes", "why": "w"}},
                    {"a:config": {"holds": True}}):
            with self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, "conditions": bad})


class PolicyTests(unittest.TestCase):
    def test_exact_ids_beat_patterns_and_ties_are_reported(self):
        problems = []
        owners = emc.assign_patterns({
            "exclude": {"a:x_*": ["a:x_*"]},
            "values": {"a:x_one": ["a:x_one"], "a:*_one": ["a:*_one"]},
        }, {"a:x_one", "a:x_two", "a:y_one"}, problems)
        self.assertEqual(owners["a:x_one"], ("values", "a:x_one"))
        self.assertEqual(owners["a:x_two"], ("exclude", "a:x_*"))
        self.assertEqual(owners["a:y_one"], ("values", "a:*_one"))
        self.assertEqual(problems, [])
        emc.assign_patterns({"exclude": {"k": ["a:*_x"]}, "values": {"v": ["a:x_*"]}}, {"a:x_x"}, problems)
        self.assertEqual(len(problems), 1)

    def test_like_values_scale_the_baseline(self):
        spec = {"like": "minecraft:gunpowder", "times": 3, "why": "w"}
        self.assertEqual(emc.explicit_value(spec, "projecte", {"minecraft:gunpowder": Fraction(192)}, "k"), 576)
        with self.assertRaises(emc.PolicyError):
            emc.explicit_value({"like": "minecraft:nothing"}, "projecte", {}, "k")

    def test_policy_grammar(self):
        base = {"namespace": "t", "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}, "output": "r"}
        emc.validate_policy(base)
        for bad in ({"values": {"t:a": {"emc": 1}}},
                    {"exclude": {"t:a": ""}},
                    {"conversions": [{"name": "c", "why": "w", "output": "t:a", "ingredients": {"t:b": 0}}]},
                    {"conversions": [{"name": "Bad Name", "why": "w", "output": "t:a", "ingredients": {"t:b": 1}}]},
                    {"processes": [{"name": "p", "in": {"t:a": 1}, "out": {"t:b": 1}}]},
                    {"processes": [{"name": "p", "why": "w", "in": {"t:a": 1}, "out": {"t:b": 1}, "systems": ["x"]}]},
                    {"vanilla": {"t:a": {"systems": ["projecte"], "emc": 1, "why": "w"}}},
                    {"vanilla": {"minecraft:dirt": {"emc": 1, "why": "w"}}},
                    {"surprise": 1}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, **bad})

    def test_projecte_grammar_rejects_unknown_items_and_zero_amounts(self):
        known = {"minecraft:dirt"}
        good = {"values": {"before": {"minecraft:dirt": 1}, "conversion": [
            {"output": "minecraft:dirt", "ingredients": [emc.NO_EMC]}]}}
        self.assertEqual(emc.validate_projecte(good, known), [])
        bad = {"groups": {"g": {"conversions": [{"output": "x:unknown", "ingredients": {"minecraft:dirt": 0}}]}}}
        self.assertEqual(len(emc.validate_projecte(bad, known)), 2)


class PortTests(unittest.TestCase):
    """A small port exercising values, exclusions, conversions, processes and the generated files."""

    def setUp(self):
        sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(sandbox.cleanup)
        self.dir = Path(sandbox.name) / "testmod"
        resources = self.dir / "res"
        recipes = resources / "data" / "testmod" / "recipes"
        write(recipes / "widget.json", shaped(["ii", "s "], {"i": {"item": "minecraft:iron_ingot"},
                                                              "s": {"tag": "testmod:rods"}}, "testmod:widget"))
        write(recipes / "pane.json", shaped(["ggg"], {"g": {"item": "minecraft:glass"}}, "testmod:pane", 8))
        write(recipes / "gated.json", shaped(["d"], {"d": {"item": "minecraft:dirt"}}, "testmod:widget",
                                             **{"fabric:load_conditions": [
                                                 {"condition": "fabric:all_mods_loaded", "values": ["absent"]}]}))
        write(recipes / "machine.json", {"type": "testmod:machine"})
        write(resources / "data" / "testmod" / "tags" / "items" / "rods.json", {"values": ["minecraft:stick"]})
        registry = ["testmod:widget", "testmod:pane", "testmod:ore", "testmod:dust",
                    "testmod:a_matter", "testmod:b_matter", "testmod:a_cell", "testmod:b_cell"]
        write(self.dir / "emc" / "registry.txt", "\n".join(registry) + "\n")
        self.policy = {
            "namespace": "testmod",
            "loaders": {"projecte": "fabric", "itemalchemy": "fabric"},
            "resources": {"projecte": ["res"], "itemalchemy": ["res"]},
            "output": "res",
            "values": {"testmod:dust": {"emc": 5, "why": "w"},
                       "testmod:*_matter": {"like": "minecraft:dirt", "times": 4, "why": "w"}},
            "exclude": {"testmod:ore": "ore"},
            "conversions": [{"name": "analyze", "why": "w", "output": "testmod:{kind}_cell",
                             "ingredients": {"testmod:{kind}_matter": 2}}],
            "processes": [{"name": "split", "why": "w", "in": {"testmod:{kind}_cell": 1},
                           "out": [{"testmod:{kind}_matter": 2}, {"testmod:dust": 1}]}],
        }

    def build(self):
        write(self.dir / "emc" / "policy.json", self.policy)
        return emc.build(self.dir)

    def test_values_come_from_recipes_policy_and_conversions(self):
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        self.assertEqual(values["testmod:widget"], {"projecte": 516, "itemalchemy": 508, "source": "recipe"})
        self.assertEqual(values["testmod:dust"]["projecte"], 5)
        self.assertEqual(values["testmod:a_matter"]["projecte"], 4)
        self.assertEqual(values["testmod:b_cell"], {"projecte": 8, "itemalchemy": 8, "source": "conversion analyze"})
        self.assertEqual(values["testmod:ore"], {"excluded": "ore"})
        self.assertEqual(values["testmod:pane"]["projecte"], 0)
        self.assertEqual(values["testmod:pane"]["below_one"]["projecte"], "3/8")

    def test_shipped_files_follow_both_formats(self):
        _port, outputs = self.build()
        data = self.dir / "res" / "data" / "testmod"
        projecte = json.loads(outputs[data / "pe_custom_conversions" / "testmod.json"])
        self.assertEqual(projecte["values"]["before"], {"testmod:dust": 5, "testmod:a_matter": 4,
                                                        "testmod:b_matter": 4})
        self.assertEqual(projecte["values"]["conversion"], [{"output": "testmod:ore", "ingredients": [emc.NO_EMC]}])
        self.assertEqual(len(projecte["groups"]["testmod_analyze"]["conversions"]), 2)
        itemalchemy = json.loads(outputs[data / "default_emcs" / "testmod.json"])
        self.assertEqual(itemalchemy["testmod:ore"], 0)
        self.assertEqual(itemalchemy["testmod:widget"], 508)
        readme = outputs[self.dir / "emc" / "README.md"]
        self.assertIn("`testmod:machine`", readme)
        self.assertIn("| `testmod:ore` | ore |", readme)

    def test_process_gain_is_a_problem(self):
        self.policy["values"]["testmod:dust"]["emc"] = 100
        port, _outputs = self.build()
        self.assertTrue(any("process 'split'" in p for p in port.problems), port.problems)

    def test_unresolved_items_are_problems(self):
        del self.policy["exclude"]
        port, _outputs = self.build()
        self.assertIn("testmod:ore resolves to no projecte value; give it an explicit value or exclude it",
                      port.problems)

    def test_cheap_recipes_for_vanilla_items_are_problems(self):
        write(self.dir / "res" / "data" / "testmod" / "recipes" / "gunpowder.json",
              shaped(["d"], {"d": {"item": "testmod:dust"}}, "minecraft:gunpowder"))
        port, _outputs = self.build()
        self.assertTrue(any("devalues vanilla minecraft:gunpowder" in p for p in port.problems), port.problems)

    def add_skull_recipe(self):
        write(self.dir / "res" / "data" / "testmod" / "recipes" / "bomb.json",
              shaped(["s"], {"s": {"item": "minecraft:wither_skeleton_skull"}}, "testmod:bomb"))
        write(self.dir / "emc" / "registry.txt",
              (self.dir / "emc" / "registry.txt").read_text() + "testmod:bomb\n")

    def test_vanilla_values_fill_gaps_in_one_system(self):
        self.add_skull_recipe()
        self.policy["vanilla"] = {"minecraft:wither_skeleton_skull": {
            "systems": ["projecte"], "like": "minecraft:nether_star", "times": "1/3", "why": "w"}}
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        self.assertEqual(values["testmod:bomb"], {"projecte": 46421, "itemalchemy": 16, "source": "recipe"})
        data = self.dir / "res" / "data" / "testmod"
        before = json.loads(outputs[data / "pe_custom_conversions" / "testmod.json"])["values"]["before"]
        self.assertEqual(before["minecraft:wither_skeleton_skull"], 46421)
        itemalchemy = json.loads(outputs[data / "default_emcs" / "testmod.json"])
        self.assertNotIn("minecraft:wither_skeleton_skull", itemalchemy)

    def test_vanilla_values_never_override_a_default(self):
        self.policy["vanilla"] = {"minecraft:dirt": {"systems": ["projecte"], "emc": 5, "why": "w"}}
        port, _outputs = self.build()
        self.assertTrue(any(p.startswith("vanilla.minecraft:dirt: projecte already values it") for p in port.problems),
                        port.problems)

    def test_vanilla_values_may_fill_gaps_in_every_system(self):
        self.policy["vanilla"] = {"minecraft:petrified_oak_slab": {
            "systems": ["projecte", "itemalchemy"], "like": "minecraft:oak_slab", "why": "w"}}
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        data = self.dir / "res" / "data" / "testmod"
        itemalchemy = json.loads(outputs[data / "default_emcs" / "testmod.json"])
        self.assertIn("minecraft:petrified_oak_slab", itemalchemy)
        vanilla = json.loads(outputs[self.dir / "emc" / "values.json"])["vanilla"]
        self.assertEqual(set(vanilla["minecraft:petrified_oak_slab"]), {"projecte", "itemalchemy"})

    def test_processes_can_be_limited_to_one_system(self):
        self.add_skull_recipe()
        self.policy["vanilla"] = {"minecraft:wither_skeleton_skull": {"systems": ["projecte"], "emc": 256, "why": "w"}}
        self.policy["processes"].append({"name": "wither", "why": "w", "systems": ["projecte"],
                                         "in": {"minecraft:wither_skeleton_skull": 3, "minecraft:soul_sand": 4},
                                         "out": {"minecraft:nether_star": 1}})
        port, _outputs = self.build()
        gains = [p for p in port.problems if "process 'wither'" in p]
        self.assertEqual(len(gains), 1, port.problems)
        self.assertTrue(gains[0].startswith("projecte:"))

    def test_merge_writes_port_values_into_an_item_alchemy_config(self):
        port, outputs = self.build()
        for path, text in outputs.items():
            write(path, text)
        config = self.dir / "emc_config.json"
        write(config, {"minecraft:dirt": 1, "testmod:widget": 7})
        original = emc.discover
        emc.discover = lambda names: [self.dir]
        self.addCleanup(setattr, emc, "discover", original)
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            emc.main(["merge-itemalchemy", str(config)])
        merged = json.loads(config.read_text())
        self.assertEqual(merged["minecraft:dirt"], 1)
        self.assertEqual(merged["testmod:widget"], 508)
        self.assertEqual(merged["testmod:ore"], 0)

    def test_merge_fills_vanilla_gaps_without_overriding_the_pack(self):
        self.policy["vanilla"] = {
            "minecraft:petrified_oak_slab": {"systems": ["itemalchemy"], "emc": 4, "why": "w"},
            "minecraft:written_book": {"systems": ["itemalchemy"], "emc": 300, "why": "w"},
        }
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        for path, text in outputs.items():
            write(path, text)
        config = self.dir / "emc_config.json"
        write(config, {"minecraft:written_book": 250})
        original = emc.discover
        emc.discover = lambda names: [self.dir]
        self.addCleanup(setattr, emc, "discover", original)
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            emc.main(["merge-itemalchemy", str(config)])
        merged = json.loads(config.read_text())
        self.assertEqual(merged["minecraft:petrified_oak_slab"], 4)
        self.assertEqual(merged["minecraft:written_book"], 250)


class MultiNamespaceTests(unittest.TestCase):
    def test_a_second_namespace_ships_its_own_files(self):
        sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(sandbox.cleanup)
        root = Path(sandbox.name) / "combo"
        core = root / "core"
        extra = root / "extra"
        write(core / "data" / "combo" / "recipes" / "widget.json",
              shaped(["i"], {"i": {"item": "minecraft:iron_ingot"}}, "combo:widget"))
        write(extra / "data" / "comboextra" / "recipes" / "gadget.json",
              shaped(["d"], {"d": {"item": "minecraft:dirt"}}, "comboextra:gadget"))
        write(root / "emc" / "registry.txt", "combo:widget\ncomboextra:gadget\ncomboextra:bucket\n")
        policy = {
            "namespace": "combo",
            "namespaces": ["combo", "comboextra"],
            "loaders": {"projecte": "fabric", "itemalchemy": "fabric"},
            "resources": {"projecte": ["core", "extra"], "itemalchemy": ["core", "extra"]},
            "output": "core",
            "outputs": {"combo": "core", "comboextra": "extra"},
            "exclude": {"comboextra:bucket": "filled by a machine"},
            "conditions": {"comboextra:config": {"holds": False, "why": "w"}},
        }
        write(root / "emc" / "policy.json", policy)
        port, outputs = emc.build(root)
        self.assertEqual(port.problems, [])
        self.assertEqual(port.namespaces, ["combo", "comboextra"])
        values = json.loads(outputs[root / "emc" / "values.json"])["items"]
        self.assertEqual(values["combo:widget"]["source"], "recipe")
        self.assertIn("projecte", values["comboextra:gadget"])
        self.assertEqual(values["comboextra:bucket"], {"excluded": "filled by a machine"})
        extra_emc = json.loads(outputs[extra / "data" / "comboextra" / "default_emcs" / "comboextra.json"])
        self.assertEqual(set(extra_emc), {"comboextra:gadget", "comboextra:bucket"})
        self.assertEqual(extra_emc["comboextra:bucket"], 0)
        core_emc = json.loads(outputs[core / "data" / "combo" / "default_emcs" / "combo.json"])
        self.assertEqual(set(core_emc), {"combo:widget"})
        self.assertIn("This policy also covers `comboextra`", outputs[root / "emc" / "README.md"])

    def test_namespace_lists_start_with_the_primary_id(self):
        base = {"namespace": "t", "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}, "output": "r"}
        emc.validate_policy({**base, "namespaces": ["t", "textra"], "outputs": {"t": "r", "textra": "x"},
                             "conditions": {"textra:config": {"holds": True, "why": "w"}}})
        for bad in ({"namespaces": ["other"]},
                    {"namespaces": ["t", "t"]},
                    {"namespaces": ["t", "Bad"]},
                    {"outputs": {"nope": "r"}},
                    {"namespaces": ["t", "textra"], "outputs": {"t": "other", "textra": "x"}},
                    {"namespaces": ["t", "textra"], "outputs": {"t": "r", "textra": "../x"}},
                    {"namespaces": ["t", "textra"], "outputs": {"t": "r", "textra": "x"},
                     "conditions": {"nope:config": {"holds": True, "why": "w"}}}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, **bad})


class RepositoryTests(unittest.TestCase):
    def test_every_port_with_a_policy_is_complete_and_current(self):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = emc.main(["check"])
        self.assertEqual(status, 0, err.getvalue())


if __name__ == "__main__":
    unittest.main()
