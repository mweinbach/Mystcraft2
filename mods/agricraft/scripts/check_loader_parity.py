#!/usr/bin/env python3
"""Validate both built AgriCraft artifacts and the resources they must share."""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'agricraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'agricraft-fabric-1.19.2-{version}.jar'

FORGE_MIXINS = 'agricraft.forge.mixins.json'
FABRIC_MIXINS = 'agricraft.fabric.mixins.json'
# Loader-only mixins live in a subpackage named after their loader; everything else is shared.
LOADER_PACKAGES = ('forge.', 'fabric.')
FABRIC_NESTED = ('META-INF/jars/mixinextras-fabric-0.4.1.jar',)
# Upstream ships its compat data and resource packs inside the jar; both loaders register them.
EXTRA_SHARED = ('pack.mcmeta', 'logo.png')
SHARED_ROOTS = ('assets/', 'data/', 'datapacks/', 'resourcepacks/')

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]


def check_mixins(jar, config_name, refmap_prefixes, loader_package):
    config = json.loads(jar.read(config_name))
    parity.require(config.get('required') is True, f'{config_name} must be required')
    names = config.get('mixins', []) + config.get('client', [])
    parity.require(all(not name.startswith(LOADER_PACKAGES) or name.startswith(loader_package) for name in names),
                   f'{config_name} lists another loader\'s mixin')
    package = config['package'].replace('.', '/') + '/'
    declared = {package + name.replace('.', '/') for name in names}
    # Mixin refuses to load any other class from its package, so such a class crashes at startup.
    for name in jar.namelist():
        if name.startswith(package) and name.endswith('.class'):
            parity.require(parity.outer_class(name) in declared, f'Non-mixin class in the mixin package: {name}')
    for name in declared:
        parity.require(name + '.class' in jar.namelist(), f'Declared mixin missing from {config_name}: {name}')
    mappings = json.loads(jar.read(config['refmap']))['mappings']
    for name in declared:
        mapped = json.dumps(mappings.get(name, {}))
        parity.require(any(prefix in mapped for prefix in refmap_prefixes), f'Missing production mixin mapping: {name}')
    return config, {name for name in names if not name.startswith(LOADER_PACKAGES)}


parity.assert_built(forge, fabric)
with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    parity.require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    forge_mod, fabric_mod = parity.check_descriptors(
        a, b, mod_id=metadata['mod_id'], version=version,
        description=metadata['mod_description'], license_id=metadata['mod_license'])
    parity.require('LICENSE' in a.namelist() and 'LICENSE' in b.namelist()
                   and a.read('LICENSE') == b.read('LICENSE'), 'License mismatch')
    notice = 'META-INF/AgriCraft-NOTICE.md'
    parity.require(notice in a.namelist() and notice in b.namelist() and a.read(notice) == b.read(notice),
                   'Upstream notice missing or different')
    shared = parity.check_artifact_pair(a, b, fabric_mod, build_dir=MOD / 'fabric', src=MOD / 'src',
                                        nested=FABRIC_NESTED, label='agricraft')

    parity.check_cross_loader(a, b)
    parity.check_gametests((a, b), *GAMETEST_SOURCES, entry_prefixes=('data/agricraft_gametest/',))
    tests, loader_only = parity.check_loader_gametests(MOD)

    shared_paths = parity.gameplay_paths(a, roots=SHARED_ROOTS, extra=EXTRA_SHARED)
    fabric_paths = parity.gameplay_paths(b, roots=SHARED_ROOTS, extra=EXTRA_SHARED)
    parity.require(shared_paths == fabric_paths,
                   'Gameplay resource path mismatch: ' + ', '.join(sorted(shared_paths ^ fabric_paths)))
    for name in sorted(shared_paths):
        parity.require(a.read(name) == b.read(name), f'Gameplay resource differs: {name}')

    shared_classes = lambda z: {n for n in z.namelist() if n.endswith('.class')
                                and '/forge/' not in n and '/fabric/' not in n}
    parity.require(shared_classes(a) == shared_classes(b),
                   'Shared class mismatch: ' + ', '.join(sorted(shared_classes(a) ^ shared_classes(b))))

    parity.require(parity.manifest_mixin_configs(a) == [FORGE_MIXINS], 'Forge manifest does not name its mixin config')
    parity.require(fabric_mod['mixins'] == [FABRIC_MIXINS], 'Fabric descriptor does not name its mixin config')
    forge_config, forge_shared = check_mixins(a, FORGE_MIXINS, ('m_', 'f_'), 'forge.')
    fabric_config, fabric_shared = check_mixins(b, FABRIC_MIXINS, ('method_', 'field_'), 'fabric.')
    parity.require(forge_shared == fabric_shared, 'Shared mixins differ: ' + ', '.join(sorted(forge_shared ^ fabric_shared)))
    parity.require(forge_config['package'] == fabric_config['package'], 'Mixin packages differ')

    resources = len(shared_paths)
    print(f'PASS: {resources} identical gameplay resources, '
          f'{len(shared_classes(a))} shared classes, {len(forge_shared)} shared mixins; {parity.summary(shared)}; '
          f'{tests} GameTests on both loaders, {loader_only} allowlisted to one; loader isolation, metadata, '
          'nested libraries, GameTest exclusion and both refmaps valid.')
parity.print_hashes(forge, fabric)
