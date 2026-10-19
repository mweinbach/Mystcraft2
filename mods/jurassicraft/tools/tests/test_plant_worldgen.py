import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def read(path):
    return json.loads(path.read_text())


class PlantWorldgenResourcesTest(unittest.TestCase):
    def test_features_are_placed_once_per_overworld_chunk(self):
        for feature in ('petrified_tree', 'coal_plant_fossils'):
            with self.subTest(feature=feature):
                configured = read(DATA / 'worldgen/configured_feature' / f'{feature}.json')
                self.assertEqual({'type': 'jurassicraft:' + feature, 'config': {}}, configured)
                placed = read(DATA / 'worldgen/placed_feature' / f'{feature}.json')
                self.assertEqual('jurassicraft:' + feature, placed['feature'])
                self.assertEqual(['minecraft:in_square', 'minecraft:heightmap', 'minecraft:biome'],
                                 [entry['type'] for entry in placed['placement']])
                modifier = read(DATA / 'forge/biome_modifier' / f'{feature}.json')
                self.assertEqual('forge:add_features', modifier['type'])
                self.assertEqual('#minecraft:is_overworld', modifier['biomes'])
                self.assertEqual('underground_decoration' if feature == 'coal_plant_fossils' else 'underground_ores',
                                 modifier['step'])
                self.assertEqual('jurassicraft:' + feature, modifier['features'])

    def test_independent_plant_fossil_ore_is_removed(self):
        modifier = read(DATA / 'forge/biome_modifier/ores.json')
        self.assertNotIn('jurassicraft:plant_fossil_block', modifier['features'])
        for folder in ('configured_feature', 'placed_feature'):
            self.assertFalse((DATA / 'worldgen' / folder / 'plant_fossil_block.json').exists())

    def test_petrified_tree_replacement_is_restricted_to_natural_materials(self):
        values = set(read(DATA / 'tags/blocks/petrified_tree_replaceable.json')['values'])
        self.assertTrue({'#minecraft:base_stone_overworld', '#minecraft:dirt'} <= values)
        self.assertNotIn('minecraft:bedrock', values)
        self.assertNotIn('minecraft:water', values)
        self.assertNotIn('minecraft:chest', values)
        self.assertNotIn('#forge:ores', values)


if __name__ == '__main__':
    unittest.main()
