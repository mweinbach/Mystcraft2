#!/usr/bin/env python3
"""Check that the Forge and Fabric variants of Extreme Reactors ship the same mod.

Run after both builds. Checks the two jars (descriptors, metadata, loader isolation, identical shared resources and
the Fabric refmap) and the sources (same-name platform classes, forwarded game tests, ore generation and the forge:
tags the shared code and recipes rely on).
"""
from pathlib import Path
import hashlib
import json
import re
import tomllib
import zipfile

MOD = Path(__file__).resolve().parents[1]
SRC = MOD / 'src'
MOD_ID = 'bigreactors'
metadata = dict(line.split('=', 1) for line in (MOD / 'port.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'extremereactors-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'extremereactors-fabric-1.19.2-{version}.jar'

# Resources only one loader reads: Forge biome modifiers (Fabric adds the same features in FabricMod), the Fabric
# bridges that give forge: tags their vanilla members and c: equivalents, and the Fabric stand-in for Forge's dynamic
# bucket model that the shared bucket item models use as their parent.
FORGE_ONLY = ('data/bigreactors/forge/biome_modifier/',)
FABRIC_ONLY = ('data/forge/tags/', 'assets/forge/models/item/bucket_drip.json',
               'assets/bigreactors/textures/item/bucket_fluid_overlay.png')
# Referenced by upstream with a typo (storage_block, not storage_blocks): they never match on Forge either.
UPSTREAM_UNDEFINED_TAGS = {('blocks', 'storage_block/apatite'), ('blocks', 'storage_block/cinnabar')}

failures = []


def require(condition, message):
    if not condition:
        failures.append(message)


def check_artifacts():
    with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
        require(a.testzip() is None and b.testzip() is None, 'corrupt artifact')
        require('fabric.mod.json' not in a.namelist(), 'Fabric descriptor leaked into Forge')
        require('META-INF/mods.toml' not in b.namelist(), 'Forge descriptor leaked into Fabric')
        forge_toml = tomllib.loads(a.read('META-INF/mods.toml').decode())
        forge_mod = forge_toml['mods'][0]
        fabric_mod = json.loads(b.read('fabric.mod.json'))
        require(forge_mod['modId'] == fabric_mod['id'] == MOD_ID, 'mod id mismatch')
        require(forge_mod['version'] == fabric_mod['version'] == version, 'version mismatch')
        require(forge_toml.get('license') == fabric_mod['license'] == metadata['mod_license'], 'license mismatch')

        for jar, forbidden in ((a, (b'net/fabricmc/', b'team/reborn/')), (b, (b'net/minecraftforge/',))):
            for name in jar.namelist():
                if name.endswith('.class'):
                    content = jar.read(name)
                    for prefix in forbidden:
                        require(prefix not in content, f'wrong-loader reference {prefix.decode()} in {name}')

        def shared(jar):
            return {n for n in jar.namelist() if n.startswith(('assets/', 'data/')) and not n.endswith('/')}

        forge_paths, fabric_paths = shared(a), shared(b)
        for name in sorted(forge_paths - fabric_paths):
            require(name.startswith(FORGE_ONLY), f'only in the Forge jar: {name}')
        for name in sorted(fabric_paths - forge_paths):
            require(name.startswith(FABRIC_ONLY), f'only in the Fabric jar: {name}')
        common = forge_paths & fabric_paths
        for name in sorted(common):
            require(a.read(name) == b.read(name), f'shared resource differs: {name}')

        mixins = json.loads(b.read('extremereactors.fabric.mixins.json'))
        require(mixins.get('required') is True, 'the Fabric mixins must be required')
        refmap = json.loads(b.read(mixins['refmap']))['mappings'] if 'refmap' in mixins else {}
        for name in mixins.get('mixins', []) + mixins.get('client', []):
            mapping = json.dumps(refmap.get('ports/extremereactors/fabric/mixin/' + name, {}))
            require('method_' in mapping or 'field_' in mapping, f'missing production mapping for mixin {name}')

        return len(common)


def java_sources(directory):
    return {p.relative_to(directory).as_posix() for p in directory.rglob('*.java')}


def check_platform_classes():
    package = Path('java/ports/extremereactors/platform')
    forge_classes = java_sources(SRC / 'forge' / package)
    fabric_classes = java_sources(SRC / 'fabric' / package)
    require(forge_classes == fabric_classes,
            f'platform classes differ: Forge-only {sorted(forge_classes - fabric_classes)}, '
            f'Fabric-only {sorted(fabric_classes - forge_classes)}')
    shared = java_sources(SRC / 'main' / package)
    require(not (shared & forge_classes), f'platform classes both shared and per loader: {sorted(shared & forge_classes)}')
    return len(forge_classes)


GAMETEST = re.compile(r'@GameTest\(([^)]*)\)\s*public (?:static )?void (\w+)\(')


def loader_game_tests(path):
    tests = {}
    for annotation, name in GAMETEST.findall(path.read_text()):
        timeout = re.search(r'timeoutTicks\s*=\s*([\w.]+)', annotation)
        tests[name] = timeout.group(1) if timeout else None
    return tests


def check_game_tests():
    package = 'java/ports/extremereactors/gametest'
    shared_source = (SRC / 'gametestCommon' / package / 'ExtremeReactorsGameTests.java').read_text()
    shared = set(re.findall(r'public static void (\w+)\(final GameTestHelper helper', shared_source))
    forge_tests = loader_game_tests(SRC / 'gametestForge' / package / 'ExtremeReactorsForgeGameTests.java')
    fabric_tests = loader_game_tests(SRC / 'gametestFabric' / package / 'ExtremeReactorsFabricGameTests.java')
    require(forge_tests == fabric_tests, f'the loaders run different game tests or timeouts: '
            f'Forge-only {sorted(set(forge_tests.items()) - set(fabric_tests.items()))}, '
            f'Fabric-only {sorted(set(fabric_tests.items()) - set(forge_tests.items()))}')
    for name in sorted(shared - set(forge_tests)):
        require(False, f'shared game test {name} is not run by the loaders')
    return len(forge_tests)


def check_ore_generation():
    modifiers = set()
    for path in (SRC / 'forge/resources/data' / MOD_ID / 'forge/biome_modifier').glob('*.json'):
        modifier = json.loads(path.read_text())
        biomes = modifier['biomes'].removeprefix('#minecraft:').upper()
        modifiers.add((biomes, modifier['feature'].removeprefix(MOD_ID + ':')))
    fabric_mod = (SRC / 'fabric/java/ports/extremereactors/fabric/FabricMod.java').read_text()
    fabric_ores = set(re.findall(r'addOre\(BiomeTags\.(\w+), "(\w+)"\)', fabric_mod))
    require(modifiers == fabric_ores, f'ore generation differs: Forge-only {sorted(modifiers - fabric_ores)}, '
            f'Fabric-only {sorted(fabric_ores - modifiers)}')
    return len(modifiers)


def defined_forge_tags(resources):
    tags = set()
    base = resources / 'data/forge/tags'
    for path in base.rglob('*.json'):
        kind, *rest = path.relative_to(base).with_suffix('').parts
        tags.add((kind, '/'.join(rest)))
    return tags


def referenced_forge_tags():
    references = set()
    calls = {'CoilMaterialRegistry.register': 'blocks', 'ModeratorsRegistry.registerSolid': 'blocks',
             'registerIngotReactantMapping': 'items'}
    for path in (SRC / 'main/java').rglob('*.java'):
        for call, kind in calls.items():
            for tag in re.findall(re.escape(call) + r'\((?:[^,()]+,\s*)?"forge:([a-z0-9_/]+)"', path.read_text()):
                references.add((kind, tag))

    def walk(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key in ('conditions', 'fabric:load_conditions'):
                    continue
                if key == 'tag' and isinstance(value, str) and value.startswith('forge:'):
                    references.add(('items', value.removeprefix('forge:')))
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    for path in (SRC / 'main/resources/data').glob('*/recipes/**/*.json'):
        walk(json.loads(path.read_text()))
    return references


def check_forge_tags_on_fabric():
    available = defined_forge_tags(SRC / 'main/resources') | defined_forge_tags(SRC / 'fabric/resources')
    references = referenced_forge_tags()
    for kind, tag in sorted(references - available - UPSTREAM_UNDEFINED_TAGS):
        require(False, f'forge:{tag} ({kind}) is used by shared code or recipes but not defined on Fabric')
    for kind, tag in sorted(UPSTREAM_UNDEFINED_TAGS - references):
        require(False, f'forge:{tag} ({kind}) is no longer referenced; drop it from UPSTREAM_UNDEFINED_TAGS')
    return len(references)


resources = check_artifacts()
platform = check_platform_classes()
tests = check_game_tests()
ores = check_ore_generation()
tags = check_forge_tags_on_fabric()

if failures:
    raise SystemExit('FAIL:\n  ' + '\n  '.join(failures))

print(f'PASS: {resources} identical shared resources, {platform} platform classes on both loaders, '
      f'{tests} game tests forwarded by both, {ores} ore features on both, {tags} forge: tags resolvable on Fabric.')
for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
