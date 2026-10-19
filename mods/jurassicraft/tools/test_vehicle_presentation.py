#!/usr/bin/env python3
"""Verify animated pivots and original sound assets in the generated vehicle port."""
import json
from pathlib import Path
from import_dinosaur_models import model, roots

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
NEW = ROOT / 'src/main/resources/assets/jurassicraft'
PIVOTS = {
    'jeep_wrangler': ('wheel holder front', 'wheel holder back', 'steering wheel main', 'door left main', 'door right main'),
    'ford_explorer': ('wheel holder front', 'wheel holder back', 'steering wheel main', 'door left main', 'door right main',
                      'Back door left main', 'Back door right main'),
    'helicopter': ('rotorbase_rotatehere', 'tailrotor_rotatehere'),
}


def descendants(cubes):
    for cube in cubes:
        yield cube
        yield from descendants(cube.get('children', []))


def main():
    for name, pivots in PIVOTS.items():
        upstream = list(descendants(roots(model(OLD / f'models/entities/{name}/{name}.tbl'))))
        ported = list(descendants(json.loads((NEW / f'models/entity_port/{name}.json').read_text())['cubes']))
        for pivot in pivots:
            original = [x for x in upstream if x.get('name') == pivot]
            actual = [x for x in ported if x.get('name') == pivot]
            assert len(original) == len(actual) == 1, (name, pivot, 'must be unique')
            assert actual[0]['identifier'] == original[0]['identifier'], (name, pivot, 'original pivot lost')
            assert len(actual[0]['children']) == len(original[0]['children']) > 0, (name, pivot, 'moving geometry lost')
    definitions = json.loads((ROOT / 'tools/sounds/vehicles.json').read_text())
    for name in ('car_move', 'helicopter_warning'):
        assert definitions[name]['sounds'] == ['jurassicraft:' + name]
        assert (NEW / f'sounds/{name}.ogg').read_bytes() == (OLD / f'sounds/{name}.ogg').read_bytes()
    print('Vehicle animation pivots and original sound assets verified')


if __name__ == '__main__':
    main()
