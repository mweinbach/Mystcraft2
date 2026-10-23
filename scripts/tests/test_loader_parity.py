"""Exercise the shared loader-parity helpers with synthetic artifacts."""

import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('loader_parity', ROOT / 'scripts' / 'loader_parity.py')
parity = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(parity)

try:
    import tomllib  # noqa: F401
    HAVE_TOML = True
except ModuleNotFoundError:
    HAVE_TOML = False


def write_jar(path, entries):
    with zipfile.ZipFile(path, 'w') as jar:
        for name, data in entries.items():
            jar.writestr(name, data)
    return path


FORGE_TOML = '[[mods]]\nmodId="demo"\nversion="1.0"\ndescription="Demo description"\n'
FABRIC_JSON = json.dumps({'id': 'demo', 'version': '1.0',
                          'description': 'Demo description', 'license': 'MIT'})


class LoaderParityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_outer_class_strips_nested_class(self):
        self.assertEqual(parity.outer_class('a/b/C$Inner.class'), 'a/b/C')
        self.assertEqual(parity.outer_class('a/b/C.class'), 'a/b/C')

    def test_read_properties_ignores_comments_and_blank_values(self):
        path = self.dir / 'port.properties'
        path.write_text('# c\nmod_id=demo\nmod_version=1.0\nempty=\n')
        self.assertEqual(parity.read_properties(path),
                         {'mod_id': 'demo', 'mod_version': '1.0', 'empty': ''})

    @unittest.skipUnless(HAVE_TOML, 'tomllib needs Python 3.11+')
    def test_descriptors_pass_and_reject_a_leak(self):
        forge = write_jar(self.dir / 'forge.jar', {'META-INF/mods.toml': FORGE_TOML})
        fabric = write_jar(self.dir / 'fabric.jar', {'fabric.mod.json': FABRIC_JSON})
        with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
            parity.check_descriptors(a, b, mod_id='demo', version='1.0',
                                     description='Demo description', license_id='MIT')
            with self.assertRaises(SystemExit):
                parity.check_descriptors(a, b, mod_id='other', version='1.0')

        leaked = write_jar(self.dir / 'leaked.jar',
                           {'META-INF/mods.toml': FORGE_TOML, 'fabric.mod.json': FABRIC_JSON})
        with zipfile.ZipFile(leaked) as b, zipfile.ZipFile(forge) as a:
            with self.assertRaises(SystemExit):
                parity.check_descriptors(a, b, mod_id='demo', version='1.0')

    def test_cross_loader_scan(self):
        forge = write_jar(self.dir / 'f.jar', {'demo/A.class': b'net/fabricmc/Loader'})
        fabric = write_jar(self.dir / 'b.jar', {'demo/A.class': b'net/minecraftforge/Mod'})
        with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
            with self.assertRaises(SystemExit):
                parity.check_cross_loader(a, b)
            parity.check_cross_loader(a, b, forge_forbidden=(), fabric_forbidden=())

    def test_gameplay_paths(self):
        jar = write_jar(self.dir / 'g.jar', {
            'assets/demo/x.json': b'{}',
            'data/demo/y.json': b'{}',
            'pack.mcmeta': b'{}',
            'demo/A.class': b'x',
        })
        with zipfile.ZipFile(jar) as z:
            self.assertEqual(parity.gameplay_paths(z),
                             {'assets/demo/x.json', 'data/demo/y.json'})
            self.assertIn('pack.mcmeta', parity.gameplay_paths(z, extra=('pack.mcmeta',)))

    def test_gametest_exclusion_rejects_classes_and_fixtures(self):
        root = self.dir / 'gametest'
        (root / 'demo').mkdir(parents=True)
        (root / 'demo' / 'DemoTest.java').write_text('class DemoTest {}')
        clean = write_jar(self.dir / 'clean.jar', {'demo/A.class': b'x'})
        dirty = write_jar(self.dir / 'dirty.jar', {'demo/DemoTest.class': b'x'})
        fixture = write_jar(self.dir / 'fixture.jar',
                            {'data/demo/gametest/structures/x.snbt': b'x'})
        with zipfile.ZipFile(clean) as z:
            parity.check_gametests((z,), root)
        for path in (dirty, fixture):
            with zipfile.ZipFile(path) as z, self.assertRaises(SystemExit):
                parity.check_gametests((z,), root)

    def test_gametest_class_marker_catches_unknown_source_sets(self):
        jar = write_jar(self.dir / 'marker.jar', {'demo/SomeGametestThing.class': b'x'})
        with zipfile.ZipFile(jar) as z, self.assertRaises(SystemExit):
            parity.check_gametests((z,), class_substrings=('gametest',))

    def test_nested_libraries(self):
        library = 'META-INF/jars/energy-2.3.0.jar'
        fabric = write_jar(self.dir / 'fabric.jar', {
            'fabric.mod.json': json.dumps({'jars': [{'file': library}]}),
            library: b'jar',
        })
        forge = write_jar(self.dir / 'forge.jar', {'demo/A.class': b'x'})
        with zipfile.ZipFile(forge) as a, zipfile.ZipFile(fabric) as b:
            mod = json.loads(b.read('fabric.mod.json'))
            self.assertEqual(parity.check_nested_libraries(a, b, mod, (library,)), 1)
            with self.assertRaises(SystemExit):
                parity.check_nested_libraries(a, b, mod, (library, 'META-INF/jars/core.jar'))

    def test_assert_built(self):
        missing = self.dir / 'nope.jar'
        with self.assertRaises(SystemExit):
            parity.assert_built(missing)
        parity.assert_built(__file__ and Path(__file__))


if __name__ == '__main__':
    unittest.main()
