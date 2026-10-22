#!/usr/bin/env python3
"""Validate both built loader artifacts and the resources they must share."""
from pathlib import Path
import hashlib
import json
import tomllib
import zipfile

MOD = Path(__file__).resolve().parents[1]
metadata = dict(line.split('=', 1) for line in (MOD / 'port.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'jurassicraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'jurassicraft-fabric-1.19.2-{version}.jar'

# Resources that must differ: OBJ machine models name each loader's model loader, the
# water-bottle recipe uses each loader's NBT ingredient, and Forge alone reads biome modifiers.
MACHINE_MODELS = {f'assets/jurassicraft/models/block/{name}.json' for name in (
    'cleaning_station', 'dna_combinator_hybridizer', 'dna_extractor', 'dna_sequencer', 'dna_synthesizer',
    'embryo_calcification_machine', 'embryonic_machine', 'feeder', 'incubator')}
LOADER_SPECIFIC = MACHINE_MODELS | {'data/jurassicraft/recipes/dna_nucleotides_from_water.json'}
FORGE_ONLY_PREFIX = 'data/jurassicraft/forge/'

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]
gametest_classes = {path.relative_to(root).with_suffix('').as_posix()
                    for root in GAMETEST_SOURCES if root.is_dir() for path in root.rglob('*.java')}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def outer_class(name):
    return name.removesuffix('.class').split('$', 1)[0]


with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    require('fabric.mod.json' not in a.namelist(), 'Fabric descriptor leaked into Forge')
    require('META-INF/mods.toml' not in b.namelist(), 'Forge descriptor leaked into Fabric')
    fm = tomllib.loads(a.read('META-INF/mods.toml').decode())['mods'][0]
    bm = json.loads(b.read('fabric.mod.json'))
    require(fm['modId'] == bm['id'] == 'jurassicraft', 'Mod ID mismatch')
    require(fm['version'] == bm['version'] == version, 'Version mismatch')
    require(fm['description'].strip() == bm['description'] == metadata['mod_description'], 'Description mismatch')
    for jar, forbidden in ((a, b'net/fabricmc/'), (b, b'net/minecraftforge/')):
        for name in jar.namelist():
            if name.endswith('.class'):
                require(forbidden not in jar.read(name), f'Wrong-loader reference in {name}')
                require(outer_class(name) not in gametest_classes, f'GameTest class shipped: {name}')
            require('/gametest/' not in name and not name.endswith(('_test.nbt', '_test.snbt'))
                    and not name.startswith(('data/jurassicraft_gametest/', 'assets/jurassicraft/test/')),
                    f'GameTest fixture shipped: {name}')

    shared = lambda z: {n for n in z.namelist() if n.startswith(('assets/', 'data/')) and not n.endswith('/')}
    forge_paths, fabric_paths = shared(a), shared(b)
    forge_only = {n for n in forge_paths if n.startswith(FORGE_ONLY_PREFIX)}
    require(forge_only, 'Forge biome modifier missing')
    require(forge_paths - forge_only == fabric_paths, 'Gameplay resource path mismatch: '
            + ', '.join(sorted((forge_paths - forge_only) ^ fabric_paths)))
    require(LOADER_SPECIFIC <= fabric_paths, 'Loader-specific resource missing')
    for name in sorted(fabric_paths):
        same = a.read(name) == b.read(name)
        require(same != (name in LOADER_SPECIFIC), f'Gameplay resource {"differs" if not same else "should be loader-specific"}: {name}')
    for name in MACHINE_MODELS:
        require(json.loads(a.read(name))['loader'] == 'forge:obj', f'Forge machine model loader: {name}')
        require('loader' not in json.loads(b.read(name)) and 'jurassicraft_obj' in json.loads(b.read(name)),
                f'Fabric machine model loader: {name}')

    mixin = json.loads(b.read('jurassicraft.mixins.json'))
    require(mixin.get('required') is True, 'Fabric mixins must be required')
    package = mixin['package'].replace('.', '/') + '/'
    declared = {package + name.replace('.', '/') for name in mixin.get('mixins', []) + mixin.get('client', [])}
    # Mixin refuses to load any other class from its package, so such a class crashes at startup.
    for name in b.namelist():
        if name.startswith(package) and name.endswith('.class'):
            require(outer_class(name) in declared, f'Non-mixin class in the mixin package: {name}')
    mappings = json.loads(b.read(mixin['refmap']))['mappings']
    for name, side in (('EntityPersistentDataMixin', 'mixins'), ('FenceClimbMixin', 'mixins'),
                       ('client.VehicleCameraMixin', 'client'), ('client.KeyBindConflictMixin', 'client')):
        require(name in mixin.get(side, []), f'Missing Fabric {side} hook: {name}')
        require('method_' in json.dumps(mappings.get('ports/jurassicraft/fabric/mixin/' + name.replace('.', '/'), {})),
                f'Missing production mixin mapping: {name}')

    forge_mixin = json.loads(a.read('jurassicraft.forge.mixins.json'))
    require(forge_mixin.get('required') is True, 'Forge mixins must be required')
    require(forge_mixin.get('mixins', []) == [], 'Forge mixins must stay client-only')
    require(forge_mixin.get('client') == ['client.KeyBindConflictMixin'], 'Unexpected Forge mixin set')
    forge_package = forge_mixin['package'].replace('.', '/') + '/'
    forge_declared = {forge_package + name.replace('.', '/') for name in forge_mixin.get('client', [])}
    for name in a.namelist():
        if name.startswith(forge_package) and name.endswith('.class'):
            require(outer_class(name) in forge_declared, f'Non-mixin class in the Forge mixin package: {name}')
    require(all(name + '.class' in a.namelist() for name in forge_declared), 'Declared Forge mixin missing')
    forge_mapped = json.dumps(json.loads(a.read(forge_mixin['refmap']))['mappings'].get(
        'ports/jurassicraft/forge/mixin/client/KeyBindConflictMixin', {}))
    require('m_' in forge_mapped and 'm_6311_' in forge_mapped and 'm_90850_' in forge_mapped,
            'Missing production Forge mixin mapping: client.KeyBindConflictMixin')
    require('MixinConfigs: jurassicraft.forge.mixins.json' in a.read('META-INF/MANIFEST.MF').decode(),
            'Forge JAR does not load its mixin config')

    transformer = [line for line in a.read('META-INF/accesstransformer.cfg').decode().splitlines()
                   if line.strip() and not line.startswith('#')]
    # A widener may list one member twice (accessible and mutable); a transformer line covers both.
    widener = {tuple(line.split()[1:]) for line in b.read('jurassicraft.accesswidener').decode().splitlines()[1:]
               if line.strip() and not line.startswith('#')}
    require(len(transformer) == len(widener), f'Access transformer ({len(transformer)}) and widener ({len(widener)}) diverge')
    print(f'PASS: {len(fabric_paths) - len(LOADER_SPECIFIC)} identical gameplay resources, {len(LOADER_SPECIFIC)} loader-specific; '
          'loader isolation, metadata, GameTest exclusion, access entries and mixin refmaps valid.')
for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
