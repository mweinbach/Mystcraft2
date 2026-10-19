import itertools
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
DATA = ROOT / 'src/main/resources/data/jurassicraft'


def load(path):
    return json.loads(path.read_text())


class DecorResourcesTest(unittest.TestCase):
    def test_connected_glass_has_model_for_every_neighbor_combination(self):
        for name in ('clear_glass', 'reinforced_glass'):
            with self.subTest(name=name):
                variants = load(ASSETS / 'blockstates' / f'{name}.json')['variants']
                expected = set(itertools.product(('false', 'true'), repeat=6))
                actual = set()
                for key, value in variants.items():
                    state = dict(part.split('=') for part in key.split(','))
                    actual.add(tuple(state[k] for k in ('down', 'east', 'north', 'south', 'up', 'west')))
                    model_path = ASSETS / 'models' / (value['model'].split(':', 1)[1] + '.json')
                    model = load(model_path)
                    self.assertEqual('minecraft:translucent', model['render_type'])
                    for texture in model['textures'].values():
                        if texture.startswith('jurassicraft:'):
                            self.assertTrue((ASSETS / 'textures' / (texture.split(':', 1)[1] + '.png')).exists())
                self.assertEqual(expected, actual)

    def test_glass_recipes_keep_upstream_yields(self):
        for name, output in [('clear_glass', 8), ('reinforced_glass', 8),
                             ('clear_glass_pane', 16), ('reinforced_glass_pane', 16)]:
            recipe = load(DATA / 'recipes' / f'{name}.json')
            self.assertEqual({'item': 'jurassicraft:' + name, 'count': output}, recipe['result'])
            self.assertEqual('minecraft:crafting_shaped', recipe['type'])

    def test_sign_overrides_and_textures_are_available(self):
        item = load(ASSETS / 'models/item/attraction_sign.json')
        self.assertEqual(17, len(item['overrides']))
        for override in item['overrides']:
            path = ASSETS / 'models' / (override['model'].split(':', 1)[1] + '.json')
            model = load(path)
            tex = model['textures']['layer0'].split(':', 1)[1]
            self.assertTrue((ASSETS / 'textures' / (tex + '.png')).exists())
            name = tex.rsplit('/', 1)[-1].removeprefix('attraction_sign_')
            for suffix in ('', '_popout'):
                self.assertTrue((ASSETS / 'textures/attraction_sign' / f'{name}{suffix}.png').exists())
        for name in ('attraction_sign', 'paddock_sign'):
            recipe = load(DATA / 'recipes' / f'{name}.json')
            self.assertEqual({'tag': 'minecraft:signs'}, recipe['key']['B'])


if __name__ == '__main__':
    unittest.main()
