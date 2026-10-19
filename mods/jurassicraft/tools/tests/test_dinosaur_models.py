"""Check the packaged dinosaur models against the imported Tabula archives."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import import_dinosaur_models as source


class DinosaurModelsTest(unittest.TestCase):
    def load(self, stem):
        return json.loads((source.NEW / 'models/entity_port' / (stem + '.json')).read_text())

    def assert_geometry(self, output, original):
        self.assertEqual(output['textureWidth'], original['textureWidth'])
        self.assertEqual(output['textureHeight'], original['textureHeight'])
        self.assertEqual(output['cubes'], [source.geometry(c) for c in source.roots(original)])
        self.assertEqual(output['scale'], original.get('scale', [1, 1, 1]))

    def test_living_geometry_and_pose_frames(self):
        for species in source.SPECIES:
            with self.subTest(species=species):
                folder = source.OLD / 'models/entities' / species / 'adult'
                animation = json.loads((folder / (species + '_adult.json')).read_text())['poses']
                idle = source.model(folder / (species + '_adult_idle.tbl'))
                result = self.load(species)
                self.assert_geometry(result, idle)
                ids = set()
                for cube in source.roots(idle):
                    parts = {}
                    source.transforms(cube, parts)
                    ids.update(parts)
                for state in source.KINDS:
                    frames = animation.get(state, {}).get('variants', {}).get('0', [])
                    if not frames:
                        self.assertNotIn(state, result['animations'])
                        continue
                    self.assertEqual(len(frames), len(result['animations'][state]))
                    for frame, converted in zip(frames, result['animations'][state]):
                        self.assertGreater(frame['time'], 0)
                        self.assertEqual(converted['time'], frame['time'])
                        pose = source.model(folder / (frame['pose'] + '.tbl'))
                        transforms = {}
                        for cube in source.roots(pose):
                            source.transforms(cube, transforms)
                        self.assertEqual(converted['parts'], {k: v for k, v in transforms.items() if k in ids})
                    for variant, extra in animation[state]['variants'].items():
                        if variant == '0' or not extra:
                            continue
                        self.assertEqual([frame['time'] for frame in extra],
                                         [frame['time'] for frame in result['animations'][f'{state}:{variant}']])
                for gender in ('male', 'female'):
                    self.assertTrue((source.NEW / 'textures/entities' / species / f'{species}_{gender}_adult.png').is_file())

    def test_skeleton_and_egg_geometry(self):
        for species in source.SPECIES:
            with self.subTest(species=species):
                skeleton = source.model(source.OLD / 'models/entities' / species / 'skeleton' / f'{species}_skeleton_idle.tbl')
                self.assert_geometry(self.load(species + '_skeleton'), skeleton)
                self.assertTrue((source.NEW / 'textures/entities' / species / f'{species}_male_skeleton.png').is_file())
                if species != 'coelacanth':
                    egg = source.model(source.OLD / 'models/entities/egg' / (species + '.tbl'))
                    self.assert_geometry(self.load(species + '_egg'), egg)
                    self.assertTrue((source.NEW / 'textures/entities/egg' / f'{species}.png').is_file())
        for pose in ('death', 'attacking'):
            skeleton = source.model(source.OLD / 'models/entities/tyrannosaurus/skeleton' / f'tyrannosaurus_skeleton_{pose}.tbl')
            self.assert_geometry(self.load('tyrannosaurus_skeleton_' + pose), skeleton)

    def test_sound_files_exist(self):
        manifest = json.loads((source.NEW / 'sounds.json').read_text())
        for species in source.SPECIES:
            if species == 'coelacanth':
                continue
            for kind in ('ambient', 'hurt', 'death'):
                for name in manifest[f'entity.{species}.{kind}']['sounds']:
                    self.assertTrue((source.NEW / 'sounds' / name.split(':', 1)[1]).with_suffix('.ogg').is_file(), name)

    def test_species_food_resources(self):
        names = json.loads((source.ROOT / 'tools/lang/dinosaurs.json').read_text())
        recipes = source.ROOT / 'src/main/resources/data/jurassicraft/recipes'
        for species in source.SPECIES:
            with self.subTest(species=species):
                for kind in ('meat', 'steak'):
                    path = f'item_port/{kind}/{species}'
                    texture = (source.NEW / 'textures' / path).with_suffix('.png')
                    original = source.OLD / 'textures/items' / kind / f'{species}.png'
                    self.assertEqual(texture.read_bytes(), original.read_bytes())
                    item = f'{species}_{kind}'
                    model = json.loads((source.NEW / 'models/item' / f'{item}.json').read_text())
                    self.assertEqual(model['textures']['layer0'], f'jurassicraft:{path}')
                    self.assertIn(f'item.jurassicraft.{item}', names)
                for method, time in (('smelting', 200), ('smoking', 100)):
                    recipe = json.loads((recipes / f'{species}_steak_from_{method}.json').read_text())
                    self.assertEqual(recipe['type'], f'minecraft:{method}')
                    self.assertEqual(recipe['ingredient']['item'], f'jurassicraft:{species}_meat')
                    self.assertEqual(recipe['result'], f'jurassicraft:{species}_steak')
                    self.assertEqual(recipe['cookingtime'], time)


if __name__ == '__main__':
    unittest.main()
