#!/usr/bin/env python3
"""Validate both built ProjectE loader artifacts and the gameplay they must share.

Shared classes keep upstream's `moze_intel.projecte` packages. Fabric rewrites Forge calls onto `ports.projecte.lib`
at build time, so those class bytes differ while the class set must not. Fabric tag bridges replace some shared
`forge:` tag files and add the rest; everything else under assets/ and data/ is the same bytes in both jars.
Adapted from the Mekanism port's gate (mods/mekanism/scripts/check_loader_parity.py), MIT.
"""
from pathlib import Path
import json
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
# The shaded jar is the published Forge artifact, as upstream's (Commons Math relocated into it).
forge = MOD / 'build/libs' / f'projecte-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'projecte-fabric-1.19.2-{version}.jar'

MIXIN_CONFIG = 'projecte.mixins.json'
MIXIN_REFMAP = 'projecte.refmap.json'
NESTED = parity.included_libraries(MOD / 'fabric')
NOTICE = 'META-INF/ProjectE-NOTICE.md'
ATTRIBUTION = 'META-INF/ProjectE-UPSTREAM.md'
LICENSES = 'META-INF/licenses/projecte/'
GAMETEST_ROOTS = (MOD / 'src/gametestCommon/java', MOD / 'src/gametestForge/java', MOD / 'src/gametestFabric/java')
SHADED = 'moze_intel/projecte/shaded/'
# Integrations whose mods have no Fabric 1.19.2 build (UPSTREAM.md, "Forge-only and Fabric-only classes").
FORGE_ONLY = (
    'moze_intel/projecte/integration/top/',
    'moze_intel/projecte/integration/crafttweaker/nss/ExpandCrTTypes$IFluidStackExpansion',
    SHADED,
)
# The Trinkets-backed replacements of the Curios classes keep upstream's names; only their inner classes differ.
FABRIC_ONLY = ('moze_intel/projecte/integration/curios/',)
SAME_NAME_REPLACED = ('moze_intel/projecte/integration/curios/',)
require = parity.require


class SourceTree(dict):
    """A source tree's ``relative path -> bytes`` mapping, listable like a jar."""

    def namelist(self):
        return list(self)


def files_under(root):
    if not root.is_dir():
        return SourceTree()
    return SourceTree((path.relative_to(root).as_posix(), path.read_bytes())
                      for path in root.rglob('*') if path.is_file())


def gameplay(entries):
    return parity.gameplay_paths(entries, extra=('pack.mcmeta', 'logo.png'))


main = files_under(MOD / 'src/main/resources')
fabric_src = files_under(MOD / 'src/fabric/resources')
forge_src = files_under(MOD / 'src/forge/resources')

parity.assert_built(forge, fabric)
with zipfile.ZipFile(forge) as forge_jar, zipfile.ZipFile(fabric) as fabric_jar:
    require(forge_jar.testzip() is None and fabric_jar.testzip() is None, 'corrupt artifact')
    forge_names = set(forge_jar.namelist())
    fabric_names = set(fabric_jar.namelist())

    forge_mod, fabric_mod = parity.check_descriptors(
        forge_jar, fabric_jar, mod_id=metadata['mod_id'], version=version, license_id=metadata['mod_license'])
    require(MIXIN_CONFIG not in forge_names and MIXIN_REFMAP not in forge_names, 'Fabric mixin config leaked into Forge')
    require(fabric_mod.get('mixins') == [MIXIN_CONFIG], 'fabric.mod.json must list only the mixin config')
    require(fabric_mod.get('accessWidener') == 'projecte.accesswidener', 'missing access widener')

    shared_checks = parity.check_artifact_pair(forge_jar, fabric_jar, fabric_mod, build_dir=MOD / 'fabric',
                                               src=MOD / 'src', nested=NESTED, label='projecte')
    parity.check_cross_loader(forge_jar, fabric_jar)
    parity.check_gametests((forge_jar, fabric_jar), *GAMETEST_ROOTS, class_substrings=('gametest',))
    tests, loader_only = parity.check_loader_gametests(MOD)

    def shared_classes(names, excluded):
        return {name for name in names if name.endswith('.class') and name.startswith('moze_intel/')
                and not name.startswith(excluded)}
    forge_classes = shared_classes(forge_names, FORGE_ONLY + SAME_NAME_REPLACED)
    fabric_classes = shared_classes(fabric_names, FABRIC_ONLY)
    require(forge_classes == fabric_classes,
            'shared class set differs: ' + ', '.join(sorted(forge_classes ^ fabric_classes)[:8]))
    for prefix in FORGE_ONLY:
        require(any(name.startswith(prefix) for name in forge_names), f'Forge jar is missing {prefix}')
        require(not any(name.startswith(prefix) for name in fabric_names), f'Forge-only class leaked into Fabric: {prefix}')
    for name in ('CuriosIntegration', 'CurioItemCapability'):
        entry = f'moze_intel/projecte/integration/curios/{name}.class'
        require(entry in forge_names and entry in fabric_names, f'{name} missing from a jar')
        require(b'top/theillusivec4/curios' in forge_jar.read(entry), f'Forge {name} should use Curios')
        require(b'dev/emi/trinkets' in fabric_jar.read(entry), f'Fabric {name} should use Trinkets')
    require(not any(name.startswith('ports/') for name in forge_names), 'Fabric adapter leaked into Forge')
    for package in ('ports/projecte/lib/', 'ports/projecte/fabric/'):
        require(any(name.startswith(package) for name in fabric_names), f'missing {package}')

    config = json.loads(fabric_jar.read(MIXIN_CONFIG))
    require(config.get('required') is True, 'mixin config must be required')
    require(config.get('package') == 'ports.projecte.fabric.mixin', 'mixin package changed')
    require(config.get('refmap') == MIXIN_REFMAP, 'mixin refmap name changed')
    package = config['package'].replace('.', '/') + '/'
    declared_classes = {package + name.replace('.', '/') for name in config.get('mixins', []) + config.get('client', [])}
    for name in fabric_names:
        if name.startswith(package) and name.endswith('.class'):
            require(parity.outer_class(name) in declared_classes, f'non-mixin class in the mixin package: {name}')
    for name in declared_classes:
        require(name + '.class' in fabric_names, f'declared mixin missing: {name}')
    mappings = json.loads(fabric_jar.read(MIXIN_REFMAP))['mappings']
    for name in declared_classes:
        mapped = json.dumps(mappings.get(name, {}))
        if mapped not in ('{}', 'null'):
            require(any(prefix in mapped for prefix in ('method_', 'field_', 'class_')),
                    f'mixin mapping is not intermediary: {name}')

    # A Fabric file at the same path replaces the shared one; Forge keeps the shared bytes.
    expected_forge = gameplay(main) | gameplay(forge_src)
    expected_fabric = (expected_forge - set(fabric_src)) | gameplay(fabric_src)
    forge_paths = gameplay(forge_jar)
    fabric_paths = gameplay(fabric_jar)
    require(forge_paths == expected_forge,
            'Forge gameplay paths differ from sources: ' + ', '.join(sorted(forge_paths ^ expected_forge)[:8]))
    require(fabric_paths == expected_fabric,
            'Fabric gameplay paths differ from sources: ' + ', '.join(sorted(fabric_paths ^ expected_fabric)[:8]))
    bridges = gameplay(fabric_src)
    outside = sorted(path for path in bridges if not path.startswith(('data/c/tags/', 'data/forge/tags/')))
    require(not outside, 'Fabric-only resources outside the tag bridges: ' + ', '.join(outside[:8]))
    shared_paths = forge_paths & fabric_paths
    overrides = shared_paths & set(fabric_src)
    for name in sorted(shared_paths - overrides):
        require(forge_jar.read(name) == fabric_jar.read(name), f'shared resource differs: {name}')
        if name in main:
            require(forge_jar.read(name) == main[name], f'Forge resource does not match src/main: {name}')
    for name in sorted(overrides):
        require(forge_jar.read(name) == main[name], f'Forge should keep the shared file: {name}')
        require(fabric_jar.read(name) == fabric_src[name], f'Fabric should use its tag bridge: {name}')

    for jar in (forge_jar, fabric_jar):
        names = jar.namelist()
        require('LICENSE' in names and jar.read('LICENSE') == (MOD / 'LICENSE').read_bytes(), 'upstream LICENSE mismatch')
        require(NOTICE in names and jar.read(NOTICE) == (MOD / 'NOTICE.md').read_bytes(), 'NOTICE.md missing or different')
        require(ATTRIBUTION in names and jar.read(ATTRIBUTION) == (MOD / 'UPSTREAM.md').read_bytes(),
                'UPSTREAM.md missing or different')
    for path in sorted((MOD / 'licenses').iterdir()):
        entry = LICENSES + path.name
        require(fabric_jar.read(entry) == path.read_bytes(), f'Fabric license mismatch: {path.name}')
        if path.name == 'Forge-LICENSE.txt':
            require(entry not in forge_names, 'Forge jar should not carry the license of the Fabric-only Forge tag values')
        else:
            require(forge_jar.read(entry) == path.read_bytes(), f'Forge license mismatch: {path.name}')

    print(f'PASS: {len(forge_classes)} shared classes, {len(shared_paths - overrides)} identical gameplay resources, '
          f'{len(overrides)} Fabric tag-bridge overrides, {len(bridges - overrides)} Fabric-only tag bridges; '
          f'{parity.summary(shared_checks)}; {tests} GameTests on both loaders, {loader_only} allowlisted to one; '
          'loader isolation, mixins, nested libraries and attribution valid.')
parity.print_hashes(forge, fabric)
