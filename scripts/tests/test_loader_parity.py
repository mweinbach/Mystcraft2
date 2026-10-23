"""Exercise the shared loader-parity helpers with synthetic artifacts."""

import importlib.util
import json
import sys
import tempfile
import textwrap
import unittest
import zipfile
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('loader_parity', ROOT / 'scripts' / 'loader_parity.py')
parity = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(parity)

try:
    parity.toml_module()
    HAVE_TOML = True
except SystemExit:
    HAVE_TOML = False

# Ports with a Forge and a Fabric variant, each with its own gate.
DUAL_PORTS = sorted(path.parents[1].name for path in (ROOT / 'mods').glob('*/scripts/check_loader_parity.py'))


def write_jar(path, entries):
    with zipfile.ZipFile(path, 'w') as jar:
        for name, data in entries.items():
            jar.writestr(name, data)
    return path


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text))
    return path


class Failures(list):
    """A ``fail=`` collector: records each message instead of exiting."""

    def __call__(self, condition, message):
        if not condition:
            self.append(message)


FORGE_TOML = 'license="MIT"\n[[mods]]\nmodId="demo"\nversion="1.0"\ndescription="Demo description"\n'
FABRIC_JSON = json.dumps({'id': 'demo', 'version': '1.0',
                          'description': 'Demo description', 'license': 'MIT'})

MIXIN_CONFIG = {'required': True, 'package': 'demo.mixin', 'refmap': 'demo.refmap.json',
                'mixins': ['LevelMixin'], 'client': ['client.ScreenMixin']}
REFMAP = {'mappings': {'demo/mixin/LevelMixin': {'tick': 'Lnet/minecraft/class_1937;method_8441()V'},
                       'demo/mixin/client/ScreenMixin': {'init': 'Lnet/minecraft/class_437;method_25426()V'}}}


def mixin_jar(path, config=None, refmap=None, *, config_name='demo.mixins.json', extra=None):
    config = MIXIN_CONFIG if config is None else config
    entries = {
        'fabric.mod.json': json.dumps({'id': 'demo', 'mixins': [config_name]}),
        config_name: json.dumps(config),
        'demo/mixin/LevelMixin.class': b'x',
        'demo/mixin/client/ScreenMixin.class': b'x',
    }
    if refmap is not False:
        entries[config.get('refmap', 'demo.refmap.json')] = json.dumps(REFMAP if refmap is None else refmap)
    entries.update(extra or {})
    return write_jar(path, entries)


class LoaderParityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def open(self, path):
        jar = zipfile.ZipFile(path)
        self.addCleanup(jar.close)
        return jar

    # --- existing helpers -------------------------------------------------------------

    def test_outer_class_strips_nested_class(self):
        self.assertEqual(parity.outer_class('a/b/C$Inner.class'), 'a/b/C')
        self.assertEqual(parity.outer_class('a/b/C.class'), 'a/b/C')

    def test_read_properties_ignores_comments_and_blank_values(self):
        path = self.dir / 'port.properties'
        path.write_text('# c\nmod_id=demo\nmod_version=1.0\nempty=\n')
        self.assertEqual(parity.read_properties(path),
                         {'mod_id': 'demo', 'mod_version': '1.0', 'empty': ''})

    @unittest.skipUnless(HAVE_TOML, 'needs tomllib (3.11+) or tomli')
    def test_descriptors_pass_and_reject_a_leak(self):
        forge = write_jar(self.dir / 'forge.jar', {'META-INF/mods.toml': FORGE_TOML})
        fabric = write_jar(self.dir / 'fabric.jar', {'fabric.mod.json': FABRIC_JSON})
        a, b = self.open(forge), self.open(fabric)
        parity.check_descriptors(a, b, mod_id='demo', version='1.0',
                                 description='Demo description', license_id='MIT')
        with self.assertRaises(SystemExit):
            parity.check_descriptors(a, b, mod_id='other', version='1.0')

        leaked = write_jar(self.dir / 'leaked.jar',
                           {'META-INF/mods.toml': FORGE_TOML, 'fabric.mod.json': FABRIC_JSON})
        with self.assertRaises(SystemExit):
            parity.check_descriptors(a, self.open(leaked), mod_id='demo', version='1.0')

    @unittest.skipUnless(HAVE_TOML, 'needs tomllib (3.11+) or tomli')
    def test_license_is_checked_in_both_descriptors(self):
        fabric = self.open(write_jar(self.dir / 'fabric.jar', {'fabric.mod.json': FABRIC_JSON}))
        forge = self.open(write_jar(self.dir / 'forge.jar',
                                    {'META-INF/mods.toml': FORGE_TOML.replace('MIT', 'LGPL-3.0-only')}))
        failures = Failures()
        parity.check_descriptors(forge, fabric, mod_id='demo', version='1.0', license_id='MIT', fail=failures)
        self.assertEqual(len(failures), 1)
        self.assertIn('mods.toml', failures[0])
        # A port without an SPDX license gives fabric.mod.json a LicenseRef while mods.toml keeps its text.
        failures = Failures()
        parity.check_descriptors(forge, fabric, mod_id='demo', version='1.0', license_id='LGPL-3.0-only',
                                 fabric_license_id='MIT', fail=failures)
        self.assertEqual(failures, [])

    def test_toml_fallback_reads_mods_toml(self):
        if not HAVE_TOML:
            self.skipTest('no tomllib, tomli or .local/tomli')
        self.assertEqual(parity.load_toml(FORGE_TOML)['mods'][0]['modId'], 'demo')

    def test_toml_fallback_uses_the_local_shim_then_explains(self):
        """WS-2: without tomllib (Python < 3.11) or tomli, use .local/tomli, else say how to get it."""
        shim = self.dir / '.local' / 'tomli' / 'tomli'
        write(shim / '__init__.py', 'def loads(text):\n    return {"shim": True}\n')
        blocked = {'tomllib': None, 'tomli': None}
        with mock.patch.dict(sys.modules, blocked), mock.patch.object(parity, 'ROOT', self.dir / 'empty'):
            with self.assertRaises(SystemExit) as caught:
                parity.toml_module()
            self.assertIn('pip install --target .local/tomli tomli', str(caught.exception))
        path = list(sys.path)
        try:
            sys.path[:] = [entry for entry in path if not entry.endswith('tomli')]
            with mock.patch.dict(sys.modules, blocked), mock.patch.object(parity, 'ROOT', self.dir):
                sys.modules.pop('tomli')  # let the import find the shim parity puts on sys.path
                self.assertEqual(parity.load_toml('x = 1'), {'shim': True})
        finally:
            sys.path[:] = path

    def test_cross_loader_scan(self):
        forge = write_jar(self.dir / 'f.jar', {'demo/A.class': b'net/fabricmc/Loader'})
        fabric = write_jar(self.dir / 'b.jar', {'demo/A.class': b'net/minecraftforge/Mod'})
        a, b = self.open(forge), self.open(fabric)
        with self.assertRaises(SystemExit):
            parity.check_cross_loader(a, b)
        parity.check_cross_loader(a, b, forge_forbidden=(), fabric_forbidden=())

    def test_gameplay_paths(self):
        jar = self.open(write_jar(self.dir / 'g.jar', {
            'assets/demo/x.json': b'{}',
            'data/demo/y.json': b'{}',
            'pack.mcmeta': b'{}',
            'demo/A.class': b'x',
        }))
        self.assertEqual(parity.gameplay_paths(jar), {'assets/demo/x.json', 'data/demo/y.json'})
        self.assertIn('pack.mcmeta', parity.gameplay_paths(jar, extra=('pack.mcmeta',)))

    def test_gametest_exclusion_rejects_classes_and_fixtures(self):
        root = self.dir / 'gametest'
        (root / 'demo').mkdir(parents=True)
        (root / 'demo' / 'DemoTest.java').write_text('class DemoTest {}')
        clean = write_jar(self.dir / 'clean.jar', {'demo/A.class': b'x'})
        dirty = write_jar(self.dir / 'dirty.jar', {'demo/DemoTest.class': b'x'})
        fixture = write_jar(self.dir / 'fixture.jar', {'data/demo/gametest/structures/x.snbt': b'x'})
        parity.check_gametests((self.open(clean),), root)
        for path in (dirty, fixture):
            with self.assertRaises(SystemExit):
                parity.check_gametests((self.open(path),), root)

    def test_gametest_class_marker_catches_unknown_source_sets(self):
        jar = self.open(write_jar(self.dir / 'marker.jar', {'demo/SomeGametestThing.class': b'x'}))
        with self.assertRaises(SystemExit):
            parity.check_gametests((jar,), class_substrings=('gametest',))

    def test_nested_libraries(self):
        library = 'META-INF/jars/energy-2.3.0.jar'
        fabric = self.open(write_jar(self.dir / 'fabric.jar', {
            'fabric.mod.json': json.dumps({'jars': [{'file': library}]}),
            library: b'jar',
        }))
        forge = self.open(write_jar(self.dir / 'forge.jar', {'demo/A.class': b'x'}))
        mod = json.loads(fabric.read('fabric.mod.json'))
        self.assertEqual(parity.check_nested_libraries(forge, fabric, mod, (library,)), 1)
        with self.assertRaises(SystemExit):
            parity.check_nested_libraries(forge, fabric, mod, (library, 'META-INF/jars/core.jar'))
        # A module jar that should nest nothing.
        with self.assertRaises(SystemExit):
            parity.check_nested_libraries(forge, fabric, mod, ())

    def test_assert_built(self):
        with self.assertRaises(SystemExit):
            parity.assert_built(self.dir / 'nope.jar')
        parity.assert_built(Path(__file__))

    # --- mixin refmaps ----------------------------------------------------------------

    def test_mixin_refmap_in_its_own_jar_passes(self):
        jar = self.open(mixin_jar(self.dir / 'demo.jar'))
        mod = json.loads(jar.read('fabric.mod.json'))
        self.assertEqual(parity.check_mixin_refmaps(jar, label='demo', fabric_mod=mod,
                                                    markers=parity.FABRIC_REFMAP_MARKERS), 1)

    def test_mek1_refmap_only_in_a_sibling_jar_fails(self):
        """MEK-1: mekanismadditions.mixins.json named a refmap that only the main mekanism jar had."""
        refmap = {'mappings': {'ports/mekanism/additions/fabric/mixin/BalloonMixin': {'x': 'method_1'},
                               'ports/mekanism/fabric/mixin/LevelMixin': {'y': 'method_2'}}}
        main = self.open(write_jar(self.dir / 'mekanism.jar', {
            'fabric.mod.json': json.dumps({'id': 'mekanism', 'mixins': ['mekanism.mixins.json']}),
            'mekanism.mixins.json': json.dumps({'package': 'ports.mekanism.fabric.mixin',
                                                'refmap': 'mekanism.refmap.json', 'mixins': ['LevelMixin']}),
            'ports/mekanism/fabric/mixin/LevelMixin.class': b'x',
            'mekanism.refmap.json': json.dumps(refmap),
            'mekanismadditions.refmap.json': json.dumps(refmap),  # shipped in the wrong jar
        }))
        additions = self.open(write_jar(self.dir / 'mekanismadditions.jar', {
            'fabric.mod.json': json.dumps({'id': 'mekanismadditions', 'mixins': ['mekanismadditions.mixins.json']}),
            'mekanismadditions.mixins.json': json.dumps({
                'package': 'ports.mekanism.additions.fabric.mixin', 'refmap': 'mekanismadditions.refmap.json',
                'mixins': ['BalloonMixin']}),
            'ports/mekanism/additions/fabric/mixin/BalloonMixin.class': b'x',
        }))
        parity.check_mixin_refmaps(main, label='mekanism', fabric_mod=json.loads(main.read('fabric.mod.json')))
        with self.assertRaises(SystemExit) as caught:
            parity.check_mixin_refmaps(additions, label='mekanismadditions',
                                       fabric_mod=json.loads(additions.read('fabric.mod.json')))
        self.assertIn('mekanismadditions.refmap.json, which is not in the same jar', str(caught.exception))

    def test_refmap_pointing_at_another_modules_mappings_fails(self):
        config = dict(MIXIN_CONFIG, refmap='other.refmap.json')
        other = {'mappings': {'other/mixin/Thing': {'x': 'method_1'}}}
        jar = self.open(mixin_jar(self.dir / 'demo.jar', config, other))
        failures = Failures()
        parity.check_mixin_refmaps(jar, label='demo', fabric_mod=json.loads(jar.read('fabric.mod.json')),
                                   fail=failures)
        self.assertTrue(any("maps no mixin in demo.mixins.json's package" in f for f in failures), failures)

    def test_empty_missing_and_dev_named_refmaps_fail(self):
        cases = {
            'empty': (MIXIN_CONFIG, {'mappings': {}}, 'is empty'),
            'missing key': ({k: v for k, v in MIXIN_CONFIG.items() if k != 'refmap'}, False, 'names no refmap'),
            'dev names': (MIXIN_CONFIG, {'mappings': {'demo/mixin/LevelMixin': {'tick': 'Lnet/minecraft/Level;tick()V'}}},
                          'no production'),
        }
        for label, (config, refmap, message) in cases.items():
            with self.subTest(label):
                jar = self.open(mixin_jar(self.dir / f'{label}.jar', config, refmap))
                failures = Failures()
                parity.check_mixin_refmaps(jar, label='demo', fabric_mod=json.loads(jar.read('fabric.mod.json')),
                                           markers=parity.FABRIC_REFMAP_MARKERS, fail=failures)
                self.assertTrue(any(message in f for f in failures), failures)

    def test_mixin_config_classes_and_declaration(self):
        config = dict(MIXIN_CONFIG, mixins=['LevelMixin', 'GoneMixin'], plugin='demo.mixin.Plugin')
        jar = self.open(mixin_jar(self.dir / 'demo.jar', config,
                                  extra={'stray.mixins.json': json.dumps({'package': 'x', 'mixins': []})}))
        failures = Failures()
        parity.check_mixin_refmaps(jar, label='demo', fabric_mod=json.loads(jar.read('fabric.mod.json')),
                                   fail=failures)
        text = '\n'.join(failures)
        self.assertIn('GoneMixin, which is not in the jar', text)
        self.assertIn('plugin demo.mixin.Plugin', text)
        self.assertIn('stray.mixins.json ships but no descriptor applies it', text)

    def test_forge_manifest_mixin_configs_wrap(self):
        manifest = ('Manifest-Version: 1.0\r\nMixinConfigs: demo.mixins.json,demo.client.mi\r\n xins.json\r\n'
                    'Implementation-Title: demo\r\n')
        jar = self.open(write_jar(self.dir / 'forge.jar', {'META-INF/MANIFEST.MF': manifest}))
        self.assertEqual(parity.manifest_mixin_configs(jar), ['demo.mixins.json', 'demo.client.mixins.json'])
        with self.assertRaises(SystemExit):
            parity.check_mixin_refmaps(jar, label='forge')  # declared configs are absent

    # --- entrypoints ------------------------------------------------------------------

    def test_entrypoints(self):
        mod = {'entrypoints': {'main': ['demo.Main'],
                               'client': [{'adapter': 'default', 'value': 'demo.Client'}],
                               'emi': ['demo.compat.Plugin::INSTANCE']}}
        jar = self.open(write_jar(self.dir / 'e.jar', {'demo/Main.class': b'x', 'demo/Client.class': b'x',
                                                        'demo/compat/Plugin.class': b'x'}))
        self.assertEqual(parity.check_entrypoints(jar, mod), 3)
        mod['entrypoints']['jei_mod_plugin'] = ['demo.compat.Missing']
        with self.assertRaises(SystemExit) as caught:
            parity.check_entrypoints(jar, mod)
        self.assertIn('jei_mod_plugin entrypoint demo.compat.Missing', str(caught.exception))

    # --- depends floors ---------------------------------------------------------------

    def build_dir(self, gradle_properties=True):
        build = self.dir / 'fabric'
        write(build / 'build.gradle', """\
            dependencies {
                minecraft "com.mojang:minecraft:${project.minecraft_version}"
                modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
                modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
                include(modApi("teamreborn:energy:${project.energy_version}") { transitive = false })
                include(implementation('com.electronwill.night-config:core:3.6.4'))
                include(modImplementation("ports.fluidphysics:fluidphysics-fabric:${fluidPhysicsVersion}"))
            }
            java { toolchain.languageVersion = JavaLanguageVersion.of(17) }
            """)
        if gradle_properties:
            write(build / 'gradle.properties', """\
                org.gradle.daemon=false
                minecraft_version=1.19.2
                loader_version=0.16.14
                fabric_version=0.77.0+1.19.2
                energy_version=2.3.0
                """)
        return build

    def test_gradle_versions_resolve_properties_and_included_builds(self):
        versions = parity.gradle_versions(self.build_dir())
        self.assertEqual(versions['net.fabricmc:fabric-loader'], '0.16.14')
        self.assertEqual(versions['net.fabricmc.fabric-api:fabric-api'], '0.77.0+1.19.2')
        self.assertEqual(versions['teamreborn:energy'], '2.3.0')
        self.assertEqual(versions['java'], '17')
        fluid = parity.read_properties(ROOT / 'mods/fluidphysics/port.properties')['mod_version']
        self.assertEqual(versions['ports.fluidphysics:fluidphysics-fabric'], fluid)

    def test_depends_floor_matches_the_build(self):
        build = self.build_dir()
        depends = {'fabricloader': '>=0.16.14', 'fabric-api': '>=0.77.0', 'minecraft': '1.19.2',
                   'java': '>=17', 'team_reborn_energy': '>=2.3.0', 'fabric-permissions-api-v0': '*'}
        self.assertEqual(parity.check_depends_floor({'depends': depends}, build), 5)
        for dependency, constraint, message in (
                ('fabricloader', '>=0.14.0', 'fabricloader >=0.14.0, but the build uses 0.16.14'),
                ('fabric-api', '>=0.76.0', 'fabric-api >=0.76.0, but the build uses 0.77.0'),
                ('team_reborn_energy', '>=2.2.0', 'team_reborn_energy >=2.2.0, but the build uses 2.3.0'),
                ('minecraft', '*', 'minecraft has no minimum version')):
            with self.subTest(dependency):
                failures = Failures()
                parity.check_depends_floor({'depends': dict(depends, **{dependency: constraint})}, build,
                                           fail=failures)
                self.assertTrue(any(message in f for f in failures), failures)
        failures = Failures()
        parity.check_depends_floor({'depends': {k: v for k, v in depends.items() if k != 'java'}}, build,
                                   fail=failures)
        self.assertTrue(any('does not depend on java' in f for f in failures), failures)
        failures = Failures()
        parity.check_depends_floor({'depends': dict(depends, mekanism='1.0')}, build,
                                   extra={'mekanism': '1.1'}, fail=failures)
        self.assertTrue(any('mekanism 1.0, but the build uses 1.1' in f for f in failures), failures)

    def test_depends_floor_reads_literal_coordinates(self):
        build = self.dir / 'literal'
        write(build / 'build.gradle', """\
            minecraft 'com.mojang:minecraft:1.19.2'
            modImplementation 'net.fabricmc:fabric-loader:0.16.14'
            modImplementation 'net.fabricmc.fabric-api:fabric-api:0.77.0+1.19.2'
            java { toolchain.languageVersion = JavaLanguageVersion.of(17) }
            """)
        depends = {'fabricloader': '>=0.16.14', 'fabric-api': '>=0.77.0', 'minecraft': '1.19.2', 'java': '>=17'}
        self.assertEqual(parity.check_depends_floor({'depends': depends}, build), 4)

    def test_included_libraries(self):
        build = self.build_dir()
        fluid = parity.read_properties(ROOT / 'mods/fluidphysics/port.properties')['mod_version']
        self.assertEqual(parity.included_libraries(build), (
            'META-INF/jars/energy-2.3.0.jar', 'META-INF/jars/core-3.6.4.jar',
            f'META-INF/jars/fluidphysics-fabric-1.19.2-{fluid}.jar'))

    # --- access widener ---------------------------------------------------------------

    def test_access_widener(self):
        forge = self.open(write_jar(self.dir / 'forge.jar', {'demo/A.class': b'x'}))
        header = 'accessWidener\tv2\tintermediary\n'
        entry = 'accessible\tfield\tnet/minecraft/class_1\tfield_1\tI\n'
        declared = {'accessWidener': 'demo.accesswidener'}
        good = self.open(write_jar(self.dir / 'good.jar', {'demo.accesswidener': header + '# why\n' + entry}))
        self.assertEqual(parity.check_access_widener(forge, good, declared), 1)
        self.assertEqual(parity.check_access_widener(forge, self.open(write_jar(self.dir / 'none.jar', {})), {}), 0)
        cases = {
            'header only (ER-6)': (good, {'demo.accesswidener': header + '# nothing yet\n'}, declared, 'widens nothing'),
            'undeclared': (good, {'demo.accesswidener': header + entry}, {}, 'undeclared access widener'),
            'declared but absent': (good, {}, declared, 'which is not in the jar'),
            'no header': (good, {'demo.accesswidener': entry}, declared, 'no accessWidener header'),
        }
        for label, (_, entries, mod, message) in cases.items():
            with self.subTest(label):
                fabric = self.open(write_jar(self.dir / f'{len(label)}.jar', entries))
                failures = Failures()
                parity.check_access_widener(forge, fabric, mod, fail=failures)
                self.assertTrue(any(message in f for f in failures), failures)
        leaked = self.open(write_jar(self.dir / 'leak.jar', {'demo.accesswidener': header + entry}))
        with self.assertRaises(SystemExit):
            parity.check_access_widener(leaked, good, declared)

    # --- GameTest fixtures ------------------------------------------------------------

    def test_gametest_fixture_paths(self):
        src = self.dir / 'src'
        write(src / 'gametestCommon/resources/data/demo/structures/arena.nbt', 'nbt')
        write(src / 'gametestFabric/resources/fabric.mod.json', '{}')
        write(src / 'fabric/resources/fabric.mod.json', '{}')
        write(src / 'main/resources/data/demo/recipes/a.json', '{}')
        clean = self.open(write_jar(self.dir / 'clean.jar', {'fabric.mod.json': '{}', 'data/demo/recipes/a.json': '{}'}))
        self.assertEqual(parity.check_gametest_fixtures((clean,), src), 1)
        leaked = self.open(write_jar(self.dir / 'leak.jar', {'data/demo/structures/arena.nbt': 'nbt'}))
        with self.assertRaises(SystemExit) as caught:
            parity.check_gametest_fixtures((clean, leaked), src)
        self.assertIn('data/demo/structures/arena.nbt', str(caught.exception))


class GameTestDiscoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.mod = Path(self.tmp.name)
        self.common = self.mod / 'src/gametestCommon/java'
        self.forge = self.mod / 'src/gametestForge/java'
        self.fabric = self.mod / 'src/gametestFabric/java'

    def tearDown(self):
        self.tmp.cleanup()

    def java(self, root, name, body):
        return write(root / (name.replace('.', '/') + '.java'), body)

    def descriptor(self, *entries):
        write(self.mod / 'src/gametestFabric/resources/fabric.mod.json',
              json.dumps({'entrypoints': {'fabric-gametest': list(entries)}}))

    def test_methods_ignore_comments_and_accept_instance_tests(self):
        path = self.java(self.common, 'demo.Tests', """\
            class Tests implements FabricGameTest {
                // @GameTest public void commented(GameTestHelper helper) {}
                /* @GameTest public static void blockComment(GameTestHelper helper) {} */
                String text = "@GameTest public void inString(";
                @GameTest(template = "demo:arena", timeoutTicks = Ticks.of(20))
                @SuppressWarnings("unused")
                public static void staticTest(GameTestHelper helper) {}
                @GameTest(template = EMPTY_STRUCTURE)
                public void instanceTest(GameTestHelper helper) {}
                public void helper(GameTestHelper helper) {}
            }
            """)
        self.assertEqual(parity.gametest_methods(path), {'staticTest', 'instanceTest'})

    def test_forge_holders_fabric_entrypoints_and_generators(self):
        self.java(self.common, 'demo.SharedTests', """\
            public class SharedTests { @GameTest public static void shared(GameTestHelper h) {} }""")
        self.java(self.common, 'demo.Support', 'public class Support { static void help() {} }')
        self.java(self.forge, 'demo.ForgeTests', """\
            @GameTestHolder("demo")
            public class ForgeTests {
                private static final List<Class<?>> SHARED = List.of(SharedTests.class);
                @GameTestGenerator public static List<TestFunction> all() { Support.help(); return null; }
                @GameTest public static void forgeAdapter(GameTestHelper h) {}
            }""")
        self.java(self.fabric, 'demo.FabricTests', """\
            public class FabricTests implements FabricGameTest {
                @GameTest public void fabricAdapter(GameTestHelper h) {}
            }""")
        self.descriptor('demo.SharedTests', 'demo.FabricTests')
        forge, fabric = parity.loader_gametests(self.mod)
        # Keyed by runtime class.method; the loader-named adapters normalise to one class.
        self.assertEqual(forge, {'sharedtests.shared': 'SharedTests.shared',
                                 'tests.forgeadapter': 'ForgeTests.forgeAdapter'})
        self.assertEqual(fabric, {'sharedtests.shared': 'SharedTests.shared',
                                  'tests.fabricadapter': 'FabricTests.fabricAdapter'})

    def test_package_scanning_generator_and_delegation(self):
        # Mekanism: Forge's holder delegates to a shared generator that scans its own package at runtime.
        self.java(self.common, 'demo.Scanner', """\
            public class Scanner {
                @GameTestGenerator public static List<TestFunction> sharedTests() {
                    URL url = Scanner.class.getResource("Scanner.class"); return null; }
            }""")
        self.java(self.common, 'demo.MachineTests', 'class MachineTests { @GameTest public static void machine(GameTestHelper h) {} }')
        self.java(self.common, 'demo.other.Elsewhere', 'class Elsewhere { @GameTest public static void elsewhere(GameTestHelper h) {} }')
        self.java(self.fabric, 'demo.FabricOnlyTests', 'class FabricOnlyTests { @GameTest public static void fabricOnly(GameTestHelper h) {} }')
        self.java(self.forge, 'demo.forge.ForgeTests', """\
            @GameTestHolder("demo") public class ForgeTests {
                @GameTestGenerator public static List<TestFunction> sharedTests() { return Scanner.sharedTests(); }
            }""")
        self.descriptor('demo.Scanner')
        forge, fabric = parity.loader_gametests(self.mod)
        self.assertEqual(set(forge), {'machinetests.machine'})
        self.assertEqual(set(fabric), {'machinetests.machine', 'onlytests.fabriconly'})

    def test_unexplained_generator_fails_loudly(self):
        self.java(self.forge, 'demo.ForgeTests', """\
            @GameTestHolder("demo") public class ForgeTests {
                @GameTestGenerator public static List<TestFunction> recipes() { return List.of(); }
            }""")
        self.descriptor()
        with self.assertRaises(SystemExit):
            parity.loader_gametests(self.mod)

    def test_symmetry_and_allowlist(self):
        allowlist = write(self.mod / parity.ALLOWLIST_NAME, """\
            # header comment
            forge ForgeEnergyTests.forgeEnergy  # Forge capability adapter
            fabric FabricTransferTests.transferApi  # Transfer API adapter
            """)

        def tests(*displays):
            return {parity.gametest_identity(*d.split('.')): d for d in displays}

        shared = ('SharedTests.shared', )
        forge = tests(*shared, 'ForgeEnergyTests.forgeEnergy')
        fabric = tests(*shared, 'FabricTransferTests.transferApi')
        self.assertEqual(parity.check_gametest_symmetry(forge, fabric, allowlist), (1, 2))
        cases = {
            'unlisted one-loader test': ({**forge, **tests('NewTests.newForgeTest')}, fabric,
                                         'NewTests.newForgeTest runs only on Forge'),
            'stale entry': ({**forge, **tests('FabricTransferTests.transferApi')}, fabric,
                            'lists FabricTransferTests.transferApi as fabric-only, but it is not'),
            'wrong loader': (tests(*shared, 'FabricTransferTests.transferApi'),
                             tests(*shared, 'ForgeEnergyTests.forgeEnergy'), 'runs only on Forge'),
            'bare method name no longer matches': (
                {**forge, **tests('OtherEnergyTests.forgeEnergy')}, fabric,
                'OtherEnergyTests.forgeEnergy runs only on Forge'),
        }
        for label, (a, b, message) in cases.items():
            with self.subTest(label):
                failures = Failures()
                parity.check_gametest_symmetry(a, b, allowlist, fail=failures)
                self.assertTrue(any(message in f for f in failures), failures)

    def test_allowlist_needs_a_reason_per_line_and_no_duplicates(self):
        for body, message in (('forge Tests.onlyHere\n', 'expected'), ('neoforge Tests.x  # why\n', 'expected'),
                              ('forge onlyHere  # bare method, no class\n', 'expected'),
                              ('forge Tests.a  # one\nforge Tests.a  # two\n', 'listed twice')):
            with self.subTest(body):
                path = write(self.mod / 'list.txt', body)
                failures = Failures()
                parity.read_gametest_allowlist(path, fail=failures)
                self.assertTrue(any(message in f for f in failures), failures)


    def test_same_method_in_two_classes_is_two_tests(self):
        # Codex P2 on #122: Mekanism's FissionReactorGameTests and FusionReactorGameTests both
        # declare reactorContentsSurviveSaveAndLoad.  Keyed by bare method name, the fission test
        # stood in for a fusion test that had stopped registering on one loader.
        for simple in ('FissionTests', 'FusionTests'):
            self.java(self.common, f'demo.{simple}', f"""\
                public class {simple} {{
                    @GameTest public static void reactorContentsSurviveSaveAndLoad(GameTestHelper h) {{}}
                }}""")
        self.java(self.forge, 'demo.forge.ForgeTests', """\
            @GameTestHolder("demo") public class ForgeTests {
                @GameTestGenerator public static List<TestFunction> all() {
                    return of(FissionTests.class, FusionTests.class); }
            }""")
        self.descriptor('demo.FissionTests')
        forge, fabric = parity.loader_gametests(self.mod)
        self.assertEqual(forge, {'fissiontests.reactorcontentssurvivesaveandload':
                                 'FissionTests.reactorContentsSurviveSaveAndLoad',
                                 'fusiontests.reactorcontentssurvivesaveandload':
                                 'FusionTests.reactorContentsSurviveSaveAndLoad'})
        self.assertEqual(set(fabric), {'fissiontests.reactorcontentssurvivesaveandload'})
        failures = Failures()
        allowlist = write(self.mod / parity.ALLOWLIST_NAME, '# nothing is one-loader\n')
        parity.check_gametest_symmetry(forge, fabric, allowlist, fail=failures)
        self.assertTrue(any('FusionTests.reactorContentsSurviveSaveAndLoad runs only on Forge' in f
                            for f in failures), failures)
        # Registering the fusion class on Fabric restores symmetry.
        self.descriptor('demo.FissionTests', 'demo.FusionTests')
        forge, fabric = parity.loader_gametests(self.mod)
        self.assertEqual(parity.check_gametest_symmetry(forge, fabric, allowlist), (2, 0))

    def test_loader_named_forwarders_share_an_identity(self):
        self.assertEqual(parity.shared_class_name('ExtremeReactorsForgeGameTests'), 'ExtremeReactorsGameTests')
        self.assertEqual(parity.gametest_identity('ExtremeReactorsForgeGameTests', 'turbineSpins'),
                         parity.gametest_identity('ExtremeReactorsFabricGameTests', 'turbineSpins'))
        self.assertEqual(parity.gametest_identity('FabricBonusLootGameTests', 'drops'),
                         parity.gametest_identity('BonusLootGameTests', 'drops'))
        self.assertNotEqual(parity.gametest_identity('DinosaurInjuryGameTests', 'heals'),
                            parity.gametest_identity('DinosaurFabricGameTests', 'heals'))

    def test_two_tests_with_one_runtime_name_on_a_loader_fail(self):
        for simple in ('ForgeMachineTests', 'MachineTests'):
            self.java(self.common, f'demo.{simple}', f"""\
                public class {simple} {{ @GameTest public static void runs(GameTestHelper h) {{}} }}""")
        self.descriptor('demo.ForgeMachineTests', 'demo.MachineTests')
        with self.assertRaises(SystemExit):
            parity.loader_gametests(self.mod)


class RepositoryGateTests(unittest.TestCase):
    """Source-only checks of every dual port: runnable in the tooling job before any build."""

    def test_every_dual_port_has_a_gate(self):
        self.assertEqual(DUAL_PORTS, ['advancedgenetics', 'buildcraft', 'extremereactors',
                                      'jurassicraft', 'mekanism', 'mystcraft'])

    def test_every_gate_calls_the_shared_checks(self):
        for port in DUAL_PORTS:
            with self.subTest(port):
                gate = (ROOT / 'mods' / port / 'scripts/check_loader_parity.py').read_text()
                for call in ('parity.assert_built(', 'license_id=', 'parity.check_artifact_pair(',
                             'parity.check_loader_gametests(', 'parity.check_gametests('):
                    self.assertIn(call, gate)
                self.assertNotIn('import tomllib', gate)

    def test_gametest_allowlists_match_the_sources(self):
        for port in DUAL_PORTS:
            with self.subTest(port):
                mod = ROOT / 'mods' / port
                self.assertTrue((mod / parity.ALLOWLIST_NAME).is_file())
                parity.check_loader_gametests(mod)

    def test_fabric_descriptors_pin_the_build_versions(self):
        for port in DUAL_PORTS:
            mod = ROOT / 'mods' / port
            for descriptor in sorted((mod / 'src').glob('**/fabric/resources/fabric.mod.json')):
                with self.subTest(str(descriptor.relative_to(ROOT))):
                    fabric_mod = json.loads(descriptor.read_text())
                    # ${version} is expanded at build time; the module pins to it.
                    fabric_mod['depends'] = {key: value for key, value in fabric_mod['depends'].items()
                                             if '${' not in value}
                    parity.check_depends_floor(fabric_mod, mod / 'fabric')

    def test_shipped_access_wideners_widen_something(self):
        for path in sorted((ROOT / 'mods').glob('*/src/fabric/resources/*.accesswidener')):
            with self.subTest(str(path.relative_to(ROOT))):
                entries = [line for line in path.read_text().splitlines()[1:]
                           if line.split('#', 1)[0].strip()]
                self.assertTrue(entries)


if __name__ == '__main__':
    unittest.main()
