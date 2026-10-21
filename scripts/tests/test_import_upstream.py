"""The Mekanism module importer copies one upstream module and leaves port-owned files alone."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("import_upstream", Path(__file__).resolve().parents[1] / "import_upstream.py")
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class ImportUpstreamTests(unittest.TestCase):
    def test_copies_sources_datagen_and_descriptors_and_skips_jei(self):
        sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(sandbox.cleanup)
        root = Path(sandbox.name)
        upstream = root / "Mekanism"
        write(upstream / "src/generators/java/mekanism/generators/common/Kept.java",
              "package mekanism.generators.common;\npublic class Kept {}\n")
        write(upstream / "src/generators/java/mekanism/generators/client/jei/Plugin.java",
              "package mekanism.generators.client.jei;\nimport mezz.jei.api.IModPlugin;\npublic class Plugin {}\n")
        write(upstream / "src/generators/resources/pack.mcmeta",
              '{"pack":{"pack_format":${pack_format},"forge:resource_pack_format":${resource_pack_format},'
              '"forge:data_pack_format":${data_pack_format}}}\n')
        write(upstream / "src/generators/resources/META-INF/mods.toml",
              'modLoader="javafml"\nloaderVersion="${loader_version}"\nversion="${version}"\n')
        write(upstream / "src/generators/resources/data/mekanismgenerators/recipes/solar.json", "{}\n")
        write(upstream / "src/datagen/generated/mekanismgenerators/data/mekanismgenerators/loot_tables/blocks/solar.json",
              "{}\n")
        write(upstream / "src/datagen/generated/mekanismgenerators/.cache/skip.json", "{}\n")
        destination = root / "out"
        write(destination / "resources/data/mekanismgenerators/pe_custom_conversions/mekanismgenerators.json",
              '{"kept":true}\n')
        write(destination / "fabric/java/Keep.java", "class Keep {}\n")

        result = importer.import_module(upstream, "generators", destination)

        self.assertEqual(result["java"], 1)
        self.assertEqual(result["skipped"], ["mekanism/generators/client/jei/Plugin.java"])
        self.assertEqual(result["datagen"], 1)
        self.assertTrue((destination / "java/mekanism/generators/common/Kept.java").is_file())
        self.assertFalse((destination / "java/mekanism/generators/client/jei/Plugin.java").exists())
        pack = json.loads((destination / "resources/pack.mcmeta").read_text())
        self.assertEqual(pack["pack"]["pack_format"], 10)
        toml = (destination / "forge/resources/META-INF/mods.toml").read_text()
        self.assertIn('loaderVersion="[43,44)"', toml)
        self.assertIn("${mod_version}", toml)
        self.assertFalse((destination / "resources/META-INF/mods.toml").exists())
        self.assertTrue((destination / "resources/data/mekanismgenerators/loot_tables/blocks/solar.json").is_file())
        self.assertFalse(list((destination / "resources").rglob(".cache")))
        self.assertEqual((destination / "resources/data/mekanismgenerators/pe_custom_conversions/mekanismgenerators.json").read_text(),
                         '{"kept":true}\n')
        self.assertTrue((destination / "fabric/java/Keep.java").is_file())

    def test_unknown_module_is_rejected(self):
        with self.assertRaises(ValueError):
            importer.import_module(Path("/tmp/does-not-matter"), "defense")
