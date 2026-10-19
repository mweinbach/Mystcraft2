import json
import re
import unittest
from pathlib import Path


MOD = Path(__file__).resolve().parents[2]
PREFIX = "gui.jurassicraft.field_guide."
FRAGMENT = MOD / "tools/field_guide/en_us.fragment.json"
LANGUAGE = MOD / "src/main/resources/assets/jurassicraft/lang/en_us.json"


class FieldGuideResourcesTest(unittest.TestCase):
    def test_every_visible_spread_and_status_is_translated(self):
        fragment = json.loads(FRAGMENT.read_text(encoding="utf8"))
        language = json.loads(LANGUAGE.read_text(encoding="utf8"))
        screen = (MOD / "src/main/java/ports/jurassicraft/fieldguide/client/FieldGuideScreen.java").read_text()
        contents = re.search(r"SPREADS\s*=\s*List\.of\((.*?)\);", screen, re.S).group(1)
        for spread in re.findall(r'"([a-z_]+)"', contents):
            for side in ("left", "right"):
                for part in ("title", "body"):
                    key = PREFIX + f"{spread}.{side}.{part}"
                    with self.subTest(key=key):
                        self.assertTrue(fragment.get(key), f"missing generator fragment: {key}")
                        self.assertEqual(language.get(key), fragment[key], f"unmerged guide content: {key}")
        for key, value in fragment.items():
            with self.subTest(key=key):
                self.assertEqual(language.get(key), value, f"unmerged guide translation: {key}")

    def test_species_notes_preserve_upstream_text(self):
        fragment = json.loads(FRAGMENT.read_text(encoding="utf8"))
        source = MOD / "upstream/src/main/resources/assets/jurassicraft/lang/en_US.lang"
        original = dict(line.split("=", 1) for line in source.read_text(encoding="utf8").splitlines() if "=" in line)
        species = [key.removeprefix(PREFIX + "species.") for key in fragment if key.startswith(PREFIX + "species.")]
        self.assertEqual(len(species), 10)
        for name in species:
            with self.subTest(species=name):
                text = fragment[PREFIX + "species." + name]
                self.assertGreater(len(text), 200)
                self.assertEqual(text, original[f"info.jurassicraft.{name}.name"].replace("\ufffd", '"'))


if __name__ == "__main__":
    unittest.main()
