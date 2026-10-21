#!/usr/bin/env python3
"""Validate both built Mekanism loader artifacts and the gameplay they must share.

Shared classes keep the `mekanism` package. Fabric rewrites Forge calls onto
`ports.mekanism.lib` at build time, so those class bytes differ while the class
set must not. Fabric tag bridges replace some shared data files; everything
else under assets/ and data/ is the same bytes in both jars.
"""
from pathlib import Path
import hashlib
import json
import tomllib
import zipfile

MOD = Path(__file__).resolve().parents[1]
metadata = dict(line.split('=', 1) for line in (MOD / 'port.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'mekanism-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'mekanism-fabric-1.19.2-{version}.jar'

MIXIN_CONFIG = 'mekanism.mixins.json'
MIXIN_REFMAP = 'mekanism.refmap.json'
NESTED = (
    'META-INF/jars/energy-2.3.0.jar',
    'META-INF/jars/core-3.6.6.jar',
    'META-INF/jars/toml-3.6.6.jar',
    'META-INF/jars/maven-artifact-3.8.5.jar',
)
EMC = (
    'data/mekanism/pe_custom_conversions/mekanism.json',
    'data/mekanism/default_emcs/mekanism.json',
)
# Forge expands these. pack.mcmeta has no tokens today; mods.toml does.
EXPANDED = {'META-INF/mods.toml', 'pack.mcmeta'}
SHARED_ROOTS = ('assets/', 'data/')
ATTRIBUTION = 'META-INF/Mekanism-UPSTREAM.md'


def require(condition, message):
    if not condition:
        raise SystemExit(f'FAIL: {message}')


def files_under(root):
    if not root.is_dir():
        return {}
    return {path.relative_to(root).as_posix(): path.read_bytes()
            for path in root.rglob('*') if path.is_file()}


def gameplay(names):
    return {name for name in names
            if not name.endswith('/') and (name.startswith(SHARED_ROOTS) or name in ('pack.mcmeta', 'logo.png'))}


def outer_class(name):
    return name.removesuffix('.class').split('$', 1)[0]


main = files_under(MOD / 'src/main/resources')
fabric_src = files_under(MOD / 'src/fabric/resources')
forge_src = files_under(MOD / 'src/forge/resources')

for path in (forge, fabric):
    require(path.is_file(), f'missing {path.relative_to(MOD)}; build both variants first')

with zipfile.ZipFile(forge) as forge_jar, zipfile.ZipFile(fabric) as fabric_jar:
    require(forge_jar.testzip() is None and fabric_jar.testzip() is None, 'corrupt artifact')
    forge_names = set(forge_jar.namelist())
    fabric_names = set(fabric_jar.namelist())
    require('fabric.mod.json' not in forge_names, 'Fabric descriptor leaked into Forge')
    require('META-INF/mods.toml' not in fabric_names, 'Forge descriptor leaked into Fabric')
    require(MIXIN_CONFIG not in forge_names and MIXIN_REFMAP not in forge_names,
            'Fabric mixin config leaked into Forge')

    forge_mod = tomllib.loads(forge_jar.read('META-INF/mods.toml').decode())['mods'][0]
    fabric_mod = json.loads(fabric_jar.read('fabric.mod.json'))
    require(forge_mod['modId'] == fabric_mod['id'] == metadata['mod_id'], 'mod ID mismatch')
    require(forge_mod['version'] == fabric_mod['version'] == version, 'version mismatch')
    require(fabric_mod['depends'].get('team_reborn_energy'), 'Fabric build must depend on Team Reborn Energy')
    require(fabric_mod.get('mixins') == [MIXIN_CONFIG], 'fabric.mod.json must list the mixin config')
    require(fabric_mod.get('accessWidener') == 'mekanism.accesswidener', 'missing access widener')

    nested = [name for name in fabric_names if name.startswith('META-INF/jars/') and name.endswith('.jar')]
    require(sorted(nested) == sorted(NESTED), f'Fabric nested libraries: {nested}')
    declared = [entry.get('file') for entry in fabric_mod.get('jars', [])]
    require(sorted(declared) == sorted(NESTED), f'fabric.mod.json jars: {declared}')
    require(not any(name.startswith('META-INF/jars/') for name in forge_names), 'Forge JAR must not nest libraries')

    for jar, forbidden, label in (
            (forge_jar, (b'net/fabricmc/', b'team/reborn/'), 'Forge'),
            (fabric_jar, (b'net/minecraftforge/',), 'Fabric')):
        for name in jar.namelist():
            if name.endswith('.class'):
                body = jar.read(name)
                for marker in forbidden:
                    require(marker not in body, f'{label} class references {marker.decode()} in {name}')
                require('gametest' not in name.lower(), f'GameTest class shipped: {name}')
            require('/gametest/' not in name and not name.endswith('.snbt'), f'GameTest fixture shipped: {name}')

    forge_classes = {name for name in forge_names if name.endswith('.class') and name.startswith('mekanism/')}
    fabric_classes = {name for name in fabric_names if name.endswith('.class') and name.startswith('mekanism/')}
    require(forge_classes == fabric_classes,
            'shared class set differs: ' + ', '.join(sorted(forge_classes ^ fabric_classes)[:8]))
    require(not any(name.startswith('ports/mekanism/') for name in forge_names),
            'Fabric adapter leaked into Forge')
    for package in ('ports/mekanism/lib/', 'ports/mekanism/fabric/'):
        require(any(name.startswith(package) for name in fabric_names), f'missing {package}')

    config = json.loads(fabric_jar.read(MIXIN_CONFIG))
    require(config.get('required') is True, 'mixin config must be required')
    require(config.get('package') == 'ports.mekanism.fabric.mixin', 'mixin package changed')
    require(config.get('refmap') == MIXIN_REFMAP, 'mixin refmap name changed')
    mixin_names = config.get('mixins', []) + config.get('client', [])
    package = config['package'].replace('.', '/') + '/'
    declared_classes = {package + name.replace('.', '/') for name in mixin_names}
    for name in fabric_names:
        if name.startswith(package) and name.endswith('.class'):
            require(outer_class(name) in declared_classes, f'non-mixin class in the mixin package: {name}')
    for name in declared_classes:
        require(name + '.class' in fabric_names, f'declared mixin missing: {name}')
    # Interface mixins only add a Forge-shaped interface, so they have no member to remap.
    # Injected members must be intermediary names; an empty entry is not a dev-named mapping.
    mappings = json.loads(fabric_jar.read(MIXIN_REFMAP))['mappings']
    require(any('method_' in json.dumps(entry) or 'field_' in json.dumps(entry) for entry in mappings.values()),
            'mixin refmap has no production mappings')
    for name in declared_classes:
        mapped = json.dumps(mappings.get(name, {}))
        if mapped not in ('{}', 'null'):
            require(any(prefix in mapped for prefix in ('method_', 'field_', 'class_')),
                    f'mixin mapping is not intermediary: {name}')

    expected_forge = gameplay(main) | gameplay(forge_src)
    expected_fabric = (expected_forge - set(fabric_src)) | gameplay(fabric_src)
    # A Fabric file at the same path replaces the shared one. Forge keeps the shared bytes.
    forge_paths = gameplay(forge_names)
    fabric_paths = gameplay(fabric_names)
    require(forge_paths == expected_forge,
            'Forge gameplay paths differ from sources: ' + ', '.join(sorted(forge_paths ^ expected_forge)[:8]))
    require(fabric_paths == expected_fabric,
            'Fabric gameplay paths differ from sources: ' + ', '.join(sorted(fabric_paths ^ expected_fabric)[:8]))
    shared_paths = forge_paths & fabric_paths
    overrides = shared_paths & set(fabric_src)
    for name in sorted(shared_paths - overrides):
        require(forge_jar.read(name) == fabric_jar.read(name), f'shared resource differs: {name}')
        if name not in EXPANDED and name in main:
            require(forge_jar.read(name) == main[name], f'Forge resource does not match src/main: {name}')
    for name in sorted(overrides):
        require(forge_jar.read(name) == main[name], f'Forge should keep the shared file: {name}')
        require(fabric_jar.read(name) == fabric_src[name], f'Fabric should use its tag bridge: {name}')
    for name in EMC:
        require(name in shared_paths and name not in overrides, f'EMC data must be shared and identical: {name}')

    for jar in (forge_jar, fabric_jar):
        require(ATTRIBUTION in jar.namelist(), 'missing upstream attribution')
        require(jar.read(ATTRIBUTION) == (MOD / 'UPSTREAM.md').read_bytes(), 'UPSTREAM.md in the jar does not match')
        require('LICENSE' in jar.namelist() and jar.read('LICENSE') == (MOD / 'LICENSE').read_bytes(),
                'LICENSE mismatch')
    for name in ('Mekanism-LICENSE.txt', 'PORT-LICENSE.txt'):
        entry = f'META-INF/licenses/mekanism/{name}'
        require(forge_jar.read(entry) == fabric_jar.read(entry) == (MOD / 'licenses' / name).read_bytes(),
                f'license mismatch: {name}')
    forge_license = 'META-INF/licenses/mekanism/Forge-LICENSE.txt'
    require(forge_license not in forge_names, 'Forge jar should not carry the Forge bucket-asset license')
    require(fabric_jar.read(forge_license) == (MOD / 'licenses' / 'Forge-LICENSE.txt').read_bytes(),
            'Fabric jar must carry the Forge bucket-asset license')

    identical = len(shared_paths - overrides)
    print(f'PASS: {len(forge_classes)} shared classes, {identical} identical gameplay resources, '
          f'{len(overrides)} Fabric tag-bridge overrides; loader isolation, mixins, nested libraries, '
          'EMC data and attribution valid.')


MODULES = (
    ('generators', 'mekanismgenerators', True),
    ('tools', 'mekanismtools', True),
    ('additions', 'mekanismadditions', False),
)


def module_jars(name, mod_id):
    return (MOD / 'build/libs' / f'{mod_id}-forge-1.19.2-{version}.jar',
            MOD / 'fabric/build/libs' / f'{mod_id}-fabric-1.19.2-{version}.jar')


def check_module(name, mod_id, expect_emc):
    """One extra mod id: its own jar on each loader, sharing the module's classes and resources."""
    forge_path, fabric_path = module_jars(name, mod_id)
    for path in (forge_path, fabric_path):
        require(path.is_file(), f'missing {path.relative_to(MOD)}; build both variants first')
    shared = files_under(MOD / 'src' / name / 'resources')
    prefix = f'mekanism/{name}/'
    entry = f'ports/mekanism/{name}/fabric/'
    emc_names = (
        f'data/{mod_id}/pe_custom_conversions/{mod_id}.json',
        f'data/{mod_id}/default_emcs/{mod_id}.json',
    )
    with zipfile.ZipFile(forge_path) as forge_jar, zipfile.ZipFile(fabric_path) as fabric_jar:
        require(forge_jar.testzip() is None and fabric_jar.testzip() is None, f'{mod_id}: corrupt artifact')
        forge_names = set(forge_jar.namelist())
        fabric_names = set(fabric_jar.namelist())
        require('fabric.mod.json' not in forge_names, f'{mod_id}: Fabric descriptor leaked into Forge')
        require('META-INF/mods.toml' not in fabric_names, f'{mod_id}: Forge descriptor leaked into Fabric')
        forge_mod = tomllib.loads(forge_jar.read('META-INF/mods.toml').decode())['mods'][0]
        fabric_mod = json.loads(fabric_jar.read('fabric.mod.json'))
        require(forge_mod['modId'] == fabric_mod['id'] == mod_id, f'{mod_id}: mod ID mismatch')
        require(forge_mod['version'] == fabric_mod['version'] == version, f'{mod_id}: version mismatch')
        require(fabric_mod['depends'].get('mekanism'), f'{mod_id}: Fabric jar must depend on mekanism')
        for jar, forbidden, label in (
                (forge_jar, (b'net/fabricmc/', b'team/reborn/'), 'Forge'),
                (fabric_jar, (b'net/minecraftforge/',), 'Fabric')):
            for entry_name in jar.namelist():
                if entry_name.endswith('.class'):
                    body = jar.read(entry_name)
                    for marker in forbidden:
                        require(marker not in body, f'{mod_id} {label} class references {marker.decode()} in {entry_name}')
                    require('gametest' not in entry_name.lower(), f'{mod_id}: GameTest class shipped: {entry_name}')
                require('/gametest/' not in entry_name and not entry_name.endswith('.snbt'),
                        f'{mod_id}: GameTest fixture shipped: {entry_name}')
        forge_classes = {entry_name for entry_name in forge_names if entry_name.endswith('.class') and entry_name.startswith(prefix)}
        fabric_classes = {entry_name for entry_name in fabric_names if entry_name.endswith('.class') and entry_name.startswith(prefix)}
        require(forge_classes and forge_classes == fabric_classes,
                f'{mod_id}: shared class set differs: ' + ', '.join(sorted(forge_classes ^ fabric_classes)[:8]))
        require(not any(entry_name.startswith('ports/') for entry_name in forge_names),
                f'{mod_id}: Fabric adapter leaked into Forge')
        require(any(entry_name.startswith(entry) and entry_name.endswith('.class') for entry_name in fabric_names),
                f'{mod_id}: missing Fabric entrypoint under {entry}')
        forge_paths = gameplay(forge_names)
        fabric_paths = gameplay(fabric_names)
        expected = gameplay(shared) | {'logo.png'}
        require(forge_paths == fabric_paths == expected,
                f'{mod_id}: gameplay paths differ: ' + ', '.join(sorted((forge_paths ^ expected) | (fabric_paths ^ expected))[:8]))
        for entry_name in sorted(expected - {'logo.png'}):
            require(forge_jar.read(entry_name) == fabric_jar.read(entry_name) == shared[entry_name],
                    f'{mod_id}: shared resource differs: {entry_name}')
        require(forge_jar.read('logo.png') == fabric_jar.read('logo.png') == (MOD / 'src/main/resources/logo.png').read_bytes(),
                f'{mod_id}: logo mismatch')
        for entry_name in emc_names:
            present = entry_name in forge_paths and forge_jar.read(entry_name) == fabric_jar.read(entry_name)
            require(present == expect_emc, f'{mod_id}: EMC data {"missing" if expect_emc else "unexpected"}: {entry_name}')
        for jar in (forge_jar, fabric_jar):
            require(ATTRIBUTION in jar.namelist(), f'{mod_id}: missing upstream attribution')
            require(jar.read(ATTRIBUTION) == (MOD / 'UPSTREAM.md').read_bytes(), f'{mod_id}: UPSTREAM.md mismatch')
            require('LICENSE' in jar.namelist() and jar.read('LICENSE') == (MOD / 'LICENSE').read_bytes(),
                    f'{mod_id}: LICENSE mismatch')
    print(f'PASS: {mod_id} {len(forge_classes)} shared classes, {len(expected)} gameplay resources.')
    for path in (forge_path, fabric_path):
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')


for module_name, module_id, module_emc in MODULES:
    check_module(module_name, module_id, module_emc)

for path in (forge, fabric):
    print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
