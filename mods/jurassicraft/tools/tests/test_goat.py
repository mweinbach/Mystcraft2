import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import import_dinosaur_models as source


class GoatResourcesTest(unittest.TestCase):
    def test_original_models_and_textures(self):
        for kind in ('billy', 'nanny', 'kid'):
            with self.subTest(kind=kind):
                name = 'goat_' + kind
                folder = source.OLD / 'models/entities' / name / 'adult'
                original = source.model(folder / (name + '.tbl'))
                output = json.loads((source.NEW / 'models/entity_port' / (name + '.json')).read_text())
                self.assertEqual(output['cubes'], [source.geometry(c) for c in source.roots(original)])
                self.assertEqual(output['scale'], original.get('scale', [1, 1, 1]))
                for state in ('IDLE', 'WALKING', 'RUNNING'):
                    self.assertTrue(output['animations'][state])
                for variant in ('jurassic_park', 'jurassic_world', 'jpog'):
                    path = Path('textures/entities/goat') / f'{kind}_{variant}.png'
                    self.assertEqual((source.OLD / path).read_bytes(), (source.NEW / path).read_bytes())

    def test_meat_recipes_and_sounds(self):
        data = ROOT / 'src/main/resources/data/jurassicraft'
        for method in ('smelting', 'smoking', 'campfire_cooking'):
            recipe = json.loads((data / 'recipes' / f'goat_cooked_{method}.json').read_text())
            self.assertEqual({'item': 'jurassicraft:goat_raw'}, recipe['ingredient'])
            self.assertEqual('jurassicraft:goat_cooked', recipe['result'])
        loot = json.loads((data / 'loot_tables/entities/goat.json').read_text())
        serialized = json.dumps(loot)
        for item in ('goat_raw', 'leather', 'white_wool', 'brown_wool', 'furnace_smelt'):
            self.assertIn(item, serialized)
        sounds = json.loads((ROOT / 'tools/sounds/goat.json').read_text())
        for event in ('goat_living', 'goat_hurt', 'goat_death'):
            self.assertTrue(sounds[event]['sounds'])
            for location in sounds[event]['sounds']:
                self.assertTrue((source.NEW / 'sounds' / (location.split(':', 1)[1] + '.ogg')).is_file())

    def test_optional_pig_biome_modifier_and_food_tag_exist(self):
        data = ROOT / 'src/main/resources/data/jurassicraft'
        modifier = json.loads((data / 'forge/biome_modifier/goat_spawns.json').read_text())
        self.assertEqual({'type': 'jurassicraft:goat_spawns'}, modifier)
        food = json.loads((data / 'tags/items/goat_food.json').read_text())
        self.assertIn('#jurassicraft:feeder_plants', food['values'])
        self.assertIn('minecraft:carrot', food['values'])


if __name__ == '__main__':
    unittest.main()
