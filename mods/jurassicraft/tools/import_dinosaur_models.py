#!/usr/bin/env python3
"""Convert the imported 1.12 Tabula models/poses into renderer data.

The source .tbl files are ZIP archives containing a model.json. All animation
poses use the same identifiers as the idle geometry. Keep this deterministic so
upstream geometry and keyframe values can be verified independently.
"""
import json
import math
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / 'upstream/src/main/resources/assets/jurassicraft'
NEW = ROOT / 'src/main/resources/assets/jurassicraft'
SPECIES = ('velociraptor', 'coelacanth', 'microraptor', 'brachiosaurus', 'mussaurus',
           'dilophosaurus', 'gallimimus', 'parasaurolophus', 'triceratops', 'tyrannosaurus')
# Names and divisors passed to faceTarget by the original species animators.
LOOK_CHAINS = {
    'velociraptor': (2.0, ('neck1', 'Head')),
    'microraptor': (2.0, ('Neck1', 'Head')),
    'mussaurus': (2.0, ('Neck1', 'Neck2', 'Neck3')),
    'dilophosaurus': (1.0, ('Neck Base', 'Head')),
    'gallimimus': (1.0, ('Neck part 1', 'Neck part 2', 'Neck part 3', 'Neck part 4', 'Head')),
    'parasaurolophus': (2.0, ('Neck1', 'Neck2')),
    'triceratops': (1.0, ('Neck 1', 'Neck 2', 'Neck 3', 'Head')),
    'tyrannosaurus': (1.5, ('bodyShoulders', 'neck1', 'neck7', 'head')),
}
KINDS = ('IDLE', 'WALKING', 'RUNNING', 'SWIMMING', 'ATTACKING', 'INJURED', 'DYING',
         'SLEEPING', 'RESTING', 'CLIMBING', 'GLIDING', 'DILOPHOSAURUS_SPIT',
         'PREPARE_LEAP', 'LEAP', 'LEAP_LAND', 'EATING', 'DRINKING', 'HEAD_COCKING',
         'SPEAK', 'CALLING', 'LOOKING_LEFT', 'LOOKING_RIGHT', 'LAYING_EGG',
         'GIVING_BIRTH', 'ON_LAND', 'START_CLIMBING', 'PREENING', 'TAIL_DISPLAY',
         'PECKING', 'REARING_UP', 'ROARING')


def model(path):
    with zipfile.ZipFile(path) as z:
        return json.loads(z.read('model.json'))


def roots(data):
    def group(g):
        for cube in g.get('cubes', []):
            yield cube
        for subgroup in g.get('cubeGroups', []):
            yield from group(subgroup)
    return list(group(data))


def geometry(c):
    return {k: c[k] for k in ('identifier', 'dimensions', 'position', 'offset', 'rotation', 'scale',
                              'txOffset', 'txMirror', 'mcScale', 'hidden') if k in c} | {
        'children': [geometry(x) for x in c.get('children', [])]}


def transforms(c, out):
    out[c['identifier']] = [c['position'], c['rotation'], c.get('scale', [1, 1, 1])]
    for child in c.get('children', []):
        transforms(child, out)


def convert(species):
    src = OLD / 'models/entities' / species / 'adult'
    animation = json.loads((src / (species + '_adult.json')).read_text())['poses']
    idle_name = animation['IDLE']['variants']['0'][0]['pose']
    idle = model(src / (idle_name + '.tbl'))
    output = {'textureWidth': idle['textureWidth'], 'textureHeight': idle['textureHeight'],
              'scale': idle.get('scale', [1, 1, 1]), 'cubes': [geometry(c) for c in roots(idle)], 'animations': {}}
    ids = set()
    for cube in roots(idle):
        transforms(cube, temp := {})
        ids.update(temp)
    for kind in KINDS:
        for variant, frames in animation.get(kind, {}).get('variants', {}).items():
            if not frames:
                continue
            converted = []
            for frame in frames:
                path = src / (frame['pose'] + '.tbl')
                if not path.exists():
                    raise FileNotFoundError(path)
                pose = model(path)
                parts = {}
                for cube in roots(pose):
                    transforms(cube, parts)
                # A few original poses lack idle identifiers; those parts retain idle transforms.
                parts = {key: value for key, value in parts.items() if key in ids}
                converted.append({'time': frame['time'], 'parts': parts})
            name = kind if variant == '0' else f'{kind}:{variant}'
            output['animations'][name] = converted
    out = NEW / 'models/entity_port' / (species + '.json')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(output, separators=(',', ':'), ensure_ascii=False) + '\n')
    for gender in ('male', 'female'):
        name = f'{species}_{gender}_adult.png'
        target = NEW / 'textures/entities' / species / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(OLD / 'textures/entities' / species / name, target)
        if species == 'tyrannosaurus':
            for layer in ('claws', 'eyes', 'mouth', 'nostrils', 'stripes', 'teeth'):
                layer_name = f'{species}_{gender}_adult_{layer}.png'
                shutil.copyfile(OLD / 'textures/entities' / species / layer_name, target.parent / layer_name)



def convert_skeleton(species, pose='idle'):
    source = OLD / 'models/entities' / species / 'skeleton' / f'{species}_skeleton_{pose}.tbl'
    data = model(source)
    parts = {}
    for cube in roots(data):
        transforms(cube, parts)
    output = {'textureWidth': data['textureWidth'], 'textureHeight': data['textureHeight'],
              'scale': data.get('scale', [1, 1, 1]), 'cubes': [geometry(c) for c in roots(data)],
              'animations': {'IDLE': [{'time': 20, 'parts': parts}]}}
    suffix = '' if pose == 'idle' else f'_{pose}'
    out = NEW / 'models/entity_port' / f'{species}_skeleton{suffix}.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(output, separators=(',', ':'), ensure_ascii=False) + '\n')
    for gender in ('male', 'female'):
        name = f'{species}_{gender}_skeleton.png'
        dest = NEW / 'textures/entities' / species / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(OLD / 'textures/entities' / species / name, dest)


def convert_egg(species):
    if species == 'coelacanth':
        return
    source = OLD / 'models/entities/egg' / f'{species}.tbl'
    data = model(source)
    parts = {}
    for cube in roots(data):
        transforms(cube, parts)
    output = {'textureWidth': data['textureWidth'], 'textureHeight': data['textureHeight'],
              'scale': data.get('scale', [1, 1, 1]), 'cubes': [geometry(c) for c in roots(data)],
              'animations': {'IDLE': [{'time': 20, 'parts': parts}]}}
    out = NEW / 'models/entity_port' / f'{species}_egg.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(output, separators=(',', ':'), ensure_ascii=False) + '\n')
    dest = NEW / 'textures/entities/egg' / f'{species}.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(OLD / 'textures/entities/egg' / f'{species}.png', dest)

def generate_head_tracking():
    result = {}
    for species, (divisor, names) in LOOK_CHAINS.items():
        idle = model(OLD / 'models/entities' / species / 'adult' / f'{species}_adult_idle.tbl')
        matches = {name: [] for name in names}
        def collect(cube):
            if cube.get('name') in matches:
                matches[cube['name']].append(cube['identifier'])
            for child in cube.get('children', []):
                collect(child)
        for cube in roots(idle):
            collect(cube)
        for name, ids in matches.items():
            if len(ids) != 1:
                raise ValueError(f'{species} look part {name}: expected one cube, got {len(ids)}')
        result[species] = {'divisor': divisor, 'parts': [matches[name][0] for name in names]}
    path = NEW / 'models/entity_port/head_tracking.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, indent=2) + '\n')


def generate_animation_data():
    """Keep server-side pose validation and durations in sync with imported keyframes."""
    lines = ['package ports.jurassicraft.entity;', '', 'import java.util.Map;',
             'import ports.jurassicraft.content.Species;', '',
             '/** Generated from the original adult pose timelines by import_dinosaur_models.py. */',
             'final class DinosaurAnimationData {',
             '    private static final Map<Species, Map<String, Integer>> DURATIONS = Map.ofEntries(']
    for index, species in enumerate(SPECIES):
        src = OLD / 'models/entities' / species / 'adult' / f'{species}_adult.json'
        animations = json.loads(src.read_text())['poses']
        entries = []
        for kind in KINDS:
            for variant, frames in animations.get(kind, {}).get('variants', {}).items():
                if frames:
                    name = kind if variant == '0' else f'{kind}:{variant}'
                    duration = math.ceil(sum(frame['time'] for frame in frames))
                    entries.append(f'Map.entry("{name}", {duration})')
        suffix = ',' if index + 1 < len(SPECIES) else ''
        lines.append(f'        Map.entry(Species.{species.upper()}, Map.ofEntries(')
        for i, entry in enumerate(entries):
            lines.append('            ' + entry + (',' if i + 1 < len(entries) else ''))
        lines.append('        ))' + suffix)
    lines.extend(['    );', '    private DinosaurAnimationData() {}',
                  '    static int duration(Species species, String pose) {',
                  '        return DURATIONS.getOrDefault(species, Map.of()).getOrDefault(pose, 0);',
                  '    }', '    static java.util.Set<String> names() {',
                  '        return DURATIONS.values().stream().flatMap(map -> map.keySet().stream())',
                  '                .map(key -> key.split(":", 2)[0]).collect(java.util.stream.Collectors.toUnmodifiableSet());',
                  '    }', '}'])
    (ROOT / 'src/main/java/ports/jurassicraft/entity/DinosaurAnimationData.java').write_text('\n'.join(lines) + '\n')


def import_species_sounds(species, sounds):
    src = OLD / 'sounds/dinosaurs' / species
    kinds = [('ambient', 'living'), ('hurt', 'hurt'), ('death', 'death')]
    if species == 'dilophosaurus':
        kinds.append(('spit', 'spit'))
    if species in ('velociraptor', 'microraptor', 'mussaurus', 'tyrannosaurus'):
        kinds.append(('attack', 'attack'))
    if species in ('velociraptor', 'tyrannosaurus'):
        kinds.append(('breathing', 'breath'))
    if species == 'velociraptor':
        kinds.append(('call', 'bark'))
    if species == 'parasaurolophus':
        kinds.append(('call', 'call'))
    if species == 'tyrannosaurus':
        kinds.extend([('call', 'calling'), ('drinking', 'drinking'), ('stomp', 'stomp')])
        kinds.extend((f'roar{i}', f'roar_{i}_') for i in range(1, 4))
    for kind, fragment in kinds:
        files = sorted(src.glob(f'{species}_{fragment}*.ogg')) if src.exists() else []
        if not files:
            if species != 'coelacanth':
                raise FileNotFoundError(f'{species} {kind} sound')
            continue
        names = []
        for f in files:
            dest = NEW / 'sounds/dinosaurs' / species / f.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(f, dest)
            names.append(f'jurassicraft:dinosaurs/{species}/{f.stem}')
        sounds[f'entity.{species}.{kind}'] = {'sounds': names}


def generate_sounds():
    sounds = {}
    for species in SPECIES:
        import_species_sounds(species, sounds)
    sound_fragment = ROOT / 'tools/sounds/dinosaurs.json'
    sound_fragment.parent.mkdir(parents=True, exist_ok=True)
    sound_fragment.write_text(json.dumps(sounds, indent=2) + '\n')
    from merge_sounds import merge
    merge()


def main():
    for species in SPECIES:
        convert(species)
        convert_skeleton(species)
        convert_egg(species)
        if species == 'tyrannosaurus':
            convert_skeleton(species, 'death')
            convert_skeleton(species, 'attacking')
    generate_animation_data()
    generate_head_tracking()
    generate_sounds()
    names = {f'entity.jurassicraft.{s}': s.capitalize() for s in SPECIES}
    names.update({f'item.jurassicraft.{s}_spawn_egg': s.capitalize() + ' Spawn Egg' for s in SPECIES})
    names['item.jurassicraft.dinosaur_meat'] = 'Raw Dinosaur Meat'
    names['entity.jurassicraft.dinosaur_egg'] = 'Dinosaur Egg'
    (ROOT / 'tools/lang/dinosaurs.json').write_text(json.dumps(names, indent=2) + '\n')
    from generate_dinosaur_food import generate as generate_food
    generate_food(SPECIES)
    for species in SPECIES:
        out = NEW / 'models/item' / f'{species}_spawn_egg.json'
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text('{"parent":"minecraft:item/template_spawn_egg"}\n')
    target = NEW / 'textures/item_port/dinosaur_meat.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(OLD / 'textures/items/meat/tyrannosaurus.png', target)
    (NEW / 'models/item/dinosaur_meat.json').write_text('{"parent":"minecraft:item/generated","textures":{"layer0":"jurassicraft:item_port/dinosaur_meat"}}\n')


if __name__ == '__main__':
    main()
