"""Tests for scripts/fabric_clashes.py: method and mixin clashes between two sets of Fabric mods."""

import contextlib
import importlib.util
import io
import json
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('fabric_clashes', ROOT / 'scripts' / 'fabric_clashes.py')
fc = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = fc
SPEC.loader.exec_module(fc)

PUBLIC, INTERFACE, ABSTRACT = 0x0001, 0x0200, 0x0400


class ClassWriter:
    """Writes the subset of a class file fabric_clashes reads: supertypes, methods and their annotations."""

    def __init__(self):
        self.pool = []
        self.index = {}

    def utf8(self, text):
        key = ('utf8', text)
        if key not in self.index:
            data = text.encode()
            self.pool.append(b'\x01' + struct.pack('>H', len(data)) + data)
            self.index[key] = len(self.pool)
        return self.index[key]

    def cls(self, name):
        key = ('class', name)
        if key not in self.index:
            name_index = self.utf8(name)
            self.pool.append(b'\x07' + struct.pack('>H', name_index))
            self.index[key] = len(self.pool)
        return self.index[key]

    def element(self, value):
        if isinstance(value, str):
            return b's' + struct.pack('>H', self.utf8(value))
        if isinstance(value, tuple):  # ('class', descriptor)
            return b'c' + struct.pack('>H', self.utf8(value[1]))
        if isinstance(value, list):
            return b'[' + struct.pack('>H', len(value)) + b''.join(self.element(v) for v in value)
        return b'@' + self.annotation(value)

    def annotation(self, values):
        pairs = [(k, v) for k, v in values.items() if k != '@']
        body = struct.pack('>HH', self.utf8(values['@']), len(pairs))
        for name, value in pairs:
            body += struct.pack('>H', self.utf8(name)) + self.element(value)
        return body

    def annotations(self, annotations):
        if not annotations:
            return struct.pack('>H', 0)
        body = struct.pack('>H', len(annotations)) + b''.join(self.annotation(a) for a in annotations)
        return struct.pack('>HHI', 1, self.utf8('RuntimeInvisibleAnnotations'), len(body)) + body

    def write(self, name, access=PUBLIC, superclass='java/lang/Object', interfaces=(), methods=(), annotations=()):
        """methods: (access, name, desc, [annotation dicts])."""
        body = struct.pack('>HHH', access, self.cls(name), self.cls(superclass))
        body += struct.pack('>H', len(interfaces)) + b''.join(struct.pack('>H', self.cls(i)) for i in interfaces)
        body += struct.pack('>H', 0)  # fields
        body += struct.pack('>H', len(methods))
        for m_access, m_name, m_desc, m_annotations in methods:
            body += struct.pack('>HHH', m_access, self.utf8(m_name), self.utf8(m_desc)) + self.annotations(m_annotations)
        body += self.annotations(list(annotations))
        head = b'\xca\xfe\xba\xbe' + struct.pack('>HHH', 0, 61, len(self.pool) + 1) + b''.join(self.pool)
        return head + body


def class_bytes(*args, **kwargs):
    return ClassWriter().write(*args, **kwargs)


def mixin(target, *, interfaces=(), methods=()):
    return dict(interfaces=interfaces, methods=methods,
                annotations=[{'@': fc.MIXIN, 'value': [('class', f'L{target};')]}])


def jar(mod_id, classes, mixins=(), refmap=None, injected=None, nested=()):
    """An in-memory mod jar. classes: {name: write() kwargs}; mixins: mixin class names in its mixin config."""
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as z:
        meta = {'id': mod_id, 'version': '1.0', 'mixins': [f'{mod_id}.mixins.json']}
        if injected:
            meta['custom'] = {'loom:injected_interfaces': injected}
        z.writestr('fabric.mod.json', json.dumps(meta))
        z.writestr(f'{mod_id}.mixins.json', json.dumps({'package': f'{mod_id}.mixin', 'refmap': f'{mod_id}.refmap.json',
                                                        'mixins': list(mixins)}))
        z.writestr(f'{mod_id}.refmap.json', json.dumps({'mappings': refmap or {}}))
        for name, kwargs in classes.items():
            z.writestr(name + '.class', class_bytes(name, **kwargs))
        for name, data in nested:
            z.writestr(f'META-INF/jars/{name}', data)
    return out.getvalue()


def side(label, *jars):
    result = fc.Side(label)
    for data in jars:
        result.load_jar(data)
    return result


BLOCK_ENTITY = 'net/minecraft/class_2586'
CHEST = 'net/minecraft/class_2595'
ANCESTORS = {BLOCK_ENTITY: {BLOCK_ENTITY, 'java/lang/Object'}, CHEST: {CHEST, BLOCK_ENTITY, 'java/lang/Object'}}


class ParserTests(unittest.TestCase):
    def test_reads_supertypes_methods_and_nested_annotations(self):
        at = {'@': 'Lorg/spongepowered/asm/mixin/injection/At;', 'value': 'INVOKE', 'target': 'La;b()V'}
        data = class_bytes('x/Y', superclass='x/Base', interfaces=['x/I'],
                           methods=[(PUBLIC, 'run', '()V', [{'@': fc.REDIRECT, 'method': ['<init>'], 'at': at}])])
        info = fc.parse_class(data)
        self.assertEqual((info.name, info.superclass, info.interfaces), ('x/Y', 'x/Base', ['x/I']))
        self.assertEqual(info.methods[0].annotations[fc.REDIRECT]['at']['target'], 'La;b()V')
        self.assertEqual(info.methods[0].annotations[fc.REDIRECT]['method'], ['<init>'])

    def test_member_names(self):
        self.assertEqual(fc.member_name('Lnet/minecraft/class_1;method_2(I)V'), 'method_2')
        self.assertEqual(fc.member_name('method_2(I)V'), 'method_2')
        self.assertEqual(fc.member_name('<init>'), '<init>')
        self.assertEqual(fc.member_key('Lnet/minecraft/class_1;method_2(I)V'), 'method_2(I)V')


class MethodClashTests(unittest.TestCase):
    def layer(self, mod_id, method):
        """A Forge-style layer adding a default method to BlockEntity through a mixin-implemented interface."""
        iface = f'{mod_id}/Extensions'
        return jar(mod_id, {
            iface: dict(access=PUBLIC | INTERFACE | ABSTRACT, methods=[(PUBLIC, method, '()V', [])]),
            f'{mod_id}/mixin/BlockEntityMixin': mixin(BLOCK_ENTITY, interfaces=[iface]),
        }, mixins=['BlockEntityMixin'])

    def test_same_default_on_the_same_class_clashes(self):
        ours, theirs = side('ours', self.layer('ours', 'onLoad')), side('theirs', self.layer('theirs', 'onLoad'))
        clashes = fc.method_clashes(ours.additions(), theirs.additions(), ANCESTORS)
        self.assertEqual([(c['name'], c['ours'][0]) for c in clashes], [('onLoad', BLOCK_ENTITY)])

    def test_renamed_default_does_not_clash(self):
        ours, theirs = side('ours', self.layer('ours', 'mekanism$onLoad')), side('theirs', self.layer('theirs', 'onLoad'))
        self.assertEqual(fc.method_clashes(ours.additions(), theirs.additions(), ANCESTORS), [])

    def test_method_merged_into_a_subclass_clashes_with_a_default_on_its_superclass(self):
        ours = side('ours', self.layer('ours', 'getExpDrop'))
        theirs = side('theirs', jar('theirs', {
            'theirs/mixin/ChestMixin': mixin(CHEST, methods=[(PUBLIC, 'getExpDrop', '()V', [])]),
        }, mixins=['ChestMixin']))
        clashes = fc.method_clashes(ours.additions(), theirs.additions(), ANCESTORS)
        self.assertEqual([(c['ours'][0], c['theirs'][0]) for c in clashes], [(BLOCK_ENTITY, CHEST)])

    def test_unrelated_classes_and_handlers_do_not_clash(self):
        inject = {'@': 'Lorg/spongepowered/asm/mixin/injection/Inject;', 'method': ['tick']}
        ours = side('ours', jar('ours', {
            'ours/mixin/A': mixin('net/minecraft/class_1', methods=[(PUBLIC, 'foo', '()V', []), (PUBLIC, 'bar', '()V', [inject])]),
        }, mixins=['A']))
        theirs = side('theirs', jar('theirs', {
            'theirs/mixin/B': mixin('net/minecraft/class_2', methods=[(PUBLIC, 'foo', '()V', [])]),
            'theirs/mixin/C': mixin('net/minecraft/class_1', methods=[(PUBLIC, 'bar', '()V', [])]),
        }, mixins=['B', 'C']))
        self.assertEqual(fc.method_clashes(ours.additions(), theirs.additions(), {}), [])

    def test_injected_interfaces_and_nested_jars_are_read(self):
        iface = 'lib/Ext'
        nested = jar('lib', {iface: dict(access=PUBLIC | INTERFACE | ABSTRACT, methods=[(PUBLIC, 'onLoad', '()V', [])])},
                     injected={BLOCK_ENTITY: [iface]})
        theirs = side('theirs', jar('theirs', {}, nested=[('lib.jar', nested)]))
        self.assertIn(('onLoad', '()V'), theirs.additions()[BLOCK_ENTITY])


class PatchClashTests(unittest.TestCase):
    CALL = 'Lnet/minecraft/class_3726;method_16195(Lnet/minecraft/class_1297;)Lnet/minecraft/class_3726;'

    def patching(self, mod_id, annotation, *, named_call=None):
        at = {'@': 'Lorg/spongepowered/asm/mixin/injection/At;', 'value': 'INVOKE', 'target': named_call or self.CALL}
        values = {'@': annotation, 'method': ['<init>'], 'at': at}
        refmap = {f'{mod_id}/mixin/ClipMixin': {named_call: self.CALL}} if named_call else None
        return jar(mod_id, {f'{mod_id}/mixin/ClipMixin': mixin('net/minecraft/class_3959',
                                                               methods=[(PUBLIC, 'handler', '()V', [values])])},
                   mixins=['ClipMixin'], refmap=refmap)

    def test_two_redirects_of_one_call_clash_through_the_refmap(self):
        ours = side('ours', self.patching('ours', fc.REDIRECT, named_call='LCollisionContext;of(LEntity;)LCollisionContext;'))
        theirs = side('theirs', self.patching('theirs', fc.REDIRECT))
        clashes = fc.patch_clashes(ours.patches(), theirs.patches())
        self.assertEqual([(c['method'], c['call']) for c in clashes], [('<init>', fc.member_key(self.CALL))])

    def test_wrap_operation_beside_a_redirect_does_not_clash(self):
        wrap = 'Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;'
        ours, theirs = side('ours', self.patching('ours', wrap)), side('theirs', self.patching('theirs', fc.REDIRECT))
        self.assertEqual(fc.patch_clashes(ours.patches(), theirs.patches()), [])

    def test_overwrite_clashes_only_with_overwrites_and_call_targets(self):
        def overwriting(mod_id):
            return jar(mod_id, {f'{mod_id}/mixin/IngredientMixin': mixin('net/minecraft/class_1856', methods=[
                (PUBLIC, 'method_8102', '()V', [{'@': fc.OVERWRITE}])])}, mixins=['IngredientMixin'])

        head = jar('ours', {'ours/mixin/IngredientMixin': mixin('net/minecraft/class_1856', methods=[
            (PUBLIC, 'h', '()V', [{'@': 'Lorg/spongepowered/asm/mixin/injection/Inject;', 'method': ['method_8102'],
                                   'at': [{'@': 'Lorg/spongepowered/asm/mixin/injection/At;', 'value': 'HEAD'}]}])])},
                   mixins=['IngredientMixin'])
        theirs = side('theirs', overwriting('theirs'))
        self.assertEqual(fc.patch_clashes(side('ours', head).patches(), theirs.patches()), [])
        self.assertEqual(len(fc.patch_clashes(side('ours', overwriting('ours')).patches(), theirs.patches())), 1)


class MainTests(unittest.TestCase):
    def test_each_reference_jar_is_checked_on_its_own(self):
        """Two reference mods nesting different versions of one library: the second version's clash is still found."""
        layer = MethodClashTests().layer
        with tempfile.TemporaryDirectory() as tmp:
            paths = {}
            for name, data in (('minecraft', jar('minecraft', {BLOCK_ENTITY: {}})), ('ours', layer('ours', 'onLoad')),
                               ('old', layer('lib', 'somethingElse')), ('new', layer('lib', 'onLoad'))):
                paths[name] = str(Path(tmp) / f'{name}.jar')
                Path(paths[name]).write_bytes(data)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                status = fc.main(['--minecraft', paths['minecraft'], '--ours', paths['ours'],
                                  '--theirs', paths['old'], paths['new']])
        self.assertEqual(status, 1)
        self.assertEqual(out.getvalue().count('checked against: lib 1.0'), 2)
        self.assertIn('0 method clashes, 0 patch clashes\n', out.getvalue())
        self.assertIn('1 method clashes, 0 patch clashes\n', out.getvalue())


if __name__ == '__main__':
    unittest.main()
