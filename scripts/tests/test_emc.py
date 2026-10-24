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


SKULL_OVERRIDE = {"replaces": 16, "like": "minecraft:nether_star", "times": "1/3", "round": "up",
                  "ports": ["testmod"], "why": "w"}


def use_pack_data(test, overrides=None, processes=None):
    """Point emc.py's pack-wide override and process files at temporary ones for one test."""
    sandbox = tempfile.TemporaryDirectory()
    test.addCleanup(sandbox.cleanup)
    for name, data, key in (("OVERRIDES", overrides, "overrides"), ("PACK_PROCESSES", processes, "processes")):
        if data is None:
            continue
        path = Path(sandbox.name) / f"{name}.json"
        write(path, {key: data})
        test.addCleanup(setattr, emc, name, getattr(emc, name))
        setattr(emc, name, path)


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

    def test_mapper_permits_equal_cost_and_refuses_undercuts(self):
        values = {"a:in": 10, "a:out": 10, "a:dear": 40}
        self.assertTrue(emc.mapper_permits({"a:in": 1}, "a:out", 1, values))
        self.assertTrue(emc.mapper_permits({"a:in": 2}, "a:out", 2, values))
        self.assertTrue(emc.mapper_permits({"a:in": 3}, "a:out", 2, {"a:in": 10, "a:out": 15}))
        self.assertFalse(emc.mapper_permits({"a:in": 3}, "a:out", 4, {"a:in": 10, "a:out": 8}))
        self.assertFalse(emc.mapper_permits({"a:in": 1}, "a:dear", 1, values))
        self.assertFalse(emc.mapper_permits({"GAS|mekanism:oxygen": 1, "a:in": 1}, "a:out", 1, values))
        self.assertFalse(emc.mapper_permits({"a:in": 1}, "a:missing", 1, values))
        self.assertFalse(emc.mapper_permits({"a:in": -1}, "a:out", 1, values))
        self.assertFalse(emc.mapper_permits({"a:below": 1}, "a:out", 1, {"a:below": Fraction(1, 5), "a:out": 1}))
        self.assertFalse(emc.mapper_permits({}, "a:out", 1, values))
        saw = {"type": "mekanism:sawing", "input": {"ingredient": {"item": "minecraft:oak_log"}},
               "mainOutput": {"item": "minecraft:oak_planks", "count": 6},
               "secondaryOutput": {"item": "mekanism:sawdust"}}
        self.assertIsNone(emc.item_only_machine_recipe(saw))
        crushing = {"type": "mekanism:crushing", "input": {"ingredient": {"item": "a:in"}},
                    "output": {"item": "a:out"}}
        self.assertEqual(emc.item_only_machine_recipe(crushing), ("a:out", 1, [({"item": "a:in"}, 1)]))

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
                    {"projecte_mappers": {"why": "w"}},
                    {"projecte_mappers": {"guard": "/tmp/guard.json", "why": "w"}},
                    {"surprise": 1}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, **bad})
        emc.validate_policy({**base, "projecte_mappers": {"guard": "data/guard.json", "why": "w"}})

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
        write(recipes / "gated.json", shaped(["d"], {"d": {"item": "minecraft:diamond_block"}}, "testmod:widget",
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
        use_pack_data(self, overrides={}, processes=[])
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

    def use_this_port_only(self):
        original = emc.discover
        emc.discover = lambda names: [self.dir]
        self.addCleanup(setattr, emc, "discover", original)

    def test_the_pack_wither_summon_is_a_gain_at_the_pack_skull_value(self):
        use_pack_data(self, overrides={})
        port, _outputs = self.build()
        gains = [p for p in port.problems if "process 'summon_wither'" in p]
        self.assertTrue(gains and all(p.startswith("itemalchemy:") for p in gains), port.problems)

    def test_an_override_prices_the_analysis_and_ships_a_late_default_emcs_file(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": SKULL_OVERRIDE})
        self.add_skull_recipe()
        self.policy["vanilla"] = {"minecraft:wither_skeleton_skull": {
            "systems": ["projecte"], "like": "minecraft:nether_star", "times": "1/3", "round": "up", "why": "w"}}
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        values = json.loads(outputs[self.dir / "emc" / "values.json"])
        self.assertEqual(values["items"]["testmod:bomb"]["itemalchemy"], 46422)
        self.assertEqual(values["itemalchemy_overrides"], {"minecraft:wither_skeleton_skull": 46422})
        data = self.dir / "res" / "data" / "testmod" / "default_emcs"
        shipped = json.loads(outputs[data / f"{emc.override_file_name('testmod')}.json"])
        self.assertEqual(shipped, {"minecraft:wither_skeleton_skull": 46422})
        self.assertNotIn("minecraft:wither_skeleton_skull", json.loads(outputs[data / "testmod.json"]))
        self.assertIn("(pack override)", outputs[self.dir / "emc" / "README.md"])

    def test_an_override_for_other_ports_is_used_but_not_shipped(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": {**SKULL_OVERRIDE, "ports": ["other"]}})
        self.add_skull_recipe()
        port, outputs = self.build()
        values = json.loads(outputs[self.dir / "emc" / "values.json"])
        self.assertEqual(values["items"]["testmod:bomb"]["itemalchemy"], 46422)
        self.assertNotIn("itemalchemy_overrides", values)
        self.assertFalse(any(p.name.startswith("pack_overrides_") for p in outputs))

    def test_a_stale_override_is_reported_and_not_applied(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": {**SKULL_OVERRIDE, "replaces": 17}})
        self.use_this_port_only()
        port, _outputs = self.build()
        self.assertEqual(emc.read_baseline("itemalchemy")["minecraft:wither_skeleton_skull"], 16)
        problems, _notes = emc.check_pack([port])
        self.assertTrue(any("not the 17 it replaces" in p for p in problems), problems)

    def test_a_vanilla_fill_must_scale_like_its_override(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": SKULL_OVERRIDE})
        self.use_this_port_only()
        self.policy["vanilla"] = {"minecraft:wither_skeleton_skull": {
            "systems": ["projecte"], "like": "minecraft:nether_star", "times": "1/3", "why": "w"}}
        port, _outputs = self.build()
        problems, _notes = emc.check_pack([port])
        self.assertTrue(any("differently from its Item Alchemy override" in p for p in problems), problems)
        self.policy["vanilla"]["minecraft:wither_skeleton_skull"]["round"] = "up"
        port, outputs = self.build()
        self.assertEqual(emc.check_pack([port])[0], [])
        before = json.loads(outputs[self.dir / "res" / "data" / "testmod" / "pe_custom_conversions" /
                                    "testmod.json"])["values"]["before"]
        self.assertEqual(before["minecraft:wither_skeleton_skull"], 46422)

    def test_the_pack_check_runs_port_processes_with_merged_values(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": SKULL_OVERRIDE}, processes=[])
        self.use_this_port_only()
        self.add_skull_recipe()
        port, _outputs = self.build()
        self.assertEqual(emc.check_pack([port])[0], [])
        # A second port pricing the bomb's output cheaply would open a loop through this port's skull recipe.
        port.vanilla["itemalchemy"]["minecraft:wither_skeleton_skull"] = 1
        problems, _notes = emc.check_pack([port])
        self.assertTrue(any("testmod:bomb" in p and "(with every port's values)" in p for p in problems), problems)

    def merge(self, config_values):
        config = self.dir / "emc_config.json"
        write(config, config_values)
        err = io.StringIO()
        with redirect_stdout(io.StringIO()), redirect_stderr(err):
            status = emc.main(["merge-itemalchemy", str(config)])
        return status, json.loads(config.read_text()), err.getvalue()

    def test_merge_applies_overrides_only_over_the_value_they_replace(self):
        use_pack_data(self, overrides={"minecraft:wither_skeleton_skull": SKULL_OVERRIDE})
        self.use_this_port_only()
        _port, outputs = self.build()
        for path, text in outputs.items():
            write(path, text)
        status, merged, _err = self.merge({"minecraft:wither_skeleton_skull": 16, "minecraft:nether_star": 139264})
        self.assertEqual((status, merged["minecraft:wither_skeleton_skull"]), (0, 46422))
        status, again, _err = self.merge(merged)
        self.assertEqual((status, again), (0, merged))
        # Port values were generated against the baseline, so a config that disagrees is left untouched.
        status, merged, err = self.merge({"minecraft:wither_skeleton_skull": 999})
        self.assertEqual((status, merged), (1, {"minecraft:wither_skeleton_skull": 999}))
        self.assertIn("not the 16 the override replaces", err)
        status, merged, err = self.merge({"minecraft:nether_star": 2700})
        self.assertEqual((status, merged), (1, {"minecraft:nether_star": 2700}))
        self.assertIn("not the baseline's 139264", err)

    def add_fuse_recipe(self, gas="testmod:plasma"):
        write(self.dir / "res" / "data" / "testmod" / "recipes" / "fuse.json",
              {"type": "testmod:fuse", "itemInput": {"ingredient": {"item": "minecraft:dirt"}, "amount": 1},
               "gasInput": {"gas": gas, "amount": 500}, "output": {"item": "minecraft:diamond", "count": 1}})

    def fuse_process(self, item):
        return [{"name": "fusing", "why": "w", "type": "testmod:fuse", "items": ["itemInput"],
                 "chemicals": {"gasInput": {"testmod:plasma": {"item": item, "per": 1000}}}, "outputs": ["output"]}]

    def test_recipe_processes_flag_machine_recipes_that_gain(self):
        self.add_fuse_recipe()
        self.policy["recipe_processes"] = self.fuse_process("testmod:dust")
        port, _outputs = self.build()
        gains = [p for p in port.problems if "testmod:fuse recipe testmod:fuse" in p]
        self.assertEqual(len(gains), 2, port.problems)
        self.assertIn("turns 3.5 EMC of input into 8192 EMC", gains[0])

    def test_recipe_processes_whose_inputs_have_no_emc_are_closed(self):
        self.add_fuse_recipe()
        self.policy["recipe_processes"] = self.fuse_process("testmod:ore")
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        self.assertEqual(port.recipe_process_stats["fusing"]["closed"], {"testmod:ore": set(emc.SYSTEMS)})
        self.add_fuse_recipe("testmod:unknown")
        port, _outputs = self.build()
        self.assertTrue(any("uses gasInput testmod:unknown" in p for p in port.problems), port.problems)

    def test_recipe_process_grammar(self):
        self.policy["recipe_processes"] = self.fuse_process("testmod:dust")
        self.policy["recipe_processes"][0]["chemicals"]["gasInput"]["testmod:plasma"]["per"] = 0
        with self.assertRaises(emc.PolicyError):
            self.build()

    def test_unmanaged_default_emcs_files_are_problems(self):
        write(self.dir / "res" / "data" / "testmod" / "default_emcs" / "extra.json", {"minecraft:dirt": 5})
        port, _outputs = self.build()
        self.assertTrue(any("extra.json is an Item Alchemy default_emcs file" in p for p in port.problems),
                        port.problems)


class PackOverrideTests(unittest.TestCase):
    def test_java_hash_matches_string_hash_code(self):
        self.assertEqual(emc.java_hash(""), 0)
        self.assertEqual(emc.java_hash("hello"), 99162322)
        self.assertEqual(emc.java_hash("polygenelubricants"), 2**31)  # Integer.MIN_VALUE, unsigned

    def test_override_files_sort_after_item_alchemys_own_defaults(self):
        for namespace, expected in (("icbm_port", "pack_overrides_28177"), ("mekanism", "pack_overrides_33284")):
            name = emc.override_file_name(namespace)
            self.assertEqual(name, expected)
            self.assertEqual(emc.hashmap_spread(namespace, f"default_emcs/{name}.json") & 0xFFFF, 0xFFFF)
        for path in ("default_emcs/minecraft.json", "default_emcs/tags.json"):
            self.assertNotEqual(emc.hashmap_spread("itemalchemy", path) & 0xF, 0xF)

    def test_override_values_scale_the_raw_anchor_and_report_stale_entries(self):
        overrides = {"minecraft:wither_skeleton_skull": SKULL_OVERRIDE}
        raw = {"minecraft:wither_skeleton_skull": Fraction(16), "minecraft:nether_star": Fraction(139264)}
        self.assertEqual(emc.override_values(raw, overrides), ({"minecraft:wither_skeleton_skull": 46422}, []))
        values, problems = emc.override_values({**raw, "minecraft:wither_skeleton_skull": Fraction(10246)}, overrides)
        self.assertEqual(values, {})
        self.assertEqual(len(problems), 1)

    def test_round_takes_the_ceiling_or_floor_of_a_like_value(self):
        baseline = {"minecraft:nether_star": Fraction(139264)}
        spec = {"like": "minecraft:nether_star", "times": "1/3", "why": "w"}
        self.assertEqual(emc.explicit_value(spec, "itemalchemy", baseline, "k"), 46421)
        self.assertEqual(emc.explicit_value({**spec, "round": "down"}, "itemalchemy", baseline, "k"), 46421)
        self.assertEqual(emc.explicit_value({**spec, "round": "up"}, "itemalchemy", baseline, "k"), 46422)
        base = {"namespace": "t", "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}}
        for bad in ({"emc": 5, "round": "up", "why": "w"}, {**spec, "round": "sideways"}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, "vanilla": {"minecraft:wither_skeleton_skull": bad}})

    def test_override_grammar(self):
        emc.validate_overrides({"comment": "c", "overrides": {"minecraft:wither_skeleton_skull": SKULL_OVERRIDE}})
        for item, entry in (("testmod:bomb", SKULL_OVERRIDE),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "why": ""}),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "emc": 5}),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "replaces": True}),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "ports": []}),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "round": "sideways"}),
                            ("minecraft:wither_skeleton_skull", {**SKULL_OVERRIDE, "surprise": 1}),
                            ("minecraft:nether_star", {**SKULL_OVERRIDE, "like": "minecraft:wither_skeleton_skull"})):
            document = {"overrides": {item: entry}}
            if item == "minecraft:nether_star":
                document["overrides"]["minecraft:wither_skeleton_skull"] = SKULL_OVERRIDE
            with self.subTest(item=item, entry=entry), self.assertRaises(emc.PolicyError):
                emc.validate_overrides(document)

    def test_the_shipped_wither_override_closes_the_summon_loop(self):
        overrides = emc.load_overrides()
        self.assertEqual(overrides["minecraft:wither_skeleton_skull"]["replaces"], 16)
        star = emc.raw_baseline("itemalchemy")["minecraft:nether_star"]
        skull = emc.read_baseline("itemalchemy")["minecraft:wither_skeleton_skull"]
        self.assertEqual(skull, -(-star // 3))
        self.assertGreaterEqual(3 * skull, star)
        names = {process["name"] for process in emc.load_pack_processes()}
        self.assertIn("summon_wither", names)


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


class OptionalModTests(unittest.TestCase):
    """A port whose recipes load only with another port's mod, like ICBM's Mekanism alloy recipes."""

    def setUp(self):
        sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(sandbox.cleanup)
        root = Path(sandbox.name)
        other = root / "othermod"
        write(other / "emc" / "policy.json", {
            "namespace": "othermod", "loaders": {"projecte": "fabric", "itemalchemy": "fabric"},
            "resources": {"projecte": ["res"], "itemalchemy": ["res"]}, "output": "res"})
        write(other / "emc" / "values.json", {"namespace": "othermod", "items": {
            "othermod:alloy": {"projecte": 10, "itemalchemy": 7, "source": "explicit"},
            "othermod:secret": {"excluded": "no EMC"}}})
        write(other / "res" / "data" / "c" / "tags" / "items" / "fuel_dusts.json", {"values": ["othermod:alloy"]})
        self.dir = root / "testmod"
        self.recipes = self.dir / "res" / "data" / "testmod" / "recipes"
        without = {"condition": "fabric:not", "value": {"condition": "fabric:all_mods_loaded", "values": ["othermod"]}}
        loaded = {"condition": "fabric:all_mods_loaded", "values": ["othermod"]}
        fuel = {"condition": "fabric:tags_populated", "values": ["c:fuel_dusts"]}
        # The fallback, and an alloy recipe othermod's own fuel tag would switch off (ICBM's nuclear case).
        write(self.recipes / "bomb.json", shaped(["d"], {"d": {"item": "minecraft:diamond"}}, "testmod:bomb",
                                                 **{"fabric:load_conditions": [without]}))
        write(self.recipes / "bomb_alloy.json", shaped(["aa"], {"a": {"item": "othermod:alloy"}}, "testmod:bomb",
                                                       **{"fabric:load_conditions": [
                                                           loaded, {"condition": "fabric:not", "value": fuel}]}))
        # A recipe that loads when any mod fills a tag (ICBM's uranium recipe), and its unconditional fallback.
        write(self.recipes / "charge.json", shaped(["g"], {"g": {"item": "minecraft:gold_ingot"}}, "testmod:charge"))
        write(self.recipes / "charge_fuel.json", shaped(["f"], {"f": {"tag": "c:fuel_dusts"}}, "testmod:charge",
                                                        **{"fabric:load_conditions": [fuel]}))
        # Gated off with othermod, so its cheap yield never meets othermod's dust (ICBM's gunpowder case).
        write(self.recipes / "charge_bulk.json", shaped(["f"], {"f": {"tag": "c:fuel_dusts"}}, "testmod:charge", 64,
                                                        **{"fabric:load_conditions": [fuel, without]}))
        write(self.dir / "emc" / "registry.txt", "testmod:bomb\ntestmod:charge\n")
        self.policy = {
            "namespace": "testmod", "loaders": {"projecte": "fabric", "itemalchemy": "fabric"},
            "resources": {"projecte": ["res"], "itemalchemy": ["res"]}, "output": "res",
            "optional_mods": {"othermod": {"port": "othermod", "why": "w"}},
        }

    def build(self):
        write(self.dir / "emc" / "policy.json", self.policy)
        return emc.build(self.dir)

    def test_outputs_take_the_cheapest_recipe_any_optional_mod_enables(self):
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        # ProjectE derives values from the recipes that load, so standalone it reads the unconditional recipes.
        self.assertEqual(values["testmod:bomb"], {"projecte": 20, "itemalchemy": 14, "projecte_standalone": 8192,
                                                  "source": "recipe"})
        self.assertEqual(values["testmod:charge"], {"projecte": 10, "itemalchemy": 7, "projecte_standalone": 2048,
                                                    "source": "recipe"})
        self.assertNotIn("othermod:alloy", json.loads(outputs[self.dir / "res" / "data" / "testmod" / "default_emcs"
                                                              / "testmod.json"]))
        readme = outputs[self.dir / "emc" / "README.md"]
        self.assertIn("## Recipes other mods enable", readme)
        self.assertIn("| `testmod:bomb_alloy` | `testmod:bomb` | loads with othermod | 20 | 14 |", readme)
        self.assertNotIn("testmod:charge_bulk", readme)

    def add_battery(self):
        """ICBM's battery: an unconditional recipe whose sulfur tag othermod's cheap dust joins."""
        other = self.dir.parent / "othermod"
        document = json.loads((other / "emc" / "values.json").read_text())
        document["items"]["othermod:sulfur"] = {"projecte": 3, "itemalchemy": 2, "source": "explicit"}
        write(other / "emc" / "values.json", document)
        write(other / "res" / "data" / "c" / "tags" / "items" / "sulfur_dusts.json", {"values": ["othermod:sulfur"]})
        write(self.dir / "res" / "data" / "testmod" / "tags" / "items" / "materials" / "sulfur_dusts.json",
              {"values": ["minecraft:gunpowder", {"id": "#c:sulfur_dusts", "required": False}]})
        write(self.recipes / "battery.json", shaped(["sr"], {"s": {"tag": "testmod:materials/sulfur_dusts"},
                                                             "r": {"item": "minecraft:redstone"}}, "testmod:battery"))
        write(self.dir / "emc" / "registry.txt", "testmod:battery\ntestmod:bomb\ntestmod:charge\n")

    def test_an_optional_mod_filling_a_tag_lowers_unconditional_recipes(self):
        self.add_battery()
        port, outputs = self.build()
        self.assertEqual(port.problems, [])
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        # Standalone the cheapest sulfur is gunpowder (192); with othermod its dust (3 / 2) is, so the battery
        # takes that price (64 redstone + dust) rather than 256, or crafting it with othermod would gain EMC.
        self.assertEqual(values["testmod:battery"], {"projecte": 67, "itemalchemy": 66, "projecte_standalone": 256,
                                                     "source": "recipe"})
        self.assertIn("| `testmod:battery` | `testmod:battery` | wider tags with othermod | 67 | 66 |",
                      outputs[self.dir / "emc" / "README.md"])

    def test_the_gain_check_covers_unconditional_recipes_with_an_optional_mods_tags(self):
        self.add_battery()
        self.policy["values"] = {"testmod:battery": {"emc": 256, "why": "w"}}
        port, _outputs = self.build()
        self.assertIn("Item Alchemy: recipe testmod:battery (with othermod) makes 1 testmod:battery worth 256 "
                      "from 66 EMC of ingredients", port.problems)
        self.assertFalse(any("testmod:battery" in p and "(with othermod)" not in p for p in port.problems),
                         port.problems)

    def test_an_undeclared_mod_leaves_its_recipes_unpriced_and_fails_the_check(self):
        del self.policy["optional_mods"]
        port, outputs = self.build()
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        self.assertGreater(values["testmod:bomb"]["projecte"], 20)
        self.assertIn("ProjectE: recipe testmod:bomb_alloy (loads only with othermod) needs othermod:alloy, which "
                      "has no EMC here; declare the mod in optional_mods or exclude testmod:bomb", port.problems)

    def test_a_conditional_recipe_cheaper_than_a_fixed_value_is_a_gain(self):
        self.policy["values"] = {"testmod:charge": {"emc": 100, "why": "w"}}
        port, _outputs = self.build()
        self.assertIn("Item Alchemy: recipe testmod:charge_fuel (with othermod) makes 1 testmod:charge worth 100 "
                      "from 7 EMC of ingredients", port.problems)

    def test_an_alternative_needing_an_unpriced_item_is_a_problem(self):
        write(self.recipes / "bomb_secret.json", shaped(["s"], {"s": {"item": "othermod:secret"}}, "testmod:bomb",
                                                        **{"fabric:load_conditions": [
                                                            {"condition": "fabric:all_mods_loaded",
                                                             "values": ["othermod"]}]}))
        port, _outputs = self.build()
        self.assertTrue(any("testmod:bomb_secret" in p and "needs othermod:secret" in p
                            and "price it in that mod's port" in p for p in port.problems), port.problems)

    def test_tag_conditions_may_go_either_way_but_mod_conditions_are_fixed(self):
        tag = {"condition": "fabric:tags_populated", "values": ["c:x"]}
        mod = {"condition": "fabric:all_mods_loaded", "values": ["m"]}
        self.assertEqual(emc.condition_possible(tag, "fabric", set()), (True, True))
        self.assertEqual(emc.condition_possible({"condition": "fabric:and", "values": [tag, mod]}, "fabric", set()),
                         (False, True))
        self.assertEqual(emc.condition_possible({"condition": "fabric:not", "value": mod}, "fabric", {"m"}),
                         (False, True))
        self.assertEqual(emc.condition_mods([{"condition": "fabric:not", "value": mod}]), {"m"})

    def test_optional_mod_grammar(self):
        base = {"namespace": "t", "loaders": {"projecte": "fabric", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}, "output": "r"}
        emc.validate_policy({**base, "optional_mods": {"m": {"port": "m", "why": "w"}}})
        for bad in ({"m": {"port": "m"}}, {"t": {"port": "t", "why": "w"}}, {"m": {"port": "../m", "why": "w"}},
                    {"m": "m"}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, "optional_mods": bad})
        self.policy["optional_mods"] = {"othermod": {"port": "missing", "why": "w"}}
        with self.assertRaises(emc.PolicyError):
            self.build()


class ProjectEModelTests(unittest.TestCase):
    """A Forge-loader port exercising the ProjectE mappers emc.py reproduces."""

    def setUp(self):
        sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(sandbox.cleanup)
        self.dir = Path(sandbox.name) / "pe"
        self.res = self.dir / "res"
        recipes = self.res / "data" / "pe" / "recipes"
        shapeless = lambda items, result, count=1: {  # noqa: E731
            "type": "minecraft:crafting_shapeless", "ingredients": [{"item": i} for i in items],
            "result": {"item": result, "count": count}}
        write(recipes / "raw_block.json", shapeless(["pe:raw_tin"] * 9, "pe:raw_tin_block"))
        write(recipes / "smelt_tin.json", {"type": "minecraft:smelting", "ingredient": {"item": "pe:raw_tin"},
                                           "result": "pe:tin_ingot"})
        write(recipes / "pigment.json", shapeless(["minecraft:red_dye"] * 2, "pe:red_pigment"))
        write(recipes / "frost.json", shapeless(["pe:goo_bucket", "minecraft:stick"], "pe:frost"))
        write(recipes / "barrel.json", shapeless(["minecraft:iron_ingot"], "pe:barrel"))
        write(recipes / "chest.json", shapeless(["minecraft:iron_ingot", "minecraft:stick"], "pe:chest"))
        tags = self.res / "data"
        write(tags / "forge" / "tags" / "items" / "ores.json", {"values": ["pe:tin_ore"]})
        write(tags / "forge" / "tags" / "items" / "raw_materials.json", {"values": ["pe:raw_tin"]})
        write(tags / "forge" / "tags" / "items" / "ingots" / "tin.json", {"values": ["pe:tin_ingot"]})
        write(tags / "forge" / "tags" / "items" / "dyes" / "red.json", {"values": ["pe:red_pigment"]})
        write(tags / "pe" / "tags" / "items" / "storage.json", {"values": ["pe:barrel", "pe:chest"]})
        write(tags / "pe" / "tags" / "items" / "same.json", {"values": ["pe:barrel", "pe:barrel_copy"]})
        write(recipes / "barrel_copy.json", shapeless(["minecraft:iron_ingot"], "pe:barrel_copy"))
        registry = ["pe:tin_ore", "pe:raw_tin", "pe:raw_tin_block", "pe:tin_ingot", "pe:red_pigment",
                    "pe:goo_bucket", "pe:frost", "pe:barrel", "pe:barrel_copy", "pe:chest", "pe:safe",
                    "pe:vault", "pe:plate"]
        write(self.dir / "emc" / "registry.txt", "\n".join(registry) + "\n")
        self.policy = {
            "namespace": "pe",
            "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
            "resources": {"projecte": ["res"], "itemalchemy": ["res"]},
            "output": "res",
            "values": {
                "pe:tin_ore": {"emc": 100, "why": "w"},
                "pe:raw_tin": {"emc": 50, "why": "w"},
                "pe:goo_bucket": {"emc": 800, "why": "w"},
            },
            "conversions": [
                {"name": "press", "why": "w", "output": "pe:plate", "ingredients": {"#forge:ingots/tin": 1}},
                {"name": "lock", "why": "w", "output": "pe:safe", "ingredients": {"#pe:storage": 1}},
                {"name": "vault", "why": "w", "output": "pe:vault", "ingredients": {"#pe:same": 2}},
            ],
            "remainders": {"pe:*_bucket": {"item": "minecraft:bucket", "systems": ["projecte"], "why": "w"}},
        }

    def build(self):
        write(self.dir / "emc" / "policy.json", self.policy)
        port, outputs = emc.build(self.dir)
        values = json.loads(outputs[self.dir / "emc" / "values.json"])["items"]
        projecte = json.loads(outputs[self.res / "data" / "pe" / "pe_custom_conversions" / "pe.json"])
        return port, values, projecte

    def test_ore_blacklists_zero_ores_and_what_only_they_make(self):
        port, values, projecte = self.build()
        self.assertEqual(port.problems, [])
        self.assertEqual(values["pe:tin_ore"]["projecte"], 0)
        self.assertEqual(values["pe:tin_ore"]["itemalchemy"], 100)
        self.assertEqual(values["pe:tin_ore"]["no_value"]["projecte"], "#forge:ores (OresBlacklistMapper)")
        self.assertEqual(values["pe:raw_tin"]["no_value"]["projecte"], "#forge:raw_materials (RawOresBlacklistMapper)")
        self.assertEqual(values["pe:raw_tin_block"]["projecte"], 0)
        self.assertEqual(values["pe:raw_tin_block"]["itemalchemy"], 450)
        self.assertEqual(values["pe:raw_tin_block"]["no_value"]["projecte"],
                         "made only from items ProjectE's ore blacklists zero")
        self.assertNotIn("pe:tin_ore", projecte["values"]["before"])
        self.assertNotIn("pe:raw_tin", projecte["values"]["before"])

    def test_metals_json_prices_tagged_ingots_and_policy_tags_name_the_item(self):
        port, values, projecte = self.build()
        self.assertEqual(values["pe:tin_ingot"]["projecte"], 256)
        self.assertEqual(values["pe:tin_ingot"]["source"], "tag #forge:ingots/tin (ProjectE metals.json)")
        self.assertEqual(values["pe:plate"]["projecte"], 256)
        press = projecte["groups"]["pe_press"]["conversions"]
        self.assertEqual(press, [{"output": "pe:plate", "ingredients": {"pe:tin_ingot": 1}}])

    def test_a_cheaper_recipe_for_a_metals_priced_item_is_a_problem(self):
        write(self.res / "data" / "pe" / "recipes" / "cheap_tin.json",
              {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:iron_nugget"}],
               "result": {"item": "pe:tin_ingot"}})
        port, _values, _projecte = self.build()
        self.assertTrue(any("metals.json prices pe:tin_ingot at 256" in p for p in port.problems), port.problems)
        self.policy["values"]["pe:tin_ingot"] = {"emc": 28, "why": "w"}
        port, values, _projecte = self.build()
        self.assertEqual(port.problems, [])
        self.assertEqual(values["pe:tin_ingot"]["projecte"], 28)

    def test_a_metals_tag_with_several_members_cannot_stand_for_an_item(self):
        write(self.res / "data" / "forge" / "tags" / "items" / "ingots" / "tin.json",
              {"values": ["pe:tin_ingot", "pe:chest"]})
        port, _values, _projecte = self.build()
        self.assertTrue(any("conversion press: ProjectE prices #forge:ingots/tin through metals.json" in p
                            for p in port.problems), port.problems)

    def test_tag_mapper_lowers_members_of_tags_projecte_creates(self):
        port, values, _projecte = self.build()
        self.assertEqual(values["pe:red_pigment"]["projecte"], 16)
        self.assertEqual(values["pe:red_pigment"]["itemalchemy"], 32)
        self.policy["exclude"] = {"pe:red_pigment": "w"}
        port, _values, _projecte = self.build()
        self.assertIn("pe:red_pigment is excluded, but ProjectE's TagMapper values it through #forge:dyes/red",
                      port.problems)

    def test_port_tags_with_unequal_members_are_written_as_the_cheapest_item(self):
        port, values, projecte = self.build()
        self.assertEqual(values["pe:safe"]["projecte"], 256)
        self.assertEqual(values["pe:chest"]["projecte"], 260)
        lock = projecte["groups"]["pe_lock"]["conversions"]
        self.assertEqual(lock, [{"output": "pe:safe", "ingredients": {"pe:barrel": 1}}])
        vault = projecte["groups"]["pe_vault"]["conversions"]
        self.assertEqual(vault, [{"output": "pe:vault", "ingredients": {"#pe:same": 2}}])
        self.assertIn("pe:storage", port.projecte_tag_reasons)

    def test_policy_remainders_can_apply_to_projecte_only(self):
        port, values, _projecte = self.build()
        self.assertEqual(values["pe:frost"]["projecte"], 800 - 768 + 4)
        self.assertEqual(values["pe:frost"]["itemalchemy"], 800 + 4)
        base = {"namespace": "t", "loaders": {"projecte": "forge", "itemalchemy": "fabric"},
                "resources": {"projecte": ["r"], "itemalchemy": ["r"]}, "output": "r"}
        emc.validate_policy({**base, "remainders": {"t:a": "minecraft:bucket"}})
        for bad in ({"t:a": {"item": "minecraft:bucket", "systems": ["projecte"]}},
                    {"t:a": {"item": "minecraft:bucket", "systems": ["x"], "why": "w"}},
                    {"t:a": "not an id"}):
            with self.subTest(bad=bad), self.assertRaises(emc.PolicyError):
                emc.validate_policy({**base, "remainders": bad})

    def test_unmanaged_projecte_files_are_problems(self):
        write(self.res / "data" / "pe" / "pe_custom_conversions" / "defaults.json", {"values": {"before": {}}})
        port, _values, _projecte = self.build()
        self.assertTrue(any("pe_custom_conversions/defaults.json is a ProjectE custom conversion file" in p
                            for p in port.problems), port.problems)

    def test_metals_conversions_are_read_from_projecte_defaults(self):
        document = {"values": {"conversion": [
            {"output": "#forge:ingots/bronze", "count": 4, "propagateTags": True,
             "ingredients": ["#forge:ingots/copper", "#forge:ingots/copper", "#forge:ingots/copper",
                             "#forge:ingots/tin"]},
            {"output": "#forge:ingots/lead", "propagateTags": True, "ingredients": {"#forge:ingots/iron": 2}}]}}
        self.assertEqual(emc.projecte_tag_conversions(document), [
            {"output": "forge:ingots/bronze", "count": 4,
             "ingredients": {"forge:ingots/copper": 3, "forge:ingots/tin": 1}},
            {"output": "forge:ingots/lead", "ingredients": {"forge:ingots/iron": 2}}])
        with self.assertRaises(emc.PolicyError):
            emc.projecte_tag_conversions({"values": {"conversion": [
                {"output": "minecraft:dirt", "ingredients": {"#forge:ingots/iron": 1}}]}})


class RepositoryTests(unittest.TestCase):
    def test_every_port_with_a_policy_is_complete_and_current(self):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = emc.main(["check"])
        self.assertEqual(status, 0, err.getvalue())


if __name__ == "__main__":
    unittest.main()
