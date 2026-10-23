"""Opt-in distribution checks; point JURASSICRAFT_TEST_JAR to either loader's built jar."""

import json
import os
import unittest
from zipfile import ZipFile

from layout import OVERLAYS, SHARED

JAR = os.environ.get("JURASSICRAFT_TEST_JAR")


@unittest.skipUnless(JAR, "Set JURASSICRAFT_TEST_JAR to test a built distribution")
class BuiltJarTests(unittest.TestCase):
    def test_resources_and_attribution_are_in_the_distribution(self):
        with ZipFile(JAR) as jar:
            self.assertIsNone(jar.testzip())
            entries = jar.namelist()
            self.assertEqual(len(entries), len(set(entries)), "duplicate zip entries")
            names = set(entries)
            for required in ("META-INF/JurassiCraft-UPSTREAM.md", "META-INF/UPSTREAM-LICENSE-HISTORICAL.md",
                             "ports/jurassicraft/PortMod.class"):
                self.assertTrue(required in names, f"Missing required archive entry: {required}")
            self.assertFalse(any(name.startswith("upstream/") for name in names))
            pack = json.loads(jar.read("pack.mcmeta"))
            self.assertEqual(pack["pack"]["pack_format"], 9)
            loader = "fabric" if "fabric.mod.json" in names else "forge"
            # Template files undergo Gradle token expansion; resource assets and data must
            # otherwise be identical to the shared tree with the loader's overlay applied.
            for root in (SHARED, OVERLAYS[loader]):
                for subtree in ("assets", "data"):
                    for path in (root / subtree).rglob("*"):
                        relative = path.relative_to(root).as_posix()
                        if path.is_file() and (root != SHARED or not (OVERLAYS[loader] / relative).is_file()):
                            with self.subTest(resource=relative):
                                self.assertTrue(relative in names, f"Missing resource in archive: {relative}")
                                self.assertEqual(path.read_bytes(), jar.read(relative))

    def test_loader_descriptor_and_entrypoints(self):
        with ZipFile(JAR) as jar:
            names = set(jar.namelist())
            if "fabric.mod.json" not in names:
                self.assertIn("META-INF/mods.toml", names)
                self.assertIn("META-INF/accesstransformer.cfg", names)
                return
            self.assertNotIn("META-INF/mods.toml", names)
            self.assertNotIn("META-INF/accesstransformer.cfg", names)
            metadata = json.loads(jar.read("fabric.mod.json"))
            self.assertEqual(metadata["id"], "jurassicraft")
            self.assertNotIn("${", metadata["version"])
            self.assertIn("fabricloader", metadata["depends"])
            self.assertIn("fabric-api", metadata["depends"])
            self.assertIn("1.19.2", str(metadata["depends"]["minecraft"]))
            self.assertNotIn("fabric-gametest", metadata.get("entrypoints", {}))
            self.assertIn(metadata["icon"], names, "mod icon must ship in the jar")
            self.assertTrue({"homepage", "sources", "issues"} <= set(metadata["contact"]))
            self.assertEqual({"jade", "emi"}, set(metadata["suggests"]))
            # The upstream license is unresolved; the descriptor must keep saying so.
            self.assertEqual("LicenseRef-Unknown", metadata["license"])


if __name__ == "__main__":
    unittest.main()
