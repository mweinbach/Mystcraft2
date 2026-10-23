#!/usr/bin/env python3
"""Validate both built BuildCraft loader artifacts and the gameplay they must share."""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'buildcraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'buildcraft-fabric-1.19.2-{version}.jar'
SHARED_CLASSES = 'ports/buildcraft/'
LOADER_PACKAGES = ('ports/buildcraft/forge/', 'ports/buildcraft/fabric/')
# Fabric adds the oil well placed feature to biomes in code instead.
FORGE_ONLY = {'data/buildcraft/forge/biome_modifier/oil_well.json'}
# One mixin config serves both loaders; each build writes its own refmap under the same name.
MIXIN_CONFIG = 'buildcraft.mixins.json'
MIXIN_REFMAP = 'buildcraft.refmap.json'
GAMETEST_SOURCES = [MOD / 'src' / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric')]


def require(condition, message):
    if not condition:
        raise SystemExit(f'FAIL: {message}')


def shared_classes(jar):
    return {n for n in jar.namelist()
            if n.endswith('.class') and n.startswith(SHARED_CLASSES) and not n.startswith(LOADER_PACKAGES)}


for path in (forge, fabric):
    require(path.is_file(), f'missing {path.relative_to(MOD)}; build both variants first')

with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    require(a.testzip() is None and b.testzip() is None, 'corrupt artifact')
    fm, bm = parity.check_descriptors(a, b, mod_id='buildcraft', version=version, fail=require)
    require(bm['depends'].get('team_reborn_energy'), 'Fabric build must depend on Team Reborn Energy')
    bundled = [n for n in b.namelist() if n.startswith('META-INF/jars/') and 'energy' in n]
    require(len(bundled) == 1, f'Fabric JAR must bundle Team Reborn Energy, found {bundled}')
    require(not any(n.startswith('META-INF/jars/') for n in a.namelist()), 'Forge JAR must not bundle Fabric libraries')

    for jar, package in ((a, LOADER_PACKAGES[1]), (b, LOADER_PACKAGES[0])):
        require(not any(n.startswith(package) for n in jar.namelist()), f'{package} leaked into the wrong loader')
    parity.check_cross_loader(a, b, forge_forbidden=(b'net/fabricmc/', b'team/reborn/'), fail=require)
    for jar in (a, b):
        require(MIXIN_CONFIG in jar.namelist() and MIXIN_REFMAP in jar.namelist(), 'missing the shared mixin config or its refmap')
    # Loom rewrites the config's formatting.
    require(json.loads(a.read(MIXIN_CONFIG)) == json.loads(b.read(MIXIN_CONFIG)), 'mixin config differs')
    require(MIXIN_CONFIG in a.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', ''), 'Forge manifest must list the mixin config')
    require(MIXIN_CONFIG in bm.get('mixins', []), 'fabric.mod.json must list the mixin config')
    for jar, package in ((a, LOADER_PACKAGES[0]), (b, LOADER_PACKAGES[1])):
        require(any(n.startswith(package) for n in jar.namelist()), f'{package} adapters are missing')

    classes = shared_classes(a)
    require(classes == shared_classes(b), 'shared gameplay class set differs: '
            + ', '.join(sorted(classes ^ shared_classes(b))[:10]))
    parity.check_gametests((a, b), *GAMETEST_SOURCES, entry_substrings=('gametest',),
                           entry_suffixes=(), entry_prefixes=(), fail=require)

    forge_paths = parity.gameplay_paths(a)
    require(FORGE_ONLY <= forge_paths, 'Forge biome modifier missing')
    paths = forge_paths - FORGE_ONLY
    require(paths == parity.gameplay_paths(b), 'gameplay resource path mismatch: '
            + ', '.join(sorted(paths ^ parity.gameplay_paths(b))[:10]))
    for name in sorted(paths):
        require(a.read(name) == b.read(name), f'gameplay resource differs: {name}')
    for name in ('data/buildcraft/pe_custom_conversions/buildcraft.json', 'data/buildcraft/default_emcs/buildcraft.json'):
        require(name in paths, f'missing EMC data {name}')
    for jar in (a, b):
        require('META-INF/BuildCraft-UPSTREAM.md' in jar.namelist(), 'missing upstream attribution')
        require(any(n.startswith('META-INF/licenses/buildcraft/') for n in jar.namelist()), 'missing upstream licenses')
    print(f'PASS: {len(classes)} shared classes and {len(paths)} identical gameplay resources; '
          'loader isolation, metadata, attribution and bundled Team Reborn Energy valid.')
parity.print_hashes(forge, fabric)
