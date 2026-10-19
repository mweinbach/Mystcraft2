"""Jade requires a translated configuration label for each provider ID."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class JadeResourcesTest(unittest.TestCase):
    def test_provider_config_labels_are_in_the_packaged_language(self):
        source = (ROOT / "src/main/java/ports/jurassicraft/compat/jade/ServerProviders.java").read_text()
        provider_ids = set(re.findall(r'id\("([a-z_]+)"\)', source))
        self.assertTrue(provider_ids, "No Jade provider IDs were found")
        fragment = json.loads((ROOT / "tools/lang/jade.json").read_text())
        packaged = json.loads((ROOT / "src/main/resources/assets/jurassicraft/lang/en_us.json").read_text())
        for provider_id in provider_ids:
            key = f"config.jade.plugin_jurassicraft.{provider_id}"
            self.assertTrue(fragment.get(key), f"Missing Jade configuration label: {key}")
            self.assertEqual(fragment[key], packaged.get(key), f"Unmerged Jade configuration label: {key}")
        for key, value in fragment.items():
            self.assertEqual(value, packaged.get(key), f"Unmerged Jade translation: {key}")


if __name__ == "__main__":
    unittest.main()
