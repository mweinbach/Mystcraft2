"""Check that the converted laboratory models retain their source geometry."""
import json
from pathlib import Path
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "upstream/src/main/resources/assets/jurassicraft/models/block"
MODELS = ROOT / "src/main/resources/assets/jurassicraft/models/block"

class DnaMachineModelTests(unittest.TestCase):
    def test_tabula_cube_faces_are_preserved(self):
        for output, source in (("dna_extractor", "dna_extractor"), ("dna_sequencer", "dna_sequencer"),
                               ("dna_combinator_hybridizer", "dna_combinator"), ("dna_synthesizer", "dna_synthesizer"),
                               ("embryonic_machine", "embryonic_machine"), ("embryo_calcification_machine", "embryo_calcification_machine"),
                               ("incubator", "incubator"), ("cleaning_station", "cleaning_station"), ("feeder", "feeder")):
            with self.subTest(model=output), ZipFile(SOURCE / (source + ".tbl")) as archive:
                model = json.loads(archive.read("model.json"))
                def count(cube):
                    inflate = cube.get("mcScale", 0)
                    flat = sum(d + 2 * inflate == 0 for d in cube["dimensions"])
                    # A zero-thickness plane still has two valid sides.
                    # Lids and combinator screens are drawn by block-entity renderers.
                    animated = (output == "feeder" and cube["name"] in ("Lid 1", "Lid 2")
                                or output == "dna_combinator_hybridizer" and cube["name"] in ("Screen 1", "Screen 2"))
                    faces = 0 if cube.get("hidden", False) or flat > 1 or animated else 2 if flat == 1 else 6
                    return faces + sum(count(child) for child in cube["children"])
                expected_faces = sum(count(cube) for cube in model["cubes"])
                faces = sum(line.startswith("f ") for line in (MODELS / (output + ".obj")).read_text().splitlines())
                self.assertEqual(faces, expected_faces)

    def test_overlay_models_keep_variant_cubes(self):
        parts = MODELS.parent / "machine"
        def names(path):
            data = json.loads(path.read_text())
            found = []
            def visit(cube):
                found.append(cube["name"])
                for child in cube.get("children", []): visit(child)
            for cube in data["cubes"]: visit(cube)
            return found
        embryonic = names(parts / "embryonic_machine_parts.json")
        self.assertTrue({"Petri dish 1", "Petri dish 2", "Test tube rack 1", "Test tube rack 2"} <= set(embryonic))
        egg = names(parts / "embryo_calcification_egg.json")
        self.assertTrue({"Egg base", "shape21"} <= set(egg))
        for source in ("dna_combinator_screens.json", "dna_hybridizer_screens.json"):
            self.assertTrue({"Screen 1", "Screen 2"} <= set(names(parts / source)))

    def test_animated_feeder_lids_match_source(self):
        with ZipFile(SOURCE / "feeder.tbl") as archive:
            model = json.loads(archive.read("model.json"))
        self.assertEqual((model["textureWidth"], model["textureHeight"]), (64, 64))
        found = {}
        def visit(cube, position):
            point = tuple(a + b for a, b in zip(position, cube["position"]))
            if cube["name"] in ("Lid 1", "Lid 2"):
                found[cube["name"]] = (point, cube["offset"], cube["dimensions"], cube["txOffset"])
            for child in cube["children"]: visit(child, point)
        for cube in model["cubes"]: visit(cube, (0, 0, 0))
        self.assertEqual(found, {
            "Lid 1": ((0, 10.05, -4), [-4, -.5, 0], [8, 1, 4], [27, 19]),
            "Lid 2": ((0, 10.05, 4), [-4, -.5, -4], [8, 1, 4], [36, 24]),
        })

    def test_extractor_base_transform_and_texture_coordinates(self):
        lines = (MODELS / "dna_extractor.obj").read_text().splitlines()
        vertices = [tuple(map(float, line.split()[1:])) for line in lines if line.startswith("v ")]
        uvs = [tuple(map(float, line.split()[1:])) for line in lines if line.startswith("vt ")]
        self.assertEqual(vertices[:4], [(0, .1875, .875), (0, .1875, .125), (0, 0, .125), (0, 0, .875)])
        self.assertEqual(uvs[:4], [(.625, .1875), (.4375, .1875), (.4375, .234375), (.625, .234375)])

    def test_existing_grinder_and_cultivator_models_are_unchanged(self):
        grinder = json.loads((MODELS / "fossil_grinder.json").read_text())
        self.assertEqual(grinder, json.loads((SOURCE / "fossil_grinder.json").read_text()))
        for half in ("bottom", "top"):
            model = json.loads((MODELS / ("cultivator_" + half + ".json")).read_text())
            upstream = json.loads((SOURCE / "cultivator" / half / ("cultivate_" + half + "_white.json")).read_text())
            # The port adds the vanilla block parent for modern item display transforms.
            model.pop("parent", None)
            upstream.pop("parent", None)
            self.assertEqual(model, upstream)

    def test_existing_blockstate_rotations_and_cultivator_halves(self):
        states = MODELS.parents[1] / "blockstates"
        for name in ("embryonic_machine", "embryo_calcification_machine", "incubator", "cleaning_station"):
            variants = json.loads((states / (name + ".json")).read_text())["variants"]
            for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                entry = variants["facing=" + facing]
                self.assertEqual(entry["model"], "jurassicraft:block/" + name)
                self.assertEqual(entry.get("y", 0), rotation)
        variants = json.loads((states / "cultivate_bottom.json").read_text())["variants"]
        self.assertEqual(variants["half=lower"]["model"], "jurassicraft:block/cultivator_bottom")
        self.assertEqual(variants["half=upper"]["model"], "jurassicraft:block/cultivator_top")
        feeder = json.loads((states / "feeder.json").read_text())["variants"]
        self.assertEqual(set(feeder), {"facing=" + facing for facing in ("up", "down", "north", "east", "south", "west")})

if __name__ == "__main__":
    unittest.main()
