#!/usr/bin/env python3
"""Find interface methods that only a Minecraft superclass would implement in a production (intermediary) jar.

An interface of ours may declare ``BlockState getBlockState()`` and rely on ``BlockEntity`` to implement it. That
works in a dev run, where Minecraft uses Mojang names, but Loom renames only Minecraft's method in the production jar
(to ``method_11010``), so the class no longer implements the interface and the first call throws
``AbstractMethodError``. Extreme Reactors' ``IMultiblockVariantProvider`` crashed the Tekxit 4 client this way.

For each concrete class in the given remapped Fabric jars (and their nested jars) whose superclass chain reaches a
``net/minecraft`` class, this lists abstract interface methods declared in those jars that nothing in the jars
implements, unless the name is one Minecraft keeps in production (``method_*``, ``comp_*``, a Forge SRG ``m_N_`` name
or a JDK method). Reobfuscated Forge jars have the same failure mode with SRG names, so they are checked too.

usage: check_unmapped_overrides.py <remapped-jar-or-dir>...
"""
import io
import struct
import sys
import zipfile
from pathlib import Path

ACC_INTERFACE, ACC_ABSTRACT, ACC_STATIC, ACC_PRIVATE = 0x0200, 0x0400, 0x0008, 0x0002
# Methods Minecraft classes inherit or override from the JDK keep their names in production.
JDK_NAMES = {
    "toString", "hashCode", "equals", "clone", "finalize", "compareTo", "iterator", "spliterator", "forEach", "close",
    "run", "call", "get", "accept", "apply", "test", "getAsInt", "getAsLong", "getAsDouble", "getAsBoolean", "size",
    "isEmpty", "contains", "length", "charAt", "subSequence", "values", "valueOf", "ordinal", "name", "getSerializedName",
}


class ClassInfo:
    def __init__(self, data):
        self.pos = 8
        self.data = data
        count = self.u2()
        cp = [None] * count
        i = 1
        while i < count:
            tag = self.u1()
            if tag == 1:
                n = self.u2()
                cp[i] = data[self.pos:self.pos + n].decode("utf-8", "replace")
                self.pos += n
            elif tag in (3, 4):
                self.pos += 4
            elif tag in (5, 6):
                self.pos += 8
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = ("ref", self.u2())
            elif tag in (9, 10, 11, 12, 17, 18):
                self.pos += 4
            elif tag == 15:
                self.pos += 3
            else:
                raise ValueError(f"bad constant tag {tag}")
            i += 1
        name_of = lambda index: cp[cp[index][1]] if index else None
        self.access = self.u2()
        self.name = name_of(self.u2())
        self.super = name_of(self.u2())
        self.interfaces = [name_of(self.u2()) for _ in range(self.u2())]
        for _ in range(self.u2()):
            self.pos += 6
            self.skip_attributes()
        self.methods = {}
        for _ in range(self.u2()):
            access, name, desc = self.u2(), cp[self.u2()], cp[self.u2()]
            self.skip_attributes()
            self.methods[(name, desc)] = access

    def u1(self):
        self.pos += 1
        return self.data[self.pos - 1]

    def u2(self):
        self.pos += 2
        return struct.unpack_from(">H", self.data, self.pos - 2)[0]

    def skip_attributes(self):
        for _ in range(self.u2()):
            self.pos += 2
            self.pos += struct.unpack_from(">I", self.data, self.pos)[0] + 4


def read_jar(data, origin, classes):
    archive = zipfile.ZipFile(io.BytesIO(data))
    for entry in archive.namelist():
        if entry.endswith(".jar"):
            read_jar(archive.read(entry), f"{origin} > {Path(entry).name}", classes)
        elif entry.endswith(".class") and not entry.startswith("META-INF/") and entry != "module-info.class":
            try:
                info = ClassInfo(archive.read(entry))
            except (ValueError, IndexError, struct.error):
                continue
            info.origin = origin
            classes.setdefault(info.name, info)


def keeps_name(name):
    """Fabric intermediary (method_/comp_) and Forge SRG (m_123_) names are what Minecraft has at runtime."""
    if name.startswith(("method_", "comp_", "<")) or name in JDK_NAMES:
        return True
    return name.startswith("m_") and name.endswith("_") and name[2:-1].isdigit()


def problems(classes):
    found = []
    for cls in classes.values():
        if cls.access & (ACC_INTERFACE | ACC_ABSTRACT):
            continue
        chain, cursor = [], cls
        while cursor is not None:
            chain.append(cursor)
            cursor = classes.get(cursor.super)
        outside = chain[-1].super
        if not outside or not outside.startswith("net/minecraft/"):
            continue  # The chain ends in the JDK or in a library we cannot see; either may implement the method.
        implemented = {key for c in chain for key, acc in c.methods.items() if not acc & (ACC_ABSTRACT | ACC_STATIC)}
        pending, seen, required = [i for c in chain for i in c.interfaces], set(), {}
        while pending:
            iface = classes.get(pending.pop())
            if iface is None or iface.name in seen:
                continue
            seen.add(iface.name)
            pending += iface.interfaces
            for key, acc in iface.methods.items():
                if acc & ACC_STATIC or acc & ACC_PRIVATE:
                    continue
                if acc & ACC_ABSTRACT:
                    required.setdefault(key, iface.name)
                else:
                    implemented.add(key)
        for (name, desc), iface in sorted(required.items()):
            if (name, desc) not in implemented and not keeps_name(name):
                found.append(f"{cls.origin}: {cls.name} leaves {iface}.{name}{desc} to {outside}, "
                             f"which has no method by that name in production")
    return found


def main(argv):
    if not argv:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    jars = [jar for arg in argv for jar in (sorted(Path(arg).glob("*.jar")) if Path(arg).is_dir() else [Path(arg)])]
    total = 0
    for jar in jars:
        classes = {}
        read_jar(jar.read_bytes(), jar.name, classes)
        found = problems(classes)
        total += len(found)
        for line in found:
            print(line)
    print(f"{len(jars)} jar(s), {total} unmapped interface implementation(s)")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
