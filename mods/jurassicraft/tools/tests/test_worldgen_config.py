"""Check the independent feature and config filter are packaged and reproducible."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def read(path):
    return json.loads(path.read_text())


class WorldgenConfigResourcesTest(unittest.TestCase):
    def test_filter_and_independent_trackways_are_connected(self):
        self.assertEqual({'type': 'jurassicraft:worldgen_filter'}, read(DATA / 'forge/biome_modifier/worldgen_filter.json'))
        self.assertEqual({'type': 'jurassicraft:fossil_trackway', 'config': {}},
                         read(DATA / 'worldgen/configured_feature/fossil_trackway.json'))
        placed = read(DATA / 'worldgen/placed_feature/fossil_trackway.json')
        self.assertEqual('jurassicraft:fossil_trackway', placed['feature'])
        self.assertEqual(['minecraft:in_square', 'minecraft:height_range', 'minecraft:biome'],
                         [step['type'] for step in placed['placement']])
        height = placed['placement'][1]['height']
        self.assertEqual({'absolute': 30}, height['min_inclusive'])
        self.assertEqual({'absolute': 49}, height['max_inclusive'])
        modifier = read(DATA / 'forge/biome_modifier/fossil_trackway.json')
        self.assertEqual('forge:add_features', modifier['type'])
        self.assertEqual('#minecraft:is_overworld', modifier['biomes'])
        self.assertEqual('jurassicraft:fossil_trackway', modifier['features'])
        self.assertEqual('underground_ores', modifier['step'])

    def test_existing_generation_uses_exact_named_features(self):
        expected = {'amber_ore', 'ice_shard', 'gypsum_stone', 'petrified_tree', 'coal_plant_fossils',
                    'moss', 'peat', 'west_indian_lilac', 'gracilaria', 'fossil_nest', 'fossil_trackway'}
        features = set()
        for path in (DATA / 'forge/biome_modifier').glob('*.json'):
            modifier = read(path)
            if modifier['type'] != 'forge:add_features':
                continue
            value = modifier['features']
            features.update(value if isinstance(value, list) else [value])
        self.assertTrue({'jurassicraft:' + name for name in expected} <= features)


if __name__ == '__main__':
    unittest.main()
