"""Validate the assets loaded dynamically by the vehicle and tour-rail renderers."""

import json
import os
import re
import struct
import unittest
import zipfile
from pathlib import Path


MOD = Path(__file__).resolve().parents[2]
ASSETS = MOD / "src/main/resources/assets/jurassicraft"
JAVA = MOD / "src/main/java/ports/jurassicraft"
HUD_TEXTURES = (
    "tachometer", "tachometer_needle", "tachometer_pointer", "altimeter", "altimeter_needle",
    "altimeter_pointer", "artificial_horizon_second_layer", "artificial_horizon_needle",
    "artificial_horizon_pointer", "compass", "compass_needle",
)


def render_assets():
    vehicle_client = (JAVA / "vehicle/client/VehicleClient.java").read_text()
    rail_renderer = (JAVA / "tourrail/client/TourRailRenderer.java").read_text()
    vehicles = set(re.findall(r'new VehicleRenderer<>\(context,\s*"([a-z_]+)"\)', vehicle_client))
    families = set(re.findall(r'new Model\("([a-z_]+)",\s*\d+\)', rail_renderer))
    if not vehicles or not families:
        raise AssertionError("Could not find vehicle or tour rail renderers")
    for vehicle in vehicles:
        yield f"models/entity_port/{vehicle}.json", [f"textures/entities/{vehicle}/{vehicle}.png"]
    for family in families:
        yield f"models/entity_port/tour_rail_{family}.json", [
            f"textures/blocks/tour_rail_{family}.png",
            f"textures/blocks/tour_rail_{family}_stripe.png",
        ]


class VehicleAssetTests(unittest.TestCase):
    def test_helicopter_gauges_and_animated_parts_exist(self):
        model = json.loads((ASSETS / "models/entity_port/helicopter.json").read_text())
        def names(cube):
            return {cube.get("name")} | set().union(*(names(child) for child in cube["children"]))
        parts = set().union(*(names(cube) for cube in model["cubes"]))
        self.assertTrue({"rotorbase_rotatehere", "tailrotor_rotatehere", "gearL1", "gearR1", "gearFront1", "controlstick1"} <= parts)
        for name in HUD_TEXTURES:
            texture = ASSETS / f"textures/entities/helicopter/hud/{name}.png"
            header = texture.read_bytes()[:24]
            self.assertEqual(header[:8], b"\x89PNG\r\n\x1a\n", name)
            self.assertEqual(struct.unpack(">II", header[16:24]), (128, 128), name)

    def test_renderer_models_and_textures_exist_and_are_loadable(self):
        def check_cube(cube):
            for key, length in (("dimensions", 3), ("offset", 3), ("position", 3),
                                ("rotation", 3), ("scale", 3), ("txOffset", 2)):
                self.assertEqual(len(cube[key]), length, key)
                self.assertTrue(all(isinstance(x, (int, float)) for x in cube[key]), key)
            for child in cube["children"]:
                check_cube(child)

        for model_path, texture_paths in render_assets():
            with self.subTest(model=model_path):
                data = json.loads((ASSETS / model_path).read_text())
                self.assertEqual(len(data["scale"]), 3)
                self.assertGreater(data["textureWidth"], 0)
                self.assertGreater(data["textureHeight"], 0)
                self.assertTrue(data["cubes"])
                for cube in data["cubes"]:
                    check_cube(cube)
                for texture_path in texture_paths:
                    header = (ASSETS / texture_path).read_bytes()[:24]
                    self.assertEqual(header[:8], b"\x89PNG\r\n\x1a\n", texture_path)
                    width, height = struct.unpack(">II", header[16:24])
                    self.assertGreater(width, 0)
                    self.assertEqual(width * data["textureHeight"], height * data["textureWidth"], texture_path)

    def test_dynamic_renderer_assets_are_in_built_jar(self):
        path = os.environ.get("JURASSICRAFT_TEST_JAR")
        if not path:
            self.skipTest("Set JURASSICRAFT_TEST_JAR to test a built distribution")
        required = {"assets/jurassicraft/" + resource
                    for model, textures in render_assets() for resource in [model, *textures]}
        required.update(f"assets/jurassicraft/textures/entities/helicopter/hud/{name}.png" for name in HUD_TEXTURES)
        with zipfile.ZipFile(path) as jar:
            missing = sorted(required.difference(jar.namelist()))
        self.assertFalse(missing, f"Missing dynamic renderer assets: {missing}")


if __name__ == "__main__":
    unittest.main()
