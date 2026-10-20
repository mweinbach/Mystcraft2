#!/usr/bin/env python3
"""Validate both built BuildCraft loader artifacts and the gameplay they must share."""
from pathlib import Path
import hashlib
import json
import tomllib
import zipfile

MOD = Path(__file__).resolve().parents[1]
metadata = dict(line.split('=', 1) for line in (MOD / 'port.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'buildcraft-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'buildcraft-fabric-1.19.2-{version}.jar'
SHARED_CLASSES = 'ports/buildcraft/'
LOADER_PACKAGES = ('ports/buildcraft/forge/', 'ports/buildcraft/fabric/')
# Fabric adds the oil well placed feature to biomes in code instead.
FORGE_ONLY = {'data/buildcraft/forge/biome_modifier/oil_well.json'}


def require(condition, message):
    if not condition:
        raise SystemExit(f'FAIL: {message}')


def shared_classes(jar):
    return {n for n in jar.namelist()
            if n.endswith('.class') and n.startswith(SHARED_CLASSES) and not n.startswith(LOADER_PACKAGES)}


def gameplay_resources(jar):
    return {n for n in jar.namelist() if n.startswith(('assets/', 'data/')) and not n.endswith('/')}


for path in (forge, fabric):
    require(path.is_file(), f'missing {path.relative_to(MOD)}; build both variants first')

with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
    require(a.testzip() is None and b.testzip() is None, 'corrupt artifact')
    require('fabric.mod.json' not in a.namelist(), 'Fabric descriptor leaked into Forge')
    require('META-INF/mods.toml' not in b.namelist(), 'Forge descriptor leaked into Fabric')
    fm = tomllib.loads(a.read('META-INF/mods.toml').decode())['mods'][0]
    bm = json.loads(b.read('fabric.mod.json'))
    require(fm['modId'] == bm['id'] == 'buildcraft', 'mod ID mismatch')
    require(fm['version'] == bm['version'] == version, 'version mismatch')
    require(bm['depends'].get('team_reborn_energy'), 'Fabric build must depend on Team Reborn Energy')
    bundled = [n for n in b.namelist() if n.startswith('META-INF/jars/') and 'energy' in n]
    require(len(bundled) == 1, f'Fabric JAR must bundle Team Reborn Energy, found {bundled}')
    require(not any(n.startswith('META-INF/jars/') for n in a.namelist()), 'Forge JAR must not bundle Fabric libraries')

    for jar, forbidden, package in ((a, (b'net/fabricmc/', b'team/reborn/'), LOADER_PACKAGES[1]),
                                    (b, (b'net/minecraftforge/',), LOADER_PACKAGES[0])):
        require(not any(n.startswith(package) for n in jar.namelist()), f'{package} leaked into the wrong loader')
        for name in jar.namelist():
            if name.endswith('.class'):
                body = jar.read(name)
                for marker in forbidden:
                    require(marker not in body, f'wrong-loader reference {marker.decode()} in {name}')
    for jar, package in ((a, LOADER_PACKAGES[0]), (b, LOADER_PACKAGES[1])):
        require(any(n.startswith(package) for n in jar.namelist()), f'{package} adapters are missing')

    classes = shared_classes(a)
    require(classes == shared_classes(b), 'shared gameplay class set differs: '
            + ', '.join(sorted(classes ^ shared_classes(b))[:10]))
    require(not any('gametest' in n for n in a.namelist() + b.namelist()), 'GameTest classes leaked into a JAR')

    forge_paths = gameplay_resources(a)
    require(FORGE_ONLY <= forge_paths, 'Forge biome modifier missing')
    paths = forge_paths - FORGE_ONLY
    require(paths == gameplay_resources(b), 'gameplay resource path mismatch: '
            + ', '.join(sorted(paths ^ gameplay_resources(b))[:10]))
    for name in sorted(paths):
        require(a.read(name) == b.read(name), f'gameplay resource differs: {name}')
    for name in ('data/buildcraft/pe_custom_conversions/buildcraft.json', 'data/buildcraft/default_emcs/buildcraft.json'):
        require(name in paths, f'missing EMC data {name}')
    for jar in (a, b):
        require('META-INF/BuildCraft-UPSTREAM.md' in jar.namelist(), 'missing upstream attribution')
        require(any(n.startswith('META-INF/licenses/buildcraft/') for n in jar.namelist()), 'missing upstream licenses')
    print(f'PASS: {len(classes)} shared classes and {len(paths)} identical gameplay resources; '
          'loader isolation, metadata, attribution and bundled Team Reborn Energy valid.')
for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
