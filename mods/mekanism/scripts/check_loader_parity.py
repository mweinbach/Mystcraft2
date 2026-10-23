#!/usr/bin/env python3
"""Validate both built Mekanism loader artifacts and the gameplay they must share.

Shared classes keep the `mekanism` package. Fabric rewrites Forge calls onto
`ports.mekanism.lib` at build time, so those class bytes differ while the class
set must not. Fabric tag bridges replace some shared data files; everything
else under assets/ and data/ is the same bytes in both jars.
"""
from pathlib import Path
import json
import zipfile

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
import loader_parity as parity

MOD = Path(__file__).resolve().parents[1]
metadata = parity.read_properties(MOD / 'port.properties')
version = metadata['mod_version']
forge = MOD / 'build/libs' / f'mekanism-forge-1.19.2-{version}.jar'
fabric = MOD / 'fabric/build/libs' / f'mekanism-fabric-1.19.2-{version}.jar'

MIXIN_CONFIG = 'mekanism.mixins.json'
MIXIN_REFMAP = 'mekanism.refmap.json'
fluid_physics = parity.read_properties(MOD.parent / 'fluidphysics/port.properties')
NESTED = (
    'META-INF/jars/energy-2.3.0.jar',
    'META-INF/jars/fabric-permissions-api-0.3.1.jar',
    'META-INF/jars/core-3.6.6.jar',
    'META-INF/jars/toml-3.6.6.jar',
    'META-INF/jars/maven-artifact-3.8.5.jar',
    f"META-INF/jars/fluidphysics-fabric-1.19.2-{fluid_physics['mod_version']}.jar",
)
EMC = (
    'data/mekanism/pe_custom_conversions/mekanism.json',
    'data/mekanism/default_emcs/mekanism.json',
)
# Forge expands these. pack.mcmeta has no tokens today; mods.toml does.
EXPANDED = {'META-INF/mods.toml', 'pack.mcmeta'}
ATTRIBUTION = 'META-INF/Mekanism-UPSTREAM.md'
GAMETEST_ROOTS = (
    MOD / 'src/gametestCommon/java',
    MOD / 'src/gametestForge/java',
    MOD / 'src/gametestFabric/java',
)
# CraftTweaker for Fabric 1.19.2 has no fluid stack API (com.blamejared.crafttweaker.api.fluid). The evaporating
# manager (fluid output), the fluid recipe handlers (they decompose through CraftTweaker fluid stacks), the rotary and
# evaporating recipe expansions and the IFluidStack/CTFluidIngredient expansions are Forge-only, as are the ProjectE
# expansions and the content hook that names Forge's mod container. See parity/19-crafttweaker.md.
_CRT = 'mekanism/common/integration/crafttweaker/'
CRAFTTWEAKER_FORGE_ONLY = (
    _CRT + 'projecte/',
    _CRT + 'content/ForgeCrTContentRegistration',
    _CRT + 'CrTIngredientExpansion$IFluidStackExpansion',
    _CRT + 'CrTIngredientExpansion$CTFluidIngredientExpansion',
    _CRT + 'recipe/CrTFluidToFluidRecipe',
    _CRT + 'recipe/CrTRotaryRecipe',
    _CRT + 'recipe/handler/FluidToFluidRecipeHandler',
    _CRT + 'recipe/handler/FluidSlurryToSlurryRecipeHandler',
    _CRT + 'recipe/handler/PressurizedReactionRecipeHandler',
    _CRT + 'recipe/handler/RotaryRecipeHandler',
    _CRT + 'recipe/handler/ElectrolysisRecipeHandler',
    _CRT + 'recipe/manager/FluidToFluidRecipeManager',
)


def require(condition, message):
    if not condition:
        raise SystemExit(f'FAIL: {message}')


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
    """The gameplay paths a built jar or a source tree ships."""
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
        forge_jar, fabric_jar, mod_id=metadata['mod_id'], version=version,
        license_id=metadata['mod_license'], fail=require)
    require(MIXIN_CONFIG not in forge_names and MIXIN_REFMAP not in forge_names,
            'Fabric mixin config leaked into Forge')
    require(fabric_mod['depends'].get('team_reborn_energy'), 'Fabric build must depend on Team Reborn Energy')
    require(fabric_mod['depends'].get('fluidphysics'), 'Fabric build must depend on Fluid Physics')
    require(fabric_mod.get('mixins') == [MIXIN_CONFIG], 'fabric.mod.json must list the mixin config')
    require(fabric_mod.get('accessWidener') == 'mekanism.accesswidener', 'missing access widener')

    shared_checks = parity.check_artifact_pair(forge_jar, fabric_jar, fabric_mod, build_dir=MOD / 'fabric',
                                               src=MOD / 'src', nested=NESTED, label='mekanism', fail=require)

    parity.check_cross_loader(forge_jar, fabric_jar, forge_forbidden=(b'net/fabricmc/', b'team/reborn/'),
                              fail=require)
    parity.check_gametests((forge_jar, fabric_jar), *GAMETEST_ROOTS,
                                  class_substrings=('gametest',), fail=require)
    # One GameTest mod covers Mekanism and its modules on each loader.
    tests, loader_only = parity.check_loader_gametests(MOD, fail=require)

    # Jade and the ComputerCraft peripheral are shared. The One Probe, the Curios adapter and the ProjectE
    # mappers are Forge-only. WTHIT, the Fabric peripheral provider and the Trinkets adapter are Fabric-only, so
    # those packages are not in both JARs. GenderArmorCapability is the same name in both JARs and stays in the
    # shared set.
    forge_only = (
        'mekanism/common/integration/lookingat/theoneprobe/',
        'mekanism/common/integration/curios/ForgeCurios',
        'mekanism/client/render/MekanismCurioRenderer',
        # Forge's capability token is an anonymous class. Fabric's adapter has no inner class.
        'mekanism/common/integration/gender/GenderArmorCapability$',
        'mekanism/common/integration/projecte/',
        # Flux Networks' long energy capability and the Recipe Stages bridge use mods that are Forge-only here.
        'mekanism/common/integration/energy/fluxnetworks/',
        'mekanism/common/integration/recipestages/RecipeStagesBridge',
    ) + CRAFTTWEAKER_FORGE_ONLY
    fabric_only = (
        'mekanism/common/integration/lookingat/wthit/',
        'mekanism/common/integration/computer/computercraft/FabricPeripheralProvider',
        'mekanism/common/integration/curios/FabricTrinkets',
    )
    def shared_classes(names, excluded):
        return {name for name in names if name.endswith('.class') and name.startswith('mekanism/')
                and not name.startswith(excluded)}
    forge_classes = shared_classes(forge_names, forge_only)
    fabric_classes = shared_classes(fabric_names, fabric_only)
    require(forge_classes == fabric_classes,
            'shared class set differs: ' + ', '.join(sorted(forge_classes ^ fabric_classes)[:8]))
    require(any(name.startswith('mekanism/common/integration/lookingat/theoneprobe/') for name in forge_names),
            'Forge JAR is missing The One Probe providers')
    require(not any(name.startswith('mekanism/common/integration/lookingat/theoneprobe/') for name in fabric_names),
            'The One Probe provider leaked into Fabric')
    require(any(name.startswith('mekanism/common/integration/projecte/') for name in forge_names),
            'Forge JAR is missing the ProjectE mappers')
    require(not any(name.startswith('mekanism/common/integration/projecte/') for name in fabric_names),
            'ProjectE mappers leaked into Fabric')
    for prefix, label in (('mekanism/common/integration/energy/fluxnetworks/FNEnergyCompat', 'Flux Networks compat'),
                          ('mekanism/common/integration/recipestages/RecipeStagesBridge', 'Recipe Stages bridge')):
        require(prefix + '.class' in forge_names, f'Forge JAR is missing the {label}')
    require('mekanism/common/integration/recipestages/RecipeStagesCompat.class' in forge_classes,
            'shared Recipe Stages facade is missing')
    require(not any(name.startswith(forge_only) for name in fabric_names),
            'a Forge-only integration leaked into Fabric')
    require(any(name.startswith(fabric_only) for name in fabric_names), 'Fabric JAR is missing the WTHIT providers')
    require(not any(name.startswith(fabric_only) for name in forge_names), 'WTHIT provider leaked into Forge')
    require(any(name.startswith('mekanism/common/integration/lookingat/jade/') for name in forge_classes),
            'Jade providers are missing from the shared class set')
    require(any(name.startswith('mekanism/common/integration/computer/computercraft/MekanismPeripheral') for name in forge_classes),
            'ComputerCraft peripherals are missing from the shared class set')
    require(any(name.startswith(fabric_only[1]) for name in fabric_names), 'Fabric JAR is missing the ComputerCraft provider')
    require(not any(name.startswith(fabric_only[1]) for name in forge_names), 'Fabric ComputerCraft provider leaked into Forge')
    require(any(name.startswith('mekanism/common/integration/curios/CuriosIntegration') for name in forge_classes),
            'shared accessory facade is missing')
    require(any(name.startswith('mekanism/common/network/to_server/PacketModeChangeCurios') for name in forge_classes),
            'accessory mode packet is missing')
    require(any(name.startswith('mekanism/common/integration/curios/ForgeCurios') for name in forge_names),
            'Forge JAR is missing the Curios adapter')
    require(not any(name.startswith('mekanism/common/integration/curios/ForgeCurios') for name in fabric_names),
            'Curios adapter leaked into Fabric')
    require(any(name.startswith('mekanism/client/render/MekanismCurioRenderer') for name in forge_names),
            'Forge JAR is missing the Curios renderer')
    require(not any(name.startswith('mekanism/client/render/MekanismCurioRenderer') for name in fabric_names),
            'Curios renderer leaked into Fabric')
    require(any(name.startswith('mekanism/common/integration/curios/FabricTrinkets') for name in fabric_names),
            'Fabric JAR is missing the Trinkets adapter')
    require(not any(name.startswith('mekanism/common/integration/curios/FabricTrinkets') for name in forge_names),
            'Trinkets adapter leaked into Forge')
    require(any(name.startswith('mekanism/common/integration/gender/GenderArmorCapability') for name in forge_classes),
            'Gender adapter missing from Forge')
    require(any(name.startswith('mekanism/common/integration/gender/GenderArmorCapability') for name in fabric_classes),
            'Gender adapter missing from Fabric')
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
            require(parity.outer_class(name) in declared_classes, f'non-mixin class in the mixin package: {name}')
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
    forge_paths = gameplay(forge_jar)
    fabric_paths = gameplay(fabric_jar)
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
          f'{len(overrides)} Fabric tag-bridge overrides; {parity.summary(shared_checks)}; '
          f'{tests} GameTests on both loaders, {loader_only} allowlisted to one; loader isolation, mixins, '
          'nested libraries, EMC data and attribution valid.')


MODULES = (
    ('generators', 'mekanismgenerators', True),
    ('tools', 'mekanismtools', True),
    ('additions', 'mekanismadditions', True),
)


def module_jars(name, mod_id):
    return (MOD / 'build/libs' / f'{mod_id}-forge-1.19.2-{version}.jar',
            MOD / 'fabric/build/libs' / f'{mod_id}-fabric-1.19.2-{version}.jar')


def check_module(name, mod_id, expect_emc):
    """One extra mod id: its own jar on each loader, sharing the module's classes and resources."""
    forge_path, fabric_path = module_jars(name, mod_id)
    parity.assert_built(forge_path, fabric_path)
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

        forge_mod, fabric_mod = parity.check_descriptors(
            forge_jar, fabric_jar, mod_id=mod_id, version=version, license_id=metadata['mod_license'],
            fail=require)
        require(fabric_mod['depends'].get('mekanism'), f'{mod_id}: Fabric jar must depend on mekanism')
        # Each module jar must carry its own mixin config's refmap (MEK-1) and nests nothing.
        shared_checks = parity.check_artifact_pair(
            forge_jar, fabric_jar, fabric_mod, build_dir=MOD / 'fabric', src=MOD / 'src', nested=(),
            label=mod_id, extra_depends={'mekanism': version}, fail=require)
        parity.check_cross_loader(forge_jar, fabric_jar, forge_forbidden=(b'net/fabricmc/', b'team/reborn/'),
                                  fail=require)
        parity.check_gametests((forge_jar, fabric_jar), *GAMETEST_ROOTS,
                                  class_substrings=('gametest',), fail=require)
        forge_classes = {entry_name for entry_name in forge_names if entry_name.endswith('.class') and entry_name.startswith(prefix)}
        fabric_classes = {entry_name for entry_name in fabric_names if entry_name.endswith('.class') and entry_name.startswith(prefix)}
        require(forge_classes and forge_classes == fabric_classes,
                f'{mod_id}: shared class set differs: ' + ', '.join(sorted(forge_classes ^ fabric_classes)[:8]))
        require(not any(entry_name.startswith('ports/') for entry_name in forge_names),
                f'{mod_id}: Fabric adapter leaked into Forge')
        require(any(entry_name.startswith(entry) and entry_name.endswith('.class') for entry_name in fabric_names),
                f'{mod_id}: missing Fabric entrypoint under {entry}')
        forge_paths = gameplay(forge_jar)
        fabric_paths = gameplay(fabric_jar)
        expected = gameplay(shared) | {'logo.png'}
        # generate_fabric_tags.py writes the module's c: tags into its Fabric resources. They are new paths, never
        # replacements: Loom fails on a path the module's Fabric and shared folders both have.
        bridges = files_under(MOD / 'src' / name / 'fabric/resources')
        bridge_paths = gameplay(bridges)
        outside = sorted(path for path in bridge_paths if not path.startswith(('data/c/tags/', 'data/forge/tags/')))
        require(not outside, f'{mod_id}: Fabric-only resources outside the tag bridges: ' + ', '.join(outside[:8]))
        require(not bridge_paths & expected, f'{mod_id}: Fabric tag bridge replaces a shared file: '
                + ', '.join(sorted(bridge_paths & expected)[:8]))
        require(forge_paths == expected and fabric_paths == expected | bridge_paths,
                f'{mod_id}: gameplay paths differ: '
                + ', '.join(sorted((forge_paths ^ expected) | (fabric_paths ^ (expected | bridge_paths)))[:8]))
        for entry_name in sorted(bridge_paths):
            require(fabric_jar.read(entry_name) == bridges[entry_name], f'{mod_id}: Fabric tag bridge differs: {entry_name}')
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
    print(f'PASS: {mod_id} {len(forge_classes)} shared classes, {len(expected)} gameplay resources, '
          f'{len(bridge_paths)} Fabric tag bridges; {parity.summary(shared_checks)}.')
    parity.print_hashes(forge_path, fabric_path)


for module_name, module_id, module_emc in MODULES:
    check_module(module_name, module_id, module_emc)

parity.print_hashes(forge, fabric)
