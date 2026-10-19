"""Check GUI textures referenced directly by the DNA machine screen."""

import re
import struct
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src/main/resources/assets/jurassicraft/textures/gui"
SCREEN = ROOT / "src/main/java/ports/jurassicraft/machine/DnaMachineScreen.java"


class DnaMachineGuiTest(unittest.TestCase):
    def test_screen_textures_are_packaged_full_size_pngs(self):
        names = re.findall(r'texture\("([a-z_]+)"\)', SCREEN.read_text(encoding="utf-8"))
        self.assertEqual(set(names), {
            "dna_extractor", "dna_sequencer", "dna_combinator", "dna_hybridizer", "dna_synthesizer"
        })
        for name in names:
            with self.subTest(texture=name):
                data = (GUI / (name + ".png")).read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(data[12:16], b"IHDR")
                self.assertEqual(struct.unpack(">II", data[16:24]), (256, 256))
                original = ROOT / "upstream/src/main/resources/assets/jurassicraft/textures/gui" / (name + ".png")
                self.assertEqual(data, original.read_bytes())


if __name__ == "__main__":
    unittest.main()
