"""Opt-in distribution checks; point JURASSICRAFT_TEST_JAR to the built jar."""

import json
import os
import unittest
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src/main/resources"
JAR = os.environ.get("JURASSICRAFT_TEST_JAR")


@unittest.skipUnless(JAR, "Set JURASSICRAFT_TEST_JAR to test a built distribution")
class BuiltJarTests(unittest.TestCase):
    def test_resources_and_attribution_are_in_the_distribution(self):
        with ZipFile(JAR) as jar:
            self.assertIsNone(jar.testzip())
            names = set(jar.namelist())
            for required in ("META-INF/JurassiCraft-UPSTREAM.md", "META-INF/UPSTREAM-LICENSE-HISTORICAL.md",
                             "ports/jurassicraft/PortMod.class"):
                self.assertTrue(required in names, f"Missing required archive entry: {required}")
            self.assertFalse(any(name.startswith("upstream/") for name in names))
            pack = json.loads(jar.read("pack.mcmeta"))
            self.assertEqual(pack["pack"]["pack_format"], 9)
            # Template files undergo Gradle token expansion; resource assets and
            # data must otherwise be identical to the reviewed source tree.
            for subtree in ("assets", "data"):
                for path in (RESOURCES / subtree).rglob("*"):
                    if path.is_file():
                        relative = path.relative_to(RESOURCES).as_posix()
                        with self.subTest(resource=relative):
                            self.assertTrue(relative in names, f"Missing resource in archive: {relative}")
                            self.assertEqual(path.read_bytes(), jar.read(relative))


if __name__ == "__main__":
    unittest.main()
