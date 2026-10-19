import json
from pathlib import Path
import unittest


ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/jurassicraft"


def read(path):
    return json.loads((ASSETS / path).read_text(encoding="utf-8"))


class FenceModelTests(unittest.TestCase):
    def test_only_powered_pole_bulbs_are_full_bright(self):
        active = read("models/block/low_security_fence_pole_active.json")
        inactive = read("models/block/low_security_fence_pole.json")
        lit = [element for element in active["elements"]
               if any(face.get("emissivity", 0) for face in element["faces"].values())]
        # The two light cubes come from the original pole_lights Tabula model.
        self.assertEqual({tuple(element["from"]) for element in lit},
                         {(8.0, 17.5, 6.2), (7.8, 18.0, 8.7)})
        for element in lit:
            self.assertEqual(set(element["faces"]), {"north", "south", "east", "west", "up", "down"})
            for face in element["faces"].values():
                self.assertEqual(face.get("emissivity"), 15)
                self.assertIs(face.get("ambientocclusion"), False)
        for element in active["elements"]:
            if element not in lit:
                self.assertTrue(all(face.get("emissivity", 0) == 0 for face in element["faces"].values()))
        self.assertTrue(all(face.get("emissivity", 0) == 0 for element in inactive["elements"]
                            for face in element["faces"].values()))
        parts = read("blockstates/low_security_fence_pole.json")["multipart"]
        self.assertIn({"when": {"active": "true"},
                       "apply": {"model": "jurassicraft:block/low_security_fence_pole_active"}}, parts)
        self.assertIn({"when": {"active": "false"},
                       "apply": {"model": "jurassicraft:block/low_security_fence_pole"}}, parts)


if __name__ == "__main__":
    unittest.main()
