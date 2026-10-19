"""Audit a remapped Fabric distribution with JURASSICRAFT_FABRIC_TEST_JAR set."""

import json
import os
import unittest
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
JAR = os.environ.get("JURASSICRAFT_FABRIC_TEST_JAR")


@unittest.skipUnless(JAR, "Set JURASSICRAFT_FABRIC_TEST_JAR to the remapped Fabric jar")
class FabricJarTests(unittest.TestCase):
    def test_loader_metadata_and_production_only_classes(self):
        with ZipFile(JAR) as jar:
            self.assertIsNone(jar.testzip())
            names = jar.namelist()
            self.assertEqual(len(names), len(set(names)), "duplicate zip entries")
            self.assertIn("fabric.mod.json", names)
            self.assertIn("ports/jurassicraft/PortMod.class", names)
            self.assertNotIn("META-INF/mods.toml", names)
            self.assertNotIn("META-INF/accesstransformer.cfg", names)
            self.assertFalse(any(name.startswith(("upstream/", "net/minecraftforge/",
                                                   "ports/jurassicraft/gametest/"))
                                 or name.endswith("GameTests.class") for name in names),
                             "Forge, upstream or GameTest classes must not be packaged")
            metadata = json.loads(jar.read("fabric.mod.json"))
            self.assertEqual(metadata["id"], "jurassicraft")
            self.assertNotIn("${", metadata["version"])
            self.assertIn("fabricloader", metadata["depends"])
            self.assertIn("fabric-api", metadata["depends"])
            self.assertIn("1.19.2", str(metadata["depends"]["minecraft"]))
            self.assertNotIn("fabric-gametest", metadata.get("entrypoints", {}))

    def test_packaged_assets_and_data_match_source(self):
        with ZipFile(JAR) as jar:
            names = set(jar.namelist())
            for subtree in ("assets", "data"):
                directory = RESOURCES / subtree
                self.assertTrue(directory.is_dir(), f"missing source {subtree}")
                files = [path for path in directory.rglob("*") if path.is_file()]
                self.assertTrue(files, f"no source {subtree}")
                for path in files:
                    relative = path.relative_to(RESOURCES).as_posix()
                    with self.subTest(resource=relative):
                        self.assertTrue(relative in names, f"missing packaged resource: {relative}")
                        self.assertEqual(path.read_bytes(), jar.read(relative))


if __name__ == "__main__":
    unittest.main()
