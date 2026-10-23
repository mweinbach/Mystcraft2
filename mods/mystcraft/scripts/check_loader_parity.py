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
forge = MOD / 'build/libs' / f'mystcraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'mystcraft-fabric-1.19.2-{version}.jar'

GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]


with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    parity.require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    fm, bm = parity.check_descriptors(a, b, mod_id='mystcraft', version=version,
                                     description=metadata['mod_description'])
    parity.check_cross_loader(a, b)
    parity.check_gametests((a, b), *GAMETEST_SOURCES)
    paths = parity.gameplay_paths(a)
    parity.require(paths == parity.gameplay_paths(b), 'Gameplay resource path mismatch')
    for name in sorted(paths):
        parity.require(a.read(name) == b.read(name), f'Gameplay resource differs: {name}')
    mixin = json.loads(b.read('mystcraft.fabric.mixins.json'))
    parity.require(mixin.get('required') is True, 'Fabric dropped-book mixin must be required')
    refmap = json.loads(b.read(mixin['refmap']))
    mapping = refmap['mappings']['net/mystcraft/fabric/mixin/ServerLevelMixin']['addFreshEntity']
    parity.require('method_8649' in mapping and 'class_3218' in mapping, 'Missing production mixin mapping')
    visual_mixins = ('AgeWaterColorMixin', 'AgeSkyColorMixin', 'AgeLightmapMixin', 'AgeBiomeTemperatureMixin', 'AgeCelestialTimeMixin', 'AgeCloudHeightMixin', 'AgeClientWeatherMixin', 'DynamicPageItemMixin', 'DynamicPageDisconnectMixin')
    for jar, config, namespace, mapped_prefix in ((a, 'mystcraft.forge.mixins.json', 'net/mystcraft/forge/mixin/', 'm_'),
                                                 (b, 'mystcraft.fabric.mixins.json', 'net/mystcraft/fabric/mixin/', 'method_')):
        settings = json.loads(jar.read(config))
        parity.require(settings.get('required') is True, f'{config} must be required')
        mappings = json.loads(jar.read(settings['refmap']))['mappings']
        parity.require('BookAttackPacketMixin' in settings.get('mixins', []), f'Missing server book-attack hook: {config}')
        parity.require(mapped_prefix in json.dumps(mappings.get(namespace + 'BookAttackPacketMixin', {})),
                       f'Missing production book-attack mapping: {config}')
        for name in ('AgePvpMixin', 'AgeChunkTickMixin', 'AgeWeatherTemperatureMixin', 'AgeWeatherChunkMixin', 'ServerPlayerDimensionIdentityMixin', 'VillageHousePoolMixin', 'DeferredCrystalChunkSaveMixin', 'ArchivistCareerMixin', 'HopperBookPickupMixin'):
            parity.require(name in settings.get('mixins', []), f'Missing common hook: {name}')
            parity.require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production mapping: {name}')
        # Forge edits the legacy treasure tables' JSON; Fabric uses LootTableEvents.MODIFY instead of a mixin.
        if jar is a:
            parity.require('LegacyTreasureLootMixin' in settings.get('mixins', []), 'Missing Forge legacy treasure hook')
            parity.require(mapped_prefix in json.dumps(mappings.get(namespace + 'LegacyTreasureLootMixin', {})),
                           'Missing production mapping: LegacyTreasureLootMixin')
        else:
            parity.require('LegacyTreasureLootMixin' not in settings.get('mixins', []), 'Fabric must use LootTableEvents, not a loot mixin')
        if jar is b:
            for name in ('InkEntityPhysicsMixin', 'InkBoatPhysicsMixin'):
                parity.require(name in settings.get('mixins', []), f'Missing native ink physics hook: {name}')
                parity.require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production ink mapping: {name}')
        accessor = 'WorldGenRegionBoundsMixin'
        parity.require(accessor in settings.get('mixins', []), f'Missing common bounds accessor: {config}')
        parity.require(('f_' if jar is a else 'field_') in json.dumps(mappings.get(namespace + accessor, {})),
                       f'Missing production field mapping: {config}')
        extra = ('AgeCelestialSkyMixin', 'InkFogMixin', 'InkCameraMixin') if jar is b else ()
        for name in visual_mixins + extra:
            parity.require(name in settings.get('client', []), f'Missing client-only visual hook: {name}')
            parity.require(name not in settings.get('mixins', []), f'Client visual hook loaded on server: {name}')
            parity.require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production mapping: {name}')
    parity.require('MixinConfigs: mystcraft.forge.mixins.json' in a.read('META-INF/MANIFEST.MF').decode(),
                   'Forge manifest must load its mixin configuration')
    print(f'PASS: {len(paths)} identical gameplay resources; loader isolation, metadata and both production refmaps valid.')
parity.print_hashes(forge, fabric)
