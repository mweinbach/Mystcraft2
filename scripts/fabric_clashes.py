#!/usr/bin/env python3
"""Find where two sets of Fabric mods change the same vanilla code in ways that cannot coexist.

Forge-to-Fabric layers such as Porting Lib, and the Forge API layers some ports in this repository carry, add
Forge-shaped methods to vanilla classes and patch vanilla code with mixins. Two such layers break each other when:

* both add a method with the same name and descriptor to related classes (a class and one of its supertypes, or two
  supertypes of one class). Two interface defaults make the JVM refuse the call ("Conflicting default methods"); a method
  a mixin merges into the class replaces the other layer's, so each layer's hooks run the other's code;
* both ``@Redirect`` the same call in the same method, which Mixin allows only once, or one ``@Overwrite``s a method the
  other injects into.

The jars are read as published (intermediary names); mixin targets are resolved through each jar's refmap. Nested jars
are read too. Vanilla supertypes come from Loom's intermediary Minecraft jar. Exit status 1 means clashes were found.

  fabric_clashes.py --minecraft <intermediary minecraft jar> [--mappings <tiny v2>] --ours <jar>... --theirs <jar>...
"""
from __future__ import annotations

import argparse
import io
import json
import re
import struct
import sys
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field

MIXIN = 'Lorg/spongepowered/asm/mixin/Mixin;'
OVERWRITE = 'Lorg/spongepowered/asm/mixin/Overwrite;'
REDIRECT = 'Lorg/spongepowered/asm/mixin/injection/Redirect;'
# Annotations whose methods Mixin does not merge into the target under their own name.
NOT_MERGED = {
    'Lorg/spongepowered/asm/mixin/Shadow;', 'Lorg/spongepowered/asm/mixin/Unique;',
    'Lorg/spongepowered/asm/mixin/gen/Accessor;', 'Lorg/spongepowered/asm/mixin/gen/Invoker;',
}
INJECTOR_PACKAGES = ('Lorg/spongepowered/asm/mixin/injection/', 'Lcom/llamalad7/mixinextras/injector/')
ACC_PRIVATE, ACC_STATIC, ACC_INTERFACE, ACC_ABSTRACT, ACC_SYNTHETIC = 0x0002, 0x0008, 0x0200, 0x0400, 0x1000


@dataclass
class Method:
    access: int
    name: str
    desc: str
    annotations: dict


@dataclass
class ClassFile:
    name: str
    access: int
    superclass: str | None
    interfaces: list
    methods: list
    annotations: dict


def parse_class(data: bytes) -> ClassFile:
    """The parts of a class file this check needs: names, supertypes, methods and their annotations."""
    buf = io.BytesIO(data)

    def u1():
        return buf.read(1)[0]

    def u2():
        return struct.unpack('>H', buf.read(2))[0]

    def u4():
        return struct.unpack('>I', buf.read(4))[0]

    buf.read(8)
    count = u2()
    pool = [None] * count
    i = 1
    while i < count:
        tag = u1()
        if tag == 1:
            pool[i] = buf.read(u2()).decode('utf-8', 'replace')
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = ('ref', u2())
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            buf.read(4)
        elif tag in (5, 6):
            buf.read(8)
            i += 1
        elif tag == 15:
            buf.read(3)
        else:
            raise ValueError(f'unknown constant pool tag {tag}')
        i += 1

    def class_name(index):
        return pool[pool[index][1]] if index else None

    def element(b):
        tag = chr(b.read(1)[0])
        index = struct.unpack('>H', b.read(2))[0]
        if tag == 's':
            return pool[index]
        if tag == 'c':
            return ('class', pool[index])
        if tag == 'e':
            b.read(2)
            return None
        if tag == '@':
            b.seek(-2, io.SEEK_CUR)
            return annotation(b)
        if tag == '[':
            return [element(b) for _ in range(index)]
        return None  # primitive constants are not needed

    def annotation(b):
        type_index, pairs = struct.unpack('>HH', b.read(4))
        values = {'@': pool[type_index]}
        for _ in range(pairs):
            name = pool[struct.unpack('>H', b.read(2))[0]]
            values[name] = element(b)
        return values

    def attributes():
        found = {}
        for _ in range(u2()):
            name = pool[u2()]
            body = buf.read(u4())
            if name in ('RuntimeInvisibleAnnotations', 'RuntimeVisibleAnnotations'):
                b = io.BytesIO(body)
                for _ in range(struct.unpack('>H', b.read(2))[0]):
                    values = annotation(b)
                    found[values['@']] = values
        return found

    def members():
        return [Method(u2(), pool[u2()], pool[u2()], attributes()) for _ in range(u2())]

    access = u2()
    name = class_name(u2())
    superclass = class_name(u2())
    interfaces = [class_name(u2()) for _ in range(u2())]
    members()
    methods = members()
    return ClassFile(name, access, superclass, interfaces, methods, attributes())


def member_name(selector: str) -> str:
    """'Lowner;name(desc)ret', 'name(desc)ret' or 'name' -> name."""
    selector = selector.split(';', 1)[1] if selector.startswith('L') and ';' in selector.split('(')[0] else selector
    return re.split(r'[(:]', selector, maxsplit=1)[0]


def member_key(selector: str) -> str:
    """A call target without its owner, so calls through a subclass compare equal."""
    if selector.startswith('L') and ';' in selector.split('(')[0]:
        selector = selector.split(';', 1)[1]
    return selector


@dataclass
class Side:
    """What one set of mod jars adds to and changes in vanilla classes."""
    label: str
    classes: dict = field(default_factory=dict)
    mixins: list = field(default_factory=list)  # (mixin class, refmap mappings for it)
    injected: dict = field(default_factory=lambda: defaultdict(set))
    mods: dict = field(default_factory=dict)

    def load_jar(self, data: bytes, name: str = '') -> None:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            names = jar.namelist()
            meta = {}
            if 'fabric.mod.json' in names:
                meta = json.loads(jar.read('fabric.mod.json').decode('utf-8', 'replace'), strict=False)
                self.mods[meta.get('id', name)] = meta.get('version')
            for entry in names:
                if entry.endswith('.class') and not entry.startswith('META-INF/'):
                    info = parse_class(jar.read(entry))
                    self.classes.setdefault(info.name, info)
            for target, interfaces in meta.get('custom', {}).get('loom:injected_interfaces', {}).items():
                self.injected[target].update(i.split('<')[0] for i in interfaces)
            for config_entry in meta.get('mixins', []):
                config_name = config_entry if isinstance(config_entry, str) else config_entry.get('config')
                if config_name not in names:
                    continue
                config = json.loads(jar.read(config_name).decode('utf-8', 'replace'), strict=False)
                refmap = {}
                if config.get('refmap') in names:
                    refmap = json.loads(jar.read(config['refmap']).decode('utf-8', 'replace')).get('mappings', {})
                package = config.get('package', '')
                for key in ('mixins', 'client', 'server'):
                    for mixin in config.get(key) or []:
                        cls = f'{package}.{mixin}'.replace('.', '/')
                        self.mixins.append((cls, refmap.get(cls, {})))
            for entry in names:
                if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):
                    self.load_jar(jar.read(entry), entry.rsplit('/', 1)[-1])

    def interface_methods(self, interface: str, seen=None) -> dict:
        """Default methods an interface declares or inherits, as {(name, desc): interface}."""
        seen = set() if seen is None else seen
        info = self.classes.get(interface)
        if interface in seen or info is None:
            return {}
        seen.add(interface)
        found = {}
        for parent in info.interfaces:
            found.update(self.interface_methods(parent, seen))
        for m in info.methods:
            if not m.access & (ACC_STATIC | ACC_PRIVATE | ACC_ABSTRACT) and not m.name.startswith('<'):
                found[(m.name, m.desc)] = interface
        return found

    def mixin_targets(self, info: ClassFile, refmap: dict) -> list:
        values = info.annotations[MIXIN]
        targets = [v[1][1:-1] for v in values.get('value') or [] if isinstance(v, tuple)]
        for target in values.get('targets') or []:
            target = refmap.get(target, target)
            targets.append(target.replace('.', '/'))
        return targets

    def additions(self) -> dict:
        """{vanilla class: {(name, desc): how}} for every method this side adds to vanilla classes."""
        added = defaultdict(dict)
        for cls, refmap in self.mixins:
            info = self.classes.get(cls)
            if info is None or MIXIN not in info.annotations:
                continue
            for target in self.mixin_targets(info, refmap):
                for interface in info.interfaces:
                    for key, owner in self.interface_methods(interface).items():
                        added[target].setdefault(key, f'default method of {owner}')
                if info.access & ACC_INTERFACE:
                    continue  # accessor and invoker mixins
                for m in info.methods:
                    if m.name.startswith('<') or m.access & (ACC_STATIC | ACC_PRIVATE | ACC_SYNTHETIC):
                        continue
                    if set(m.annotations) & NOT_MERGED or any(a.startswith(INJECTOR_PACKAGES) for a in m.annotations):
                        continue
                    if m.name.startswith('method_') or OVERWRITE in m.annotations:
                        continue  # overrides or overwrites of vanilla methods, not additions
                    added[target][(m.name, m.desc)] = f'method merged by {cls}'
        for target, interfaces in self.injected.items():
            for interface in interfaces:
                for key, owner in self.interface_methods(interface).items():
                    added[target].setdefault(key, f'default method of {owner}')
        return added

    def patches(self) -> list:
        """[(vanilla class, method name, kind, call target or None, mixin)] for redirects, overwrites and injections."""
        found = []
        for cls, refmap in self.mixins:
            info = self.classes.get(cls)
            if info is None or MIXIN not in info.annotations:
                continue
            for target in self.mixin_targets(info, refmap):
                for m in info.methods:
                    if OVERWRITE in m.annotations:
                        found.append((target, m.name, 'overwrite', None, cls))
                        continue
                    for kind, values in m.annotations.items():
                        if not kind.startswith(INJECTOR_PACKAGES):
                            continue
                        selectors = values.get('method') or []
                        selectors = selectors if isinstance(selectors, list) else [selectors]
                        ats = values.get('at') or []
                        ats = ats if isinstance(ats, list) else [ats]
                        call_targets = [refmap.get(a.get('target'), a.get('target')) for a in ats
                                        if isinstance(a, dict) and a.get('target')]
                        for selector in selectors:
                            method = member_name(refmap.get(selector, selector))
                            redirect = kind == REDIRECT
                            for call in call_targets or [None]:
                                found.append((target, method, 'redirect' if redirect else 'inject',
                                              member_key(call) if call else None, cls))
        return found


def read_ancestors(minecraft_jars) -> dict:
    """{class: every supertype including itself} for the vanilla classes in Loom's intermediary Minecraft jar(s)."""
    parents = {}
    for path in minecraft_jars:
        with zipfile.ZipFile(path) as jar:
            for entry in jar.namelist():
                if entry.endswith('.class'):
                    info = parse_class(jar.read(entry))
                    parents[info.name] = [c for c in [info.superclass, *info.interfaces] if c]
    cache = {}

    def ancestors(cls):
        if cls not in cache:
            cache[cls] = {cls}
            for parent in parents.get(cls, []):
                cache[cls] |= ancestors(parent)
        return cache[cls]

    return {cls: ancestors(cls) for cls in parents}


def related(a: str, b: str, ancestors: dict) -> bool:
    """Whether some class has both a and b among its supertypes (or is one of them)."""
    return a == b or any(a in supers and b in supers for supers in ancestors.values())


def method_clashes(ours: dict, theirs: dict, ancestors: dict) -> list:
    """Methods both sides add, with the same name and descriptor, to related classes."""
    by_key = defaultdict(list)
    for target, methods in theirs.items():
        for key, how in methods.items():
            by_key[key].append((target, how))
    clashes = []
    for target, methods in ours.items():
        for key, how in methods.items():
            for their_target, their_how in by_key.get(key, []):
                if related(target, their_target, ancestors):
                    clashes.append({'name': key[0], 'desc': key[1], 'ours': (target, how), 'theirs': (their_target, their_how)})
    return clashes


def patch_clashes(ours: list, theirs: list) -> list:
    """Redirects of the same call in the same method, and overwrites of methods the other side patches.

    Mixin merges every overwrite before it runs any injector, so an injection at HEAD or RETURN still lands in an
    overwritten method; one aimed at a call or field may not find it in the new body.
    """
    clashes = []
    for target, method, kind, call, cls in ours:
        for their_target, their_method, their_kind, their_call, their_cls in theirs:
            if target != their_target or method != their_method:
                continue
            same_redirect = kind == their_kind == 'redirect' and call == their_call
            overwritten = (kind == 'overwrite' and (their_kind == 'overwrite' or their_call)) or \
                          (their_kind == 'overwrite' and (kind == 'overwrite' or call))
            if same_redirect or overwritten:
                clashes.append({'target': target, 'method': method, 'call': call or their_call,
                                'ours': (kind, cls), 'theirs': (their_kind, their_cls)})
    return clashes


def load_names(path) -> dict:
    """intermediary -> named class names from a tiny v2 file (optional, for readable output)."""
    if not path:
        return {}
    with open(path, encoding='utf-8') as f:
        header = f.readline().rstrip('\n').split('\t')[3:]
        inter, named = header.index('intermediary'), header.index('named')
        names = {}
        for line in f:
            if line.startswith('c\t'):
                columns = line.rstrip('\n').split('\t')[1:]
                names[columns[inter]] = columns[named]
    return names


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--minecraft', nargs='+', required=True, help="Loom's intermediary Minecraft jar(s)")
    parser.add_argument('--mappings', help='tiny v2 mappings with intermediary and named columns, for readable output')
    parser.add_argument('--ours', nargs='+', required=True)
    parser.add_argument('--theirs', nargs='+', required=True)
    args = parser.parse_args(argv)
    sides = []
    for label, jars in (('ours', args.ours), ('theirs', args.theirs)):
        side = Side(label)
        for jar in jars:
            with open(jar, 'rb') as f:
                side.load_jar(f.read(), jar)
        sides.append(side)
    ours, theirs = sides
    ancestors = read_ancestors(args.minecraft)
    names = load_names(args.mappings)
    show = lambda cls: names.get(cls, cls).replace('/', '.')
    methods = method_clashes(ours.additions(), theirs.additions(), ancestors)
    patches = patch_clashes(ours.patches(), theirs.patches())
    print('checked against: ' + ', '.join(f'{k} {v}' for k, v in sorted(theirs.mods.items(), key=lambda kv: str(kv[0]))))
    for c in methods:
        print(f"method {show(c['ours'][0])}.{c['name']}{c['desc']}\n"
              f"    ours:   {c['ours'][1]}\n    theirs: {show(c['theirs'][0])}: {c['theirs'][1]}")
    for c in patches:
        call = f" call {c['call']}" if c['call'] else ''
        print(f"patch {show(c['target'])}.{c['method']}{call}\n"
              f"    ours:   {c['ours'][0]} in {c['ours'][1]}\n    theirs: {c['theirs'][0]} in {c['theirs'][1]}")
    print(f'{len(methods)} method clashes, {len(patches)} patch clashes')
    return 1 if methods or patches else 0


if __name__ == '__main__':
    sys.exit(main())
