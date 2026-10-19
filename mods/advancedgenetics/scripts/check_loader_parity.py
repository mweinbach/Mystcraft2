#!/usr/bin/env python3
"""Validate both built Advanced Genetics artifacts and the resources they must share."""
from pathlib import Path
import hashlib
import json
import tomllib
import zipfile

MOD = Path(__file__).resolve().parents[1]
metadata = dict(line.split('=', 1) for line in (MOD / 'port.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'advancedgenetics-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'advancedgenetics-fabric-1.19.2-{version}.jar'

FORGE_MIXINS = 'advancedgenetics.forge.mixins.json'
FABRIC_MIXINS = 'advancedgenetics.fabric.mixins.json'
# Loader-only mixins live in a subpackage named after their loader; everything else is shared.
LOADER_PACKAGES = ('forge.', 'fabric.')
# Targets Forge's own ArrowItem.isInfinite with remap = false, so it has no refmap entry.
UNMAPPED_MIXINS = {'forge.ArrowItemMixin'}
FABRIC_NESTED = ('META-INF/jars/energy-2.3.0.jar', 'META-INF/jars/core-3.6.4.jar', 'META-INF/jars/toml-3.6.4.jar')
NESTED_LICENSES = ('META-INF/licenses/night-config-LGPL-3.0.txt', 'META-INF/licenses/night-config-GPL-3.0.txt')

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]
gametest_classes = {path.relative_to(root).with_suffix('').as_posix()
                    for root in GAMETEST_SOURCES if root.is_dir() for path in root.rglob('*.java')}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def outer_class(name):
    return name.removesuffix('.class').split('$', 1)[0]


def check_mixins(jar, config_name, refmap_prefixes, loader_package):
    config = json.loads(jar.read(config_name))
    require(config.get('required') is True, f'{config_name} must be required')
    names = config.get('mixins', []) + config.get('client', [])
    require(all(not name.startswith(LOADER_PACKAGES) or name.startswith(loader_package) for name in names),
            f'{config_name} lists another loader\'s mixin')
    package = config['package'].replace('.', '/') + '/'
    declared = {package + name.replace('.', '/') for name in names}
    # Mixin refuses to load any other class from its package, so such a class crashes at startup.
    for name in jar.namelist():
        if name.startswith(package) and name.endswith('.class'):
            require(outer_class(name) in declared, f'Non-mixin class in the mixin package: {name}')
    for name in declared:
        require(name + '.class' in jar.namelist(), f'Declared mixin missing from {config_name}: {name}')
    mappings = json.loads(jar.read(config['refmap']))['mappings']
    for name in declared - {package + name.replace('.', '/') for name in UNMAPPED_MIXINS}:
        mapped = json.dumps(mappings.get(name, {}))
        require(any(prefix in mapped for prefix in refmap_prefixes), f'Missing production mixin mapping: {name}')
    return config, {name for name in names if not name.startswith(LOADER_PACKAGES)}


with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    require('fabric.mod.json' not in a.namelist(), 'Fabric descriptor leaked into Forge')
    require('META-INF/mods.toml' not in b.namelist(), 'Forge descriptor leaked into Fabric')
    fm = tomllib.loads(a.read('META-INF/mods.toml').decode())['mods'][0]
    bm = json.loads(b.read('fabric.mod.json'))
    require(fm['modId'] == bm['id'] == metadata['mod_id'], 'Mod ID mismatch')
    require(fm['version'] == bm['version'] == version, 'Version mismatch')
    require(fm['description'].strip() == bm['description'] == metadata['mod_description'], 'Description mismatch')
    require(bm['license'] == metadata['mod_license'] and 'LICENSE' in a.namelist() and 'LICENSE' in b.namelist()
            and a.read('LICENSE') == b.read('LICENSE'), 'License mismatch')
    for nested in FABRIC_NESTED:
        require(nested in b.namelist() and {'file': nested} in bm.get('jars', []), f'Fabric nested library missing: {nested}')
    for notice in NESTED_LICENSES:
        require(b.read(notice) == (MOD / 'licenses' / notice.rsplit('/', 1)[1]).read_bytes(), f'Bundled-library license missing: {notice}')
    require(not any(name.startswith('META-INF/jars/') for name in a.namelist()), 'Forge JAR nests libraries')

    for jar, forbidden in ((a, b'net/fabricmc/'), (b, b'net/minecraftforge/')):
        for name in jar.namelist():
            if name.endswith('.class'):
                require(forbidden not in jar.read(name), f'Wrong-loader reference in {name}')
                require(outer_class(name) not in gametest_classes, f'GameTest class shipped: {name}')
            require('/gametest/' not in name and not name.endswith('.snbt')
                    and not name.startswith('data/advancedgenetics_gametest/'), f'GameTest fixture shipped: {name}')

    shared = lambda z: {n for n in z.namelist() if n.startswith(('assets/', 'data/')) or n == 'pack.mcmeta'}
    shared_paths = shared(a)
    require(shared_paths == shared(b), 'Gameplay resource path mismatch: ' + ', '.join(sorted(shared_paths ^ shared(b))))
    for name in sorted(shared_paths):
        require(name.endswith('/') or a.read(name) == b.read(name), f'Gameplay resource differs: {name}')

    shared_classes = lambda z: {n for n in z.namelist() if n.endswith('.class')
                                and '/forge/' not in n and '/fabric/' not in n}
    require(shared_classes(a) == shared_classes(b),
            'Shared class mismatch: ' + ', '.join(sorted(shared_classes(a) ^ shared_classes(b))))

    require(a.read('META-INF/MANIFEST.MF').decode().count(f'MixinConfigs: {FORGE_MIXINS}') == 1,
            'Forge manifest does not name its mixin config')
    require(bm['mixins'] == [FABRIC_MIXINS], 'Fabric descriptor does not name its mixin config')
    forge_config, forge_shared = check_mixins(a, FORGE_MIXINS, ('m_', 'f_'), 'forge.')
    fabric_config, fabric_shared = check_mixins(b, FABRIC_MIXINS, ('method_', 'field_'), 'fabric.')
    require(forge_shared == fabric_shared, 'Shared mixins differ: ' + ', '.join(sorted(forge_shared ^ fabric_shared)))
    require(forge_config['package'] == fabric_config['package'], 'Mixin packages differ')

    print(f'PASS: {len([n for n in shared_paths if not n.endswith("/")])} identical gameplay resources, '
          f'{len(shared_classes(a))} shared classes, {len(forge_shared)} shared mixins; loader isolation, metadata, '
          'nested libraries, GameTest exclusion and both refmaps valid.')
for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
