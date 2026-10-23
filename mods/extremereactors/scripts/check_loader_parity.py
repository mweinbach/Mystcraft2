#!/usr/bin/env python3
"""Check that the Forge and Fabric variants of Extreme Reactors ship the same mod.

Run after both builds. Checks the two jars (descriptors, metadata, bundled libraries, the access widener, loader
isolation, identical shared resources and the Fabric refmap) and the sources (same-name platform classes, forwarded
game tests, ore generation, the forge: tags the shared code and recipes rely on, and c:steel_ingots in the Fabric
steel gates).
"""
from pathlib import Path
import json
import re
import sys
import tomllib
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
SRC = MOD / 'src'
MOD_ID = 'bigreactors'
WIDENER = 'extremereactors.accesswidener'
GAMETEST_ROOTS = tuple(SRC / name / 'java' for name in ('gametestCommon', 'gametestForge', 'gametestFabric'))

metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'extremereactors-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'extremereactors-fabric-1.19.2-{version}.jar'


def bundled_libraries():
    """The names Loom gives the include(...) dependencies of fabric/build.gradle under META-INF/jars.

    A plain Maven dependency keeps its artifact file name (energy-2.3.0.jar). Fluid Physics comes from the included
    build, so its version comes from that build's port.properties and its archivesName already carries the Minecraft
    version. Both are resolved rather than restated so a version bump cannot skip the check.
    """
    included_builds = {'ports.fluidphysics:fluidphysics-fabric': MOD.parent / 'fluidphysics'}
    libraries = []
    for coordinate in re.findall(r"include\(modImplementation\(['\"]([^'\"]+)['\"]",
                                 (MOD / 'fabric/build.gradle').read_text()):
        module, artifact, declared = coordinate.split(':')
        if declared.startswith('${'):
            project = included_builds[f'{module}:{artifact}']
            declared = parity.read_properties(project / 'port.properties')['mod_version']
            archive = re.search(r"base\.archivesName\s*=\s*['\"]([^'\"]+)['\"]",
                                (project / 'build.gradle').read_text()).group(1)
            libraries.append(f'{archive}-{declared}.jar')
        else:
            libraries.append(f'{artifact}-{declared}.jar')
    return tuple(f'META-INF/jars/{name}' for name in libraries)


NESTED = bundled_libraries()

# Resources only one loader reads: Forge biome modifiers (Fabric adds the same features in FabricMod), the bridges
# between the two loaders' steel tags (one per loader, as both would form a cycle), the Fabric bridges that give forge:
# tags their vanilla members and c: equivalents, and the Fabric stand-in for Forge's dynamic bucket model that the
# shared bucket item models use as their parent.
FORGE_ONLY = ('data/bigreactors/forge/biome_modifier/', 'data/c/tags/items/steel_ingots.json')
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
        _forge_mod, fabric_mod = parity.check_descriptors(
            a, b, mod_id=MOD_ID, version=version, license_id=metadata['mod_license'], fail=require)
        # check_descriptors covers fabric.mod.json; the Forge descriptor also carries a top-level license.
        forge_toml = tomllib.loads(a.read('META-INF/mods.toml').decode())
        require(forge_toml.get('license') == metadata['mod_license'], 'license mismatch')

        require(NESTED, 'fabric/build.gradle bundles no include(...) libraries')
        bundled = parity.check_nested_libraries(a, b, fabric_mod, NESTED, fail=require)

        require(fabric_mod.get('accessWidener') == WIDENER, 'fabric.mod.json does not declare the access widener')
        require(WIDENER in b.namelist(), f'the Fabric jar does not contain {WIDENER}')
        if WIDENER in b.namelist():
            require(b.read(WIDENER).strip(), f'the Fabric access widener {WIDENER} is empty')
        require(WIDENER not in a.namelist(), 'the access widener leaked into the Forge jar')

        parity.check_cross_loader(a, b, forge_forbidden=(b'net/fabricmc/', b'team/reborn/'), fail=require)
        parity.check_gametests((a, b), *GAMETEST_ROOTS,
                               class_substrings=('gametest',), fail=require)

        forge_paths, fabric_paths = parity.gameplay_paths(a), parity.gameplay_paths(b)
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

        return len(common), bundled


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
        ore = modifier['type'].removeprefix(MOD_ID + ':oregen_')
        modifiers.add((biomes, modifier['feature'].removeprefix(MOD_ID + ':'), ore))
    fabric_mod = (SRC / 'fabric/java/ports/extremereactors/fabric/FabricMod.java').read_text()
    fabric_ores = {(biomes, feature, ore.lower()) for biomes, feature, ore in
                   re.findall(r'addOre\(BiomeTags\.(\w+), "(\w+)", worldgen::generates(\w+)\)', fabric_mod)}
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


def tag_names(node):
    return set(re.findall(r'"((?:forge|c):[a-z0-9_/]+)"', json.dumps(node)))


def check_steel_recipes():
    """Fabric steel mods tag c:steel_ingots, so the Fabric load conditions and the ingredients must name it."""
    gated = 0
    for path in (SRC / 'main/resources/data').glob('*/recipes/**/*.json'):
        recipe = json.loads(path.read_text())
        name = path.relative_to(SRC / 'main/resources/data')
        fabric_tags = tag_names(recipe.get('fabric:load_conditions', []))
        require(not any(tag.startswith('forge:') for tag in fabric_tags),
                f'{name}: Fabric load conditions name a forge: tag')
        if 'forge:ingots/steel' not in tag_names(recipe.get('conditions', [])):
            continue
        gated += 1
        require(fabric_tags == {'c:steel_ingots'}, f'{name}: the Fabric steel gate does not test c:steel_ingots')
        steel = [value for value in recipe.get('key', {}).values() if 'forge:ingots/steel' in tag_names(value)]
        require(steel or 'alt' in path.stem, f'{name}: gated on steel but no ingredient uses it')
        for value in steel:
            require(isinstance(value, list) and {'tag': 'c:steel_ingots'} in value,
                    f'{name}: a steel ingredient does not accept c:steel_ingots')
    return gated


resources, bundled = check_artifacts()
platform = check_platform_classes()
tests = check_game_tests()
ores = check_ore_generation()
tags = check_forge_tags_on_fabric()
steel = check_steel_recipes()

if failures:
    raise SystemExit('FAIL:\n  ' + '\n  '.join(failures))

print(f'PASS: {resources} identical shared resources, {platform} platform classes on both loaders, '
      f'{tests} game tests forwarded by both, {ores} ore features on both, '
      f'{bundled} bundled libraries and the access widener on Fabric, '
      f'{tags} forge: tags resolvable on Fabric, {steel} steel-gated recipes testing c:steel_ingots on Fabric.')
parity.print_hashes(forge, fabric)
