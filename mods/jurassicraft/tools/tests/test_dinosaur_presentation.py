"""Verify imported running poses and the original dinosaur presentation sounds."""
import json
import re
import sys
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/jurassicraft'
UPSTREAM = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
sys.path.insert(0, str(ROOT / 'tools'))
import import_dinosaur_models as source


class DinosaurPresentationResourcesTest(unittest.TestCase):
    def test_running_frames_are_available_with_safe_fallback(self):
        for species in ('velociraptor', 'microraptor', 'mussaurus', 'dilophosaurus',
                        'parasaurolophus', 'triceratops', 'gallimimus', 'tyrannosaurus'):
            with self.subTest(species=species):
                model = json.loads((ASSETS / 'models/entity_port' / f'{species}.json').read_text())
                self.assertTrue(model['animations']['RUNNING'])
                self.assertTrue(model['animations']['WALKING'])
        # The renderer must fall back to WALKING for species without a running timeline.
        brachiosaurus = json.loads((ASSETS / 'models/entity_port/brachiosaurus.json').read_text())
        self.assertNotIn('RUNNING', brachiosaurus['animations'])
        self.assertTrue(brachiosaurus['animations']['WALKING'])

    def test_look_chains_match_the_original_animators(self):
        chains = json.loads((ASSETS / 'models/entity_port/head_tracking.json').read_text())
        self.assertEqual(set(chains), set(source.LOOK_CHAINS))
        upstream_java = ROOT / 'upstream/src/main/java/org/jurassicraft/client/model/animation/entity'
        for species, (divisor, names) in source.LOOK_CHAINS.items():
            with self.subTest(species=species):
                java = (upstream_java / f'{species.capitalize()}Animator.java').read_text()
                declarations = dict(re.findall(r'AdvancedModelRenderer\s+(\w+)\s*=\s*model\.getCube\("([^"]+)"\)', java))
                call = re.search(r'model\.faceTarget\(rotationYaw,\s*rotationPitch,\s*([\d.]+)F,\s*([^;]+?)\);', java)
                self.assertIsNotNone(call)
                self.assertEqual(divisor, float(call.group(1)))
                self.assertEqual(names, tuple(declarations[v.strip()] for v in call.group(2).split(',')))
                idle = source.model(UPSTREAM / 'models/entities' / species / 'adult' / f'{species}_adult_idle.tbl')
                identifiers = {}
                def collect(cube):
                    identifiers[cube.get('name')] = cube['identifier']
                    for child in cube.get('children', []):
                        collect(child)
                for cube in source.roots(idle):
                    collect(cube)
                self.assertEqual(chains[species], {'divisor': divisor, 'parts': [identifiers[name] for name in names]})
                model = json.loads((ASSETS / 'models/entity_port' / f'{species}.json').read_text())
                self.assertTrue(all(identifier in model['animations']['IDLE'][0]['parts']
                                    for identifier in chains[species]['parts']))

    def test_extra_sounds_are_original_and_packaged(self):
        original = json.loads((UPSTREAM / 'sounds.json').read_text())
        fragment = json.loads((ROOT / 'tools/sounds/dinosaurs.json').read_text())
        packaged = json.loads((ASSETS / 'sounds.json').read_text())
        mappings = {
            'entity.velociraptor.breathing': 'velociraptor_breathing',
            'entity.velociraptor.call': 'velociraptor_call',
            'entity.mussaurus.attack': 'mussaurus_attack',
            'entity.parasaurolophus.call': 'parasaurolophus_call',
            **{f'entity.tyrannosaurus.{kind}': f'tyrannosaurus_{kind}'
               for kind in ('attack', 'breathing', 'call', 'drinking', 'stomp', 'roar1', 'roar2', 'roar3')},
        }
        for target, source in mappings.items():
            with self.subTest(event=target):
                self.assertEqual(fragment[target]['sounds'], original[source]['sounds'])
                self.assertEqual(packaged[target], fragment[target])
                for sound in original[source]['sounds']:
                    path = Path('sounds') / (sound.split(':', 1)[1] + '.ogg')
                    self.assertEqual((ASSETS / path).read_bytes(), (UPSTREAM / path).read_bytes())


if __name__ == '__main__':
    unittest.main()
