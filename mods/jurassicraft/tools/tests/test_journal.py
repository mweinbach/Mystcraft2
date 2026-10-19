import json
import unittest
from pathlib import Path


MOD = Path(__file__).resolve().parents[2]
ASSETS = MOD / "src/main/resources/assets/jurassicraft"
UPSTREAM = MOD / "upstream/src/main/resources/assets/jurassicraft"
AUTHORS = {
    "chef_alejandro": 7, "dennis_nedry": 7, "dr_gerry_harding": 10, "dr_henry_wu": 10,
    "dr_laura_sorkin": 14, "ed_regis": 8, "john_hammond": 13, "ray_arnold": 8,
    "robert_muldoon": 6,
}


class JournalResourcesTest(unittest.TestCase):
    def test_original_entries_and_authors(self):
        for author, entry_count in AUTHORS.items():
            with self.subTest(author=author):
                path = ASSETS / "journal_entries" / (author + ".json")
                self.assertEqual(path.read_bytes(), (UPSTREAM / "journal_entries" / path.name).read_bytes())
                data = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(data["identifier"], "jurassicraft:" + author)
                self.assertEqual(len(data["entries"]), entry_count)
                self.assertTrue(all(isinstance(line, str) for entry in data["entries"] for line in entry))

    def test_original_artwork(self):
        for path in ("textures/journal/background.png", "textures/journal/widgets.png"):
            self.assertEqual((ASSETS / path).read_bytes(), (UPSTREAM / path).read_bytes())
        self.assertEqual((ASSETS / "textures/item/ingen_journal.png").read_bytes(),
                         (UPSTREAM / "textures/items/ingen_journal.png").read_bytes())


if __name__ == "__main__":
    unittest.main()
