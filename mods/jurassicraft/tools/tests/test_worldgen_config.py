"""Check the independent feature and config filter are packaged and reproducible."""
import json
import unittest
from pathlib import Path

from layout import OVERLAYS, placements

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def read(path):
    return json.loads(path.read_text())


class WorldgenConfigResourcesTest(unittest.TestCase):
    def test_filter_and_independent_trackways_are_connected(self):
        self.assertEqual({'type': 'jurassicraft:biome_additions'},
                         read(OVERLAYS['forge'] / 'data/jurassicraft/forge/biome_modifier/biome_additions.json'))
        self.assertEqual({'type': 'jurassicraft:fossil_trackway', 'config': {}},
                         read(DATA / 'worldgen/configured_feature/fossil_trackway.json'))
        placed = read(DATA / 'worldgen/placed_feature/fossil_trackway.json')
        self.assertEqual('jurassicraft:fossil_trackway', placed['feature'])
        self.assertEqual(['minecraft:in_square', 'minecraft:height_range', 'minecraft:biome'],
                         [step['type'] for step in placed['placement']])
        height = placed['placement'][1]['height']
        self.assertEqual({'absolute': 30}, height['min_inclusive'])
        self.assertEqual({'absolute': 49}, height['max_inclusive'])
        self.assertEqual(('#minecraft:is_overworld', 'underground_ores'), placements()['fossil_trackway'])

    def test_existing_generation_uses_exact_named_features(self):
        expected = {'amber_ore', 'ice_shard', 'gypsum_stone', 'petrified_tree', 'coal_plant_fossils',
                    'moss', 'peat', 'west_indian_lilac', 'gracilaria', 'fossil_nest', 'fossil_trackway'}
        self.assertTrue(expected <= set(placements()))
        for name in placements():
            with self.subTest(feature=name):
                placed = name.replace('{species}', 'tyrannosaurus')
                self.assertTrue((DATA / 'worldgen/placed_feature' / f'{placed}.json').is_file())


if __name__ == '__main__':
    unittest.main()
