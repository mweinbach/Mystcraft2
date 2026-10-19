"""Checks the plant content's generated resources and progression links."""
import json
import unittest
from pathlib import Path

from layout import packaged_everywhere, placements


def model_exists(location):
    return packaged_everywhere(f"assets/jurassicraft/models/{location.split(':')[1]}.json")

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data'
TREES = ('ginkgo', 'calamites', 'psaronius', 'phoenix', 'araucaria')
CROPS = {'ajuginucula_smithii': 7, 'wild_onion_plant': 6, 'wild_potato_plant': 5,
         'rhamnus_salicifolius': 7}


def read(path):
    return json.loads(path.read_text())


def references(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key == 'model' and isinstance(child, str):
                yield child
            yield from references(child)
    elif isinstance(value, list):
        for child in value:
            yield from references(child)


class PlantResourcesTest(unittest.TestCase):
    def test_model_and_texture_references_exist(self):
        missing = []
        for path in (ASSETS / 'models').rglob('*.json'):
            model = read(path)
            parent = model.get('parent', '')
            if parent.startswith('jurassicraft:') and not model_exists(parent):
                missing.append((path, parent))
            for tex in model.get('textures', {}).values():
                if tex.startswith('jurassicraft:') and not (ASSETS / 'textures' / (tex.split(':')[1] + '.png')).exists():
                    missing.append((path, tex))
        for path in (ASSETS / 'blockstates').rglob('*.json'):
            for model in references(read(path)):
                if model.startswith('jurassicraft:') and not model_exists(model):
                    missing.append((path, model))
        self.assertEqual([], missing)

    def test_tree_content_and_foliage_drops(self):
        tags = read(DATA / 'minecraft/tags/blocks/logs.json')['values']
        for tree in TREES:
            self.assertIn(f'#jurassicraft:{tree}_logs', tags)
            for suffix in ('log', 'log_petrified', 'planks', 'leaves', 'sapling',
                           'stairs', 'slab', 'fence', 'fence_gate', 'door'):
                name = f'{tree}_{suffix}'
                for base in (ASSETS / 'blockstates', ASSETS / 'models/item', DATA / 'jurassicraft/loot_tables/blocks'):
                    self.assertTrue((base / (name + '.json')).is_file(), str(base / name))
            leaf = read(DATA / 'jurassicraft/loot_tables/blocks' / (tree + '_leaves.json'))
            children = leaf['pools'][0]['entries'][0]['children']
            expected = 'phoenix_fruit' if tree == 'phoenix' else tree + '_sapling'
            self.assertEqual('jurassicraft:' + expected, children[1]['name'])
            self.assertEqual('minecraft:table_bonus', children[1]['conditions'][1]['condition'])
            self.assertEqual(40, len(read(ASSETS / 'blockstates' / (tree + '_stairs.json'))['variants']))
            self.assertEqual(32, len(read(ASSETS / 'blockstates' / (tree + '_door.json'))['variants']))

    def test_crop_growing_and_harvest_states(self):
        for crop, maximum in CROPS.items():
            states = read(ASSETS / 'blockstates' / (crop + '.json'))['variants']
            self.assertEqual({f'age={i}' for i in range(8)}, set(states))
            loot = read(DATA / 'jurassicraft/loot_tables/blocks' / (crop + '.json'))
            first = loot['pools'][0]['entries'][0]['children']
            self.assertEqual(str(maximum), first[0]['conditions'][0]['properties']['age'])
            self.assertTrue(first[1]['name'].startswith('jurassicraft:'))

    def test_food_chain_and_lilac_growth_models(self):
        recipes = DATA / 'jurassicraft/recipes'
        oil = read(recipes / 'ajuginucula_smithii_oil.json')
        self.assertEqual(4, sum(x.get('item') == 'jurassicraft:ajuginucula_smithii_leaves' for x in oil['ingredients']))
        self.assertIn({'item': 'minecraft:glass_bottle'}, oil['ingredients'])
        for source in ('wild', 'potato'):
            strips = read(recipes / f'oiled_potato_strips_{source}.json')
            self.assertIn({'item': 'jurassicraft:ajuginucula_smithii_oil'}, strips['ingredients'])
            self.assertEqual('jurassicraft:oiled_potato_strips', strips['result']['item'])
        fried = read(recipes / 'fun_fries.json')
        self.assertEqual({'item': 'jurassicraft:oiled_potato_strips'}, fried['ingredient'])
        self.assertEqual('jurassicraft:fun_fries', fried['result'])
        states = read(ASSETS / 'blockstates/west_indian_lilac.json')['variants']
        self.assertEqual({f'age={age},half={half}' for age in range(3) for half in ('lower', 'upper')}, set(states))
        self.assertTrue(states['age=2,half=upper']['model'].endswith('west_indian_lilac_fruit_top'))

    def test_bale_recipes_use_obtainable_plant_items(self):
        recipes = list((DATA / 'jurassicraft/recipes').glob('paleo_bale_*_from_*.json'))
        self.assertGreaterEqual(len(recipes), 25)
        for path in recipes:
            data = read(path)
            ingredient = data['key']['A']['item'].split(':')[1]
            self.assertTrue((ASSETS / 'models/item' / (ingredient + '.json')).exists(), ingredient)
            result = data['result']['item'].split(':')[1]
            self.assertTrue((ASSETS / 'models/item' / (result + '.json')).exists(), result)
        tag = read(DATA / 'jurassicraft/tags/items/ancient_feeder_plants.json')['values']
        for variant in ('cycadeoidea', 'cycad', 'fern', 'leaves', 'other'):
            self.assertIn('jurassicraft:paleo_bale_' + variant, tag)

    def test_double_plant_drops_once_and_ocean_gracilaria_exists(self):
        loot = read(DATA / 'jurassicraft/loot_tables/blocks/dicksonia.json')
        self.assertEqual('lower', loot['pools'][0]['conditions'][0]['properties']['half'])
        loot = read(DATA / 'jurassicraft/loot_tables/blocks/gracilaria_seaweed.json')
        self.assertEqual('jurassicraft:gracilaria', loot['pools'][0]['entries'][0]['name'])
        self.assertEqual(('#minecraft:is_ocean', 'vegetal_decoration'), placements()['gracilaria'])


if __name__ == '__main__':
    unittest.main()
