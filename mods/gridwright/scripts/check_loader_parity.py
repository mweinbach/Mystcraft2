#!/usr/bin/env python3
"""Validate both built Gridwright artifacts and the resources they must share."""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'gridwright-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'gridwright-fabric-1.19.2-{version}.jar'

# Team Reborn Energy carries generator output to other Fabric mods; Forge Energy is built into Forge.
FABRIC_NESTED = ('META-INF/jars/energy-2.3.0.jar',)
NOTICE = 'META-INF/Gridwright-NOTICE.md'
FABRIC_MIXINS = 'gridwright.fabric.mixins.json'

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]


parity.assert_built(forge, fabric)
with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    parity.require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    forge_mod, fabric_mod = parity.check_descriptors(
        a, b, mod_id=metadata['mod_id'], version=version,
        description=metadata['mod_description'], license_id=metadata['mod_license'])
    parity.require('LICENSE' in a.namelist() and 'LICENSE' in b.namelist()
                   and a.read('LICENSE') == b.read('LICENSE'), 'License mismatch')
    parity.require(NOTICE in a.namelist() and NOTICE in b.namelist() and a.read(NOTICE) == b.read(NOTICE)
                   and a.read(NOTICE) == (MOD / 'NOTICE.md').read_bytes(), 'Clean-room notice missing or different')
    shared = parity.check_artifact_pair(a, b, fabric_mod, build_dir=MOD / 'fabric', src=MOD / 'src',
                                        nested=FABRIC_NESTED, label='gridwright')

    parity.check_cross_loader(a, b)
    parity.check_gametests((a, b), *GAMETEST_SOURCES, entry_prefixes=('data/gridwright_gametest/',))
    tests, loader_only = parity.check_loader_gametests(MOD)

    shared_paths = parity.gameplay_paths(a, extra=('pack.mcmeta',))
    fabric_paths = parity.gameplay_paths(b, extra=('pack.mcmeta',))
    parity.require(shared_paths == fabric_paths,
                   'Gameplay resource path mismatch: ' + ', '.join(sorted(shared_paths ^ fabric_paths)))
    for name in sorted(shared_paths):
        parity.require(name.endswith('/') or a.read(name) == b.read(name), f'Gameplay resource differs: {name}')

    shared_classes = lambda z: {n for n in z.namelist() if n.endswith('.class')
                                and '/forge/' not in n and '/fabric/' not in n}
    parity.require(shared_classes(a) == shared_classes(b),
                   'Shared class mismatch: ' + ', '.join(sorted(shared_classes(a) ^ shared_classes(b))))
    # Loader hooks go through the Platform bridge. The exceptions are Fabric's Sound Muffler and Ward hooks,
    # which have no Fabric events (Forge fires PlayLevelSoundEvent, LivingSpawnEvent.CheckSpawn and
    # EntityTeleportEvent.EnderEntity); Forge has no mixins at all.
    parity.require(fabric_mod.get('mixins') == [FABRIC_MIXINS], f'Fabric descriptor must name only {FABRIC_MIXINS}')
    fabric_mixins = json.loads(b.read(FABRIC_MIXINS))
    parity.require(fabric_mixins.get('package') == 'ports.gridwright.fabric.mixin'
                   and fabric_mixins.get('mixins') == ['ServerLevelMixin', 'NaturalSpawnerMixin', 'EnderManMixin']
                   and not fabric_mixins.get('client') and not fabric_mixins.get('server'),
                   f'{FABRIC_MIXINS} may hold only the Sound Muffler and Ward hooks')
    parity.require('MixinConfigs' not in a.read('META-INF/MANIFEST.MF').decode(), 'Forge manifest names a mixin config')
    parity.require(not any(n.endswith('mixins.json') for n in a.namelist()), 'Forge jar carries a mixin config')

    resources = len([n for n in shared_paths if not n.endswith('/')])
    print(f'PASS: {resources} identical gameplay resources, {len(shared_classes(a))} shared classes; '
          f'{parity.summary(shared)}; {tests} GameTests on both loaders, {loader_only} allowlisted to one; '
          'loader isolation, metadata, clean-room notice, nested libraries and GameTest exclusion valid.')
parity.print_hashes(forge, fabric)
