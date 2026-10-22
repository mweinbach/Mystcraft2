#!/usr/bin/env python3
"""Procedural dinosaur motion transcribed from the 1.12.2 species animators.

The packaged JSON keeps Tabula cube names. DinosaurProceduralPose resolves them
through cube_names.json using the same last-write-wins order as FixedTabulaModel.
Chain, bob, and walk values are the arguments after each animator's global
speed and degree multipliers. An age driver uses ageInTicks plus the fixed
amount; a walk driver uses limbSwing and limbSwingAmount. shiftZ matches the
stegosaur and triceratops `rotationPointZ -= amplitude * cos(age * frequency)`
term and does not use movement scale.

Slope foot planting (LegArticulator) and the tyrannosaurus stomp sound are not
part of this pose table. Goat and machine animators are separate.
"""
import json
from pathlib import Path

import import_dinosaur_models as models

ROOT = Path(__file__).resolve().parents[1]
OUT = models.NEW / 'models/entity_port'


def chain(axis, parts, speed, degree, root, driver, amount=None, water=False):
    motion = {'op': 'chain', 'axis': axis, 'parts': list(parts), 'speed': speed,
              'degree': degree, 'root': root, 'driver': driver}
    if amount is not None:
        motion['amount'] = amount
    if water:
        motion['water'] = True
    return motion


def bob(part, speed, degree, driver, amount=None, water=False):
    motion = {'op': 'bob', 'part': part, 'speed': speed, 'degree': degree, 'driver': driver}
    if amount is not None:
        motion['amount'] = amount
    if water:
        motion['water'] = True
    return motion


def walk(part, speed, degree, invert, offset, weight, driver, amount, water=False):
    motion = {'op': 'walk', 'part': part, 'speed': speed, 'degree': degree, 'invert': invert,
              'offset': offset, 'weight': weight, 'driver': driver, 'amount': amount}
    if water:
        motion['water'] = True
    return motion


def shift(part, amplitude, frequency):
    return {'op': 'shiftZ', 'part': part, 'amplitude': amplitude, 'frequency': frequency}


# Explicit per-species tables. Part order matches the animator arrays.
def species_motion():
    raptor_tail = ['tail6', 'tail5', 'tail4', 'tail3', 'tail2', 'tail1']
    raptor_body = ['body3', 'body2', 'body1', 'neck1', 'neck2', 'neck3', 'neck4', 'Head']
    raptor_right = ['Right hand', 'Right forearm', 'Right arm']
    raptor_left = ['Left hand', 'Left forearm', 'Left arm']
    rex_tail = ['tail9', 'tail8', 'tail7', 'tail6', 'tail5', 'tail4', 'tail3', 'tail2', 'tail1']
    rex_body = ['head', 'throat3', 'throat2', 'throat1', 'neck7', 'neck6', 'neck5', 'neck4',
                'neck3', 'neck2', 'neck1', 'bodyShoulders', 'bodyBack', 'bodyHips']
    dilo_tail = ['Tail 6', 'Tail 5', 'Tail 4', 'Tail 3', 'Tail 2', 'Tail BASE']
    dilo_body = ['Head', 'Neck 6', 'Neck 5', 'Neck 4', 'Neck 3', 'Neck 2', 'Neck Base',
                 'Body FRONT', 'Body MIDDLE', 'Body REAR']
    galli_tail = ['Tail part 6', 'Tail part 5', 'Tail part 4', 'Tail part 3', 'Tail part 2', 'Tail part 1']
    galli_body = ['Head', 'Neck part 5', 'Neck part 4', 'Neck part 3', 'Neck part 2', 'Neck part 1',
                  'Body 1', 'Body 2', 'Body 3']
    micro_tail = ['Tail1', 'Tail1', 'Tail1', 'Tail1']
    micro_body = ['Body2', 'Body1', 'Neck1', 'Neck2', 'Head']
    muss_tail = ['Tail8', 'Tail7', 'Tail6', 'Tail5', 'Tail4', 'Tail3', 'Tail2', 'Tail1']
    muss_body = ['Head1', 'Neck3', 'Neck2', 'Neck1', 'Body2', 'Body1']
    para_tail = ['Tail6', 'Tail5', 'Tail4', 'Tail3', 'Tail2', 'Tail1']
    para_body = ['Head', 'Neck3', 'Neck2', 'Neck1', 'Body1', 'Body2', 'Body3']
    stego_tail = ['Tail 6', 'Tail 5', 'Tail 4', 'Tail 3', 'Tail 2', 'Tail 1']
    trike_body = ['Head', 'Neck 3', 'Neck 2', 'Neck 1', 'Body shoulders', 'Body MAIN', 'Body hips']
    fish_front = ['Body Section 3', 'Body Section 2', 'Body Section 1']
    fish_body = ['Tail Section 3', 'Tail Section 2', 'Tail Section 1',
                 'Body Section 3', 'Body Section 2', 'Body Section 1']
    return {
        'velociraptor': {
            'tail': raptor_tail,
            'motions': [
                bob('body3', 0.5, 1.0, 'walk'),
                bob('Right thigh', 0.5, 1.0, 'walk'),
                bob('Left thigh', 0.5, 1.0, 'walk'),
                chain('x', raptor_tail, 0.5, 0.05, 1, 'walk'),
                chain('y', raptor_tail, 0.5, 0.1, 2, 'walk'),
                chain('x', raptor_body, 0.5, 0.025, 3, 'walk'),
                chain('x', raptor_tail, 0.1, 0.05, 2, 'age', 0.25),
                chain('x', raptor_body, 0.1, -0.03, 5, 'age', 0.25),
                chain('x', raptor_right, 0.1, -0.1, 4, 'age', 0.25),
                chain('x', raptor_left, 0.1, -0.1, 4, 'age', 0.25),
            ],
        },
        'tyrannosaurus': {
            'tail': rex_tail,
            'motions': [
                bob('bodyHips', 0.25, 0.75, 'walk'),
                bob('thighRight', 0.25, 0.75, 'walk'),
                bob('thighLeft', 0.25, 0.75, 'walk'),
                chain('x', rex_tail, 0.25, 0.025, 1, 'walk'),
                chain('x', rex_body, 0.25, 0.0125, 3, 'walk'),
                chain('x', rex_body, 0.1, -0.03, 3, 'age', 0.25),
                chain('x', ['handRight', 'bicepRight'], -0.1, 0.2, 4, 'age', 0.25),
                chain('x', ['handLeft', 'bicepLeft'], -0.1, 0.2, 4, 'age', 0.25),
                chain('x', rex_tail, 0.1, -0.1, 2, 'age', 0.1),
            ],
        },
        'brachiosaurus': {
            'tail': ['tail5', 'tail4', 'tail3', 'tail2', 'tail1'],
            'motions': [
                chain('x', ['tail5', 'tail4', 'tail3'], 0.1, 0.5, 3, 'age', 0.025),
                chain('x', ['head', 'neck7', 'neck6', 'neck5', 'neck4', 'neck3', 'neck2', 'Neck 1'],
                      0.1, 0.125, -3, 'age', 0.025),
            ],
        },
        'dilophosaurus': {
            'tail': dilo_tail,
            'hideUnlessTarget': ['Frill Lower Left', 'Frill Upper Left', 'Frill Lower Right', 'Frill Upper Right'],
            'motions': [
                bob('Body REAR', 0.5, 1.0, 'walk'),
                bob('Leg Right UPPER', 0.5, 1.0, 'walk'),
                bob('Leg Left UPPER', 0.5, 1.0, 'walk'),
                chain('x', dilo_tail, 0.5, 0.05, 1, 'walk'),
                chain('y', dilo_tail, 0.5, 0.1, 2, 'walk'),
                chain('x', dilo_body, 0.5, 0.025, 3, 'walk'),
                chain('x', dilo_tail, 0.15, -0.03, 2, 'age', 0.25),
                chain('x', dilo_body, 0.15, 0.03, 3.5, 'age', 0.25),
                chain('x', ['Right hand', 'Right forearm', 'Right arm'], 0.15, -0.1, 4, 'age', 0.25),
                chain('x', ['Left hand', 'Left forearm', 'Left arm'], 0.15, -0.1, 4, 'age', 0.25),
                chain('y', dilo_tail, 0.15, -0.1, 3, 'age', 0.25),
            ],
        },
        'gallimimus': {
            'tail': galli_tail,
            'motions': [
                bob('Body 1', 0.75, 1.5, 'walk'),
                bob('Right thigh', 0.75, 1.5, 'walk'),
                bob('Left thigh', 0.75, 1.5, 'walk'),
                chain('x', galli_tail, 0.375, 0.05, 1, 'walk'),
                chain('y', galli_tail, 0.375, 0.2, 2, 'walk'),
                chain('x', galli_body, 0.375, 0.025, 3, 'walk'),
                chain('x', galli_tail, 0.1, 0.05, 1, 'age', 0.25),
                chain('x', galli_body, 0.1, -0.05, 4, 'age', 0.25),
                chain('x', ['Right wrist', 'Right lower arm', 'Right upper arm'], 0.1, -0.15, 4, 'age', 0.25),
                chain('x', ['Left wrist', 'Left lower arm', 'Left upper arm'], 0.1, -0.15, 4, 'age', 0.25),
            ],
        },
        'microraptor': {
            'tail': micro_tail,
            'glidePitch': True,
            'motions': [
                bob('Body1', 1.0, 3.0, 'walk'),
                bob('RightLeg1', 1.0, 3.0, 'walk'),
                bob('LeftLeg1', 1.0, 3.0, 'walk'),
                chain('x', micro_tail, 0.1, 0.05, 2, 'age', 0.5),
                chain('x', micro_body, 0.1, -0.03, 5, 'age', 0.5),
                chain('x', ['RightArm3', 'RightArm2', 'RightArm1'], 0.1, -0.1, 4, 'age', 0.5),
                chain('x', ['LeftArm3', 'LeftArm2', 'LeftArm1'], 0.1, -0.1, 4, 'age', 0.5),
            ],
        },
        'mussaurus': {
            'tail': muss_tail,
            'motions': [
                chain('x', muss_body, 0.075, -0.01, -2, 'age', 1.0),
                chain('x', muss_tail, 0.075, -0.01, 2, 'age', 1.0),
            ],
        },
        'parasaurolophus': {
            'tail': para_tail,
            'motions': [
                bob('Body1', 0.25, 1.2, 'walk'),
                bob('Thigh Right', 0.25, 1.2, 'walk'),
                bob('Thigh Left', 0.25, 1.2, 'walk'),
                chain('x', para_tail, 0.125, 0.08, 1, 'walk'),
                chain('y', para_tail, 0.125, 0.2, 2, 'walk'),
                chain('x', para_body, 0.125, 0.04, 3, 'walk'),
                walk('Neck1', 0.1, 0.07, False, -1, 0, 'age', 0.25),
                walk('Head', 0.1, 0.07, True, 0, 0, 'age', 0.25),
                walk('Body1', 0.1, 0.04, False, 0, 0, 'age', 0.25),
                walk('Upper Arm Right', 0.1, 0.1, False, -1, 0, 'age', 0.25),
                walk('Upper Arm Left', 0.1, 0.1, False, -1, 0, 'age', 0.25),
                walk('Lower Arm Right', 0.1, 0.1, True, -1.5, 0, 'age', 0.25),
                walk('Lower Arm Left', 0.1, 0.1, True, -1.5, 0, 'age', 0.25),
                walk('Right Hand', 0.1, 0.1, False, -2, 0, 'age', 0.25),
                walk('Left Hand', 0.1, 0.1, False, -2, 0, 'age', 0.25),
                chain('x', para_tail, 0.1, -0.02, 2, 'age', 1),
            ],
        },
        'triceratops': _quadruped(stego_tail, trike_body, 'Neck 1', 'Body hips'),
        'coelacanth': {
            'tail': fish_body,
            'motions': [
                chain('y', fish_front, 0.6, 0.4, 3, 'walk'),
                walk('Left Front Flipper', 0.2, 0.25, False, 1, 0.1, 'age', 0.25, water=True),
                walk('Right Front Flipper', 0.2, 0.25, False, 1, 0.1, 'age', 0.25, water=True),
                chain('y', fish_body, 0.05, -0.075, 1.5, 'age', 0.25, water=True),
                bob('Neck ', 0.125, 4.5, 'age', 0.25, water=True),
                walk('Lower jaw front', 0.1, 0.7, False, 0, 0.5, 'age', 0.25, water=True),
            ],
        },
    }


def _quadruped(tail, body, neck, waist):
    """Stegosaurus and triceratops share one animator body, with different neck names."""
    legs = []
    for side in ('Right', 'Left'):
        legs.extend([
            walk(f'FrontLeg Upper {side}', 0.1, 0.03, False, 0, 0, 'age', 0.25),
            walk(f'FrontLeg MID {side}', 0.1, 0.09, True, 0, 0, 'age', 0.25),
            walk(f'FrontLeg FOOT {side}', 0.1, 0.0525, False, 0, 0, 'age', 0.25),
            shift(f'FrontLeg Upper {side}', 0.5, 0.025),
        ])
    return {
        'tail': tail,
        'motions': [
            bob(waist, 0.5, 1.2, 'walk'),
            bob('RearLeg Upper Right', 0.5, 1.2, 'walk'),
            bob('RearLeg Upper Left', 0.5, 1.2, 'walk'),
            chain('x', tail, 0.25, 0.08, 1, 'walk'),
            chain('y', tail, 0.25, 0.2, 2, 'walk'),
            chain('x', body, 0.25, 0.04, 3, 'walk'),
            walk(neck, 0.1, 0.07, False, -1, 0, 'age', 1),
            walk('Head', 0.1, 0.07, True, 0, 0, 'age', 1),
            walk(waist, 0.1, 0.025, False, 0, 0, 'age', 1),
            *legs,
            chain('y', tail, 0.1, 0.05, 2, 'age', 0.25),
            chain('x', tail, 0.1, -0.05, 1, 'age', 0.25),
        ],
    }


def _round(value):
    if isinstance(value, float):
        return round(value, 6)
    if isinstance(value, list):
        return [_round(item) for item in value]
    if isinstance(value, dict):
        return {key: _round(item) for key, item in value.items()}
    return value


def cube_names(species, needed):
    path = models.OLD / 'models/entities' / species / 'adult' / f'{species}_adult_idle.tbl'
    data = models.model(path)
    found = {}

    def walk(cube):
        found[cube.get('name')] = cube['identifier']
        for child in cube.get('children', []):
            walk(child)

    def group(node):
        for cube in node.get('cubes', []):
            walk(cube)
        for subgroup in node.get('cubeGroups', []):
            group(subgroup)

    group(data)
    missing = sorted({name for name in needed if name not in found})
    if missing:
        raise SystemExit(f'{species} is missing cubes {missing}')
    return {name: found[name] for name in sorted(needed)}


def referenced_names(spec):
    names = set(spec.get('tail', []))
    names.update(spec.get('hideUnlessTarget', []))
    for motion in spec['motions']:
        if 'part' in motion:
            names.add(motion['part'])
        names.update(motion.get('parts', []))
    return names


def build():
    motion = species_motion()
    if set(motion) != set(models.SPECIES):
        raise SystemExit(f'species mismatch: {set(motion) ^ set(models.SPECIES)}')
    names = {}
    ordered = {}
    for species in models.SPECIES:
        spec = _round(motion[species])
        ordered[species] = spec
        names[species] = cube_names(species, referenced_names(spec))
    return ordered, names


def main():
    motion, names = build()
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'procedural_motion.json').write_text(json.dumps(motion, indent=2) + '\n')
    (OUT / 'cube_names.json').write_text(json.dumps(names, indent=2) + '\n')


if __name__ == '__main__':
    main()
