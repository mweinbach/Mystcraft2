"""The packaged procedural-motion tables stay tied to the animator transcription."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import procedural_motion as source


class ProceduralMotionTest(unittest.TestCase):
    def test_packaged_tables_match_the_animator_transcription(self):
        motion, names = source.build()
        packaged_motion = json.loads((source.OUT / 'procedural_motion.json').read_text())
        packaged_names = json.loads((source.OUT / 'cube_names.json').read_text())
        self.assertEqual(packaged_motion, motion)
        self.assertEqual(packaged_names, names)
        for species, mapping in names.items():
            model = json.loads((source.OUT / f'{species}.json').read_text())
            identifiers = model['animations']['IDLE'][0]['parts']
            for name, identifier in mapping.items():
                self.assertIn(identifier, identifiers, f'{species} {name}')
            # The brachiosaurus tip is the later cube that reuses the name tail5.
            if species == 'brachiosaurus':
                self.assertTrue(mapping['tail5'])


if __name__ == '__main__':
    unittest.main()
