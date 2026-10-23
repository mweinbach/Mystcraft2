#!/usr/bin/env python3
"""Validate both built loader artifacts and the resources they must share."""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
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
FORGE_ONLY_TRANSFORMER = 'net.minecraft.world.level.block.FireBlock m_53444_'

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]


with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    parity.require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    fm, bm = parity.check_descriptors(a, b, mod_id='jurassicraft', version=version,
                                     description=metadata['mod_description'])
    parity.check_cross_loader(a, b)
    parity.check_gametests((a, b), *GAMETEST_SOURCES,
                           entry_suffixes=('_test.nbt', '_test.snbt'),
                           entry_prefixes=('data/jurassicraft_gametest/', 'assets/jurassicraft/test/'))

    forge_paths, fabric_paths = parity.gameplay_paths(a), parity.gameplay_paths(b)
    forge_only = {n for n in forge_paths if n.startswith(FORGE_ONLY_PREFIX)}
    parity.require(forge_only, 'Forge biome modifier missing')
    parity.require(forge_paths - forge_only == fabric_paths, 'Gameplay resource path mismatch: '
                   + ', '.join(sorted((forge_paths - forge_only) ^ fabric_paths)))
    parity.require(LOADER_SPECIFIC <= fabric_paths, 'Loader-specific resource missing')
    for name in sorted(fabric_paths):
        same = a.read(name) == b.read(name)
        parity.require(same != (name in LOADER_SPECIFIC), f'Gameplay resource {"differs" if not same else "should be loader-specific"}: {name}')
    for name in MACHINE_MODELS:
        parity.require(json.loads(a.read(name))['loader'] == 'forge:obj', f'Forge machine model loader: {name}')
        parity.require('loader' not in json.loads(b.read(name)) and 'jurassicraft_obj' in json.loads(b.read(name)),
                       f'Fabric machine model loader: {name}')

    mixin = json.loads(b.read('jurassicraft.mixins.json'))
    parity.require(mixin.get('required') is True, 'Fabric mixins must be required')
    package = mixin['package'].replace('.', '/') + '/'
    declared = {package + name.replace('.', '/') for name in mixin.get('mixins', []) + mixin.get('client', [])}
    # Mixin refuses to load any other class from its package, so such a class crashes at startup.
    for name in b.namelist():
        if name.startswith(package) and name.endswith('.class'):
            parity.require(parity.outer_class(name) in declared, f'Non-mixin class in the mixin package: {name}')
    mappings = json.loads(b.read(mixin['refmap']))['mappings']
    for name, side in (('EntityPersistentDataMixin', 'mixins'), ('FenceClimbMixin', 'mixins'),
                       ('VillageAssemblyMixin', 'mixins'), ('ParkEdgePostProcessMixin', 'mixins'),
                       ('client.VehicleCameraMixin', 'client'), ('client.KeyBindConflictMixin', 'client'),
                       ('client.VehicleKeyDispatchMixin', 'client')):
        parity.require(name in mixin.get(side, []), f'Missing Fabric {side} hook: {name}')
        parity.require('method_' in json.dumps(mappings.get('ports/jurassicraft/fabric/mixin/' + name.replace('.', '/'), {})),
                       f'Missing production mixin mapping: {name}')

    forge_mixin = json.loads(a.read('jurassicraft.forge.mixins.json'))
    parity.require(forge_mixin.get('required') is True, 'Forge mixins must be required')
    # Village assembly runs while a dedicated server generates chunks, so that mixin is not client-only.
    parity.require(forge_mixin.get('mixins') == ['VillageAssemblyMixin', 'ParkEdgePostProcessMixin'],
                   'Unexpected Forge server mixin set')
    parity.require(forge_mixin.get('client') == ['client.KeyBindConflictMixin'], 'Unexpected Forge client mixin set')
    forge_package = forge_mixin['package'].replace('.', '/') + '/'
    forge_declared = {forge_package + name.replace('.', '/')
                      for name in forge_mixin.get('mixins', []) + forge_mixin.get('client', [])}
    for name in a.namelist():
        if name.startswith(forge_package) and name.endswith('.class'):
            parity.require(parity.outer_class(name) in forge_declared, f'Non-mixin class in the Forge mixin package: {name}')
    parity.require(all(name + '.class' in a.namelist() for name in forge_declared), 'Declared Forge mixin missing')
    forge_mapped = json.dumps(json.loads(a.read(forge_mixin['refmap']))['mappings'].get(
        'ports/jurassicraft/forge/mixin/client/KeyBindConflictMixin', {}))
    parity.require('m_' in forge_mapped and 'm_6311_' in forge_mapped and 'm_90850_' in forge_mapped,
                   'Missing production Forge mixin mapping: client.KeyBindConflictMixin')
    village_mapped = json.dumps(json.loads(a.read(forge_mixin['refmap']))['mappings'].get(
        'ports/jurassicraft/forge/mixin/VillageAssemblyMixin', {}))
    parity.require('m_227264_' in village_mapped, 'Missing production Forge mixin mapping: VillageAssemblyMixin')
    edge_mapped = json.dumps(json.loads(a.read(forge_mixin['refmap']))['mappings'].get(
        'ports/jurassicraft/forge/mixin/ParkEdgePostProcessMixin', {}))
    parity.require('m_62812_' in edge_mapped, 'Missing production Forge mixin mapping: ParkEdgePostProcessMixin')
    parity.require('MixinConfigs: jurassicraft.forge.mixins.json' in a.read('META-INF/MANIFEST.MF').decode(),
                   'Forge JAR does not load its mixin config')

    # Fabric registers flammability through FlammableBlockRegistry, so FireBlock.setFlammable is Forge-only.
    transformer = [line for line in a.read('META-INF/accesstransformer.cfg').decode().splitlines()
                   if line.strip() and not line.startswith('#') and FORGE_ONLY_TRANSFORMER not in line]
    # A widener may list one member twice (accessible and mutable); a transformer line covers both.
    widener = {tuple(line.split()[1:]) for line in b.read('jurassicraft.accesswidener').decode().splitlines()[1:]
               if line.strip() and not line.startswith('#')}
    parity.require(len(transformer) == len(widener), f'Access transformer ({len(transformer)}) and widener ({len(widener)}) diverge')
    print(f'PASS: {len(fabric_paths) - len(LOADER_SPECIFIC)} identical gameplay resources, {len(LOADER_SPECIFIC)} loader-specific; '
          'loader isolation, metadata, GameTest exclusion, access entries and mixin refmaps valid.')
parity.print_hashes(forge, fabric)
