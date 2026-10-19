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
forge = MOD / 'build/libs' / f'mystcraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'mystcraft-fabric-1.19.2-{version}.jar'


def require(condition, message):
    if not condition:
        raise SystemExit(message)


with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    require(a.testzip() is None and b.testzip() is None, 'Corrupt artifact')
    require('fabric.mod.json' not in a.namelist(), 'Fabric descriptor leaked into Forge')
    require('META-INF/mods.toml' not in b.namelist(), 'Forge descriptor leaked into Fabric')
    fm = tomllib.loads(a.read('META-INF/mods.toml').decode())['mods'][0]
    bm = json.loads(b.read('fabric.mod.json'))
    require(fm['modId'] == bm['id'] == 'mystcraft', 'Mod ID mismatch')
    require(fm['version'] == bm['version'] == version, 'Version mismatch')
    for jar, forbidden in ((a, b'net/fabricmc/'), (b, b'net/minecraftforge/')):
        for name in jar.namelist():
            if name.endswith('.class'):
                require(forbidden not in jar.read(name), f'Wrong-loader reference in {name}')
    shared = lambda z: {n for n in z.namelist() if n.startswith(('assets/', 'data/')) and not n.endswith('/')}
    paths = shared(a)
    require(paths == shared(b), 'Gameplay resource path mismatch')
    for name in sorted(paths):
        require(a.read(name) == b.read(name), f'Gameplay resource differs: {name}')
    mixin = json.loads(b.read('mystcraft.fabric.mixins.json'))
    require(mixin.get('required') is True, 'Fabric dropped-book mixin must be required')
    refmap = json.loads(b.read(mixin['refmap']))
    mapping = refmap['mappings']['net/mystcraft/fabric/mixin/ServerLevelMixin']['addFreshEntity']
    require('method_8649' in mapping and 'class_3218' in mapping, 'Missing production mixin mapping')
    visual_mixins = ('AgeWaterColorMixin', 'AgeSkyColorMixin', 'AgeLightmapMixin', 'AgeBiomeTemperatureMixin', 'AgeCelestialTimeMixin', 'AgeCloudHeightMixin', 'AgeClientWeatherMixin', 'DynamicPageItemMixin', 'DynamicPageDisconnectMixin')
    for jar, config, namespace, mapped_prefix in ((a, 'mystcraft.forge.mixins.json', 'net/mystcraft/forge/mixin/', 'm_'),
                                                 (b, 'mystcraft.fabric.mixins.json', 'net/mystcraft/fabric/mixin/', 'method_')):
        settings = json.loads(jar.read(config))
        require(settings.get('required') is True, f'{config} must be required')
        mappings = json.loads(jar.read(settings['refmap']))['mappings']
        require('BookAttackPacketMixin' in settings.get('mixins', []), f'Missing server book-attack hook: {config}')
        require(mapped_prefix in json.dumps(mappings.get(namespace + 'BookAttackPacketMixin', {})),
                f'Missing production book-attack mapping: {config}')
        for name in ('AgePvpMixin', 'AgeChunkTickMixin', 'AgeWeatherTemperatureMixin', 'AgeWeatherChunkMixin', 'ServerPlayerDimensionIdentityMixin', 'VillageHousePoolMixin', 'DeferredCrystalChunkSaveMixin', 'ArchivistCareerMixin', 'HopperBookPickupMixin', 'LegacyTreasureLootMixin'):
            require(name in settings.get('mixins', []), f'Missing common hook: {name}')
            require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production mapping: {name}')
        if jar is b:
            for name in ('InkEntityPhysicsMixin', 'InkBoatPhysicsMixin'):
                require(name in settings.get('mixins', []), f'Missing native ink physics hook: {name}')
                require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production ink mapping: {name}')
        accessor = 'WorldGenRegionBoundsMixin'
        require(accessor in settings.get('mixins', []), f'Missing common bounds accessor: {config}')
        require(('f_' if jar is a else 'field_') in json.dumps(mappings.get(namespace + accessor, {})),
                f'Missing production field mapping: {config}')
        extra = ('AgeCelestialSkyMixin', 'InkFogMixin', 'InkCameraMixin') if jar is b else ()
        for name in visual_mixins + extra:
            require(name in settings.get('client', []), f'Missing client-only visual hook: {name}')
            require(name not in settings.get('mixins', []), f'Client visual hook loaded on server: {name}')
            require(mapped_prefix in json.dumps(mappings.get(namespace + name, {})), f'Missing production mapping: {name}')
    require('MixinConfigs: mystcraft.forge.mixins.json' in a.read('META-INF/MANIFEST.MF').decode(),
            'Forge manifest must load its mixin configuration')
    print(f'PASS: {len(paths)} identical gameplay resources; loader isolation, metadata and both production refmaps valid.')
for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
