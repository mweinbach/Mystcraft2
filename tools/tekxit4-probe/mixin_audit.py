#!/usr/bin/env python3
"""Find exclusive mixin injections that collide between our ports and a modpack.

Two ``@Redirect``s (or ``@ModifyConstant``s) on the same instruction cannot both apply: whichever runs
second finds nothing and, with ``require = 1``, crashes the game at startup. An ``@Overwrite`` likewise
hides the method from other mods' injectors. This script reads every mixin config, mixin class and
refmap in the given jars (including jar-in-jar), maps targets to intermediary names, and reports pairs
of exclusive injectors on the same target from different mods. It also reports injectors that need the
original method body (an ``INVOKE``, ``FIELD``, ``CONSTANT``... point, including MixinExtras'
``@WrapOperation`` and ``@ModifyExpressionValue``) in a method another mod ``@Overwrite``s: Sodium
0.4.4 overwrites ``ItemRenderer.renderQuadList``, so a wrapped call inside it no longer exists.

usage:
  mixin_audit.py --pack <mods-dir> --ports <jar>... [--dump-pack FILE]
  mixin_audit.py --targets FILE --ports <jar>...      (check against a saved --dump-pack list)
"""
import argparse
import io
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

EXCLUSIVE = {
    "Lorg/spongepowered/asm/mixin/injection/Redirect;": "Redirect",
    "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;": "ModifyConstant",
    "Lorg/spongepowered/asm/mixin/Overwrite;": "Overwrite",
}
INJECTORS = dict(EXCLUSIVE, **{
    "Lorg/spongepowered/asm/mixin/injection/Inject;": "Inject",
    "Lorg/spongepowered/asm/mixin/injection/ModifyArg;": "ModifyArg",
    "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;": "ModifyArgs",
    "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;": "ModifyVariable",
    "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;": "WrapOperation",
    "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;": "ModifyExpressionValue",
    "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;": "ModifyReturnValue",
    "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;": "ModifyReceiver",
    "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;": "WrapWithCondition",
    "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;": "WrapWithCondition",
})
# Injection points that exist whatever the method body is; anything else needs the original instructions.
BODY_INDEPENDENT = {"HEAD", "RETURN", "TAIL", "OVERWRITE"}
MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
MEMBER = re.compile(r"(method_\d+|field_\d+|comp_\d+)")


class ClassFile:
    """The parts of a class file mixin audits need: constant pool, annotations, methods."""

    def __init__(self, data):
        self.data = data
        self.pos = 8
        count = self.u2()
        self.cp = [None] * count
        i = 1
        while i < count:
            tag = self.u1()
            if tag == 1:
                length = self.u2()
                self.cp[i] = ("utf8", self.data[self.pos:self.pos + length].decode("utf-8", "replace"))
                self.pos += length
            elif tag in (3, 4):
                self.cp[i] = ("int", struct.unpack(">i", self.read(4))[0])
            elif tag in (5, 6):
                self.cp[i] = ("long", self.read(8))
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                self.cp[i] = ("ref", self.u2())
            elif tag in (9, 10, 11, 12, 17, 18):
                self.cp[i] = ("pair", self.u2(), self.u2())
            elif tag == 15:
                self.cp[i] = ("handle", self.u1(), self.u2())
            else:
                raise ValueError(f"bad constant tag {tag}")
            i += 1
        self.pos += 6  # access, this, super
        interfaces = self.u2()
        self.pos += 2 * interfaces
        self.fields = [self.member() for _ in range(self.u2())]
        self.methods = [self.member() for _ in range(self.u2())]
        self.annotations = self.attributes_annotations()

    def u1(self):
        self.pos += 1
        return self.data[self.pos - 1]

    def u2(self):
        self.pos += 2
        return struct.unpack(">H", self.data[self.pos - 2:self.pos])[0]

    def read(self, n):
        self.pos += n
        return self.data[self.pos - n:self.pos]

    def utf8(self, index):
        return self.cp[index][1]

    def member(self):
        self.pos += 2
        name = self.utf8(self.u2())
        desc = self.utf8(self.u2())
        return name, desc, self.attributes_annotations()

    def attributes_annotations(self):
        found = []
        for _ in range(self.u2()):
            name = self.utf8(self.u2())
            length = struct.unpack(">I", self.read(4))[0]
            end = self.pos + length
            if name in ("RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations"):
                found += [self.annotation() for _ in range(self.u2())]
            self.pos = end
        return found

    def annotation(self):
        kind = self.utf8(self.u2())
        values = {}
        for _ in range(self.u2()):
            key = self.utf8(self.u2())
            values[key] = self.element()
        return kind, values

    def element(self):
        tag = chr(self.u1())
        if tag in "BCDFIJSZ":
            entry = self.cp[self.u2()]
            return entry[1] if entry[0] == "int" else None
        if tag == "s":
            return self.utf8(self.u2())
        if tag == "e":
            self.u2()
            return self.utf8(self.u2())
        if tag == "c":
            return self.utf8(self.u2())
        if tag == "@":
            return self.annotation()
        if tag == "[":
            return [self.element() for _ in range(self.u2())]
        raise ValueError(f"bad element tag {tag}")


def loose_json(raw):
    raw = re.sub(r"(?m)^\s*//.*$", "", raw)
    return json.loads(raw, strict=False)


def scan_jar(data, origin, out):
    """Collect injectors from every mixin config in this jar and its nested jars."""
    archive = zipfile.ZipFile(io.BytesIO(data))
    names = set(archive.namelist())
    mod_id = origin
    configs = []
    if "fabric.mod.json" in names:
        meta = loose_json(archive.read("fabric.mod.json").decode("utf-8", "replace"))
        mod_id = meta.get("id", origin)
        for entry in meta.get("mixins", []) or []:
            configs.append(entry if isinstance(entry, str) else entry.get("config"))
        for nested in meta.get("jars", []) or []:
            path = nested.get("file")
            if path in names:
                scan_jar(archive.read(path), f"{origin} > {Path(path).name}", out)
    for config in configs:
        if config not in names:
            continue
        cfg = loose_json(archive.read(config).decode("utf-8", "replace"))
        package = cfg.get("package", "")
        refmap = {}
        if cfg.get("refmap") in names:
            refmap = loose_json(archive.read(cfg["refmap"]).decode("utf-8", "replace")).get("mappings", {})
        priority = cfg.get("mixinPriority", 1000)
        for side in ("mixins", "client", "server"):
            for simple in cfg.get(side, []) or []:
                internal = f"{package}.{simple}".replace(".", "/")
                path = internal + ".class"
                if path not in names:
                    continue
                try:
                    collect(ClassFile(archive.read(path)), internal, refmap.get(internal, {}), mod_id, config,
                            side, priority, out)
                except (ValueError, IndexError, TypeError, struct.error) as error:
                    print(f"warning: cannot parse {origin}!{path}: {error}", file=sys.stderr)


def member_key(text):
    """Intermediary member name (globally unique) when present, else the raw selector."""
    found = MEMBER.findall(text or "")
    return found[-1] if found else (text or "")


def collect(cls, internal, refmap, mod_id, config, side, class_priority, out):
    targets = []
    priority = class_priority
    for kind, values in cls.annotations:
        if kind == MIXIN:
            targets += [v for v in values.get("value", []) or []]
            targets += [refmap.get(v, v).replace(".", "/") for v in values.get("targets", []) or []]
            priority = values.get("priority", priority)
    targets = [t[1:-1] if t.startswith("L") and t.endswith(";") else t for t in targets]
    for name, desc, annotations in cls.methods:
        for kind, values in annotations:
            label = INJECTORS.get(kind)
            if not label:
                continue
            if label == "Overwrite":
                selectors = [refmap.get(name, name)]
                at_value, at_target, ordinal = "OVERWRITE", "", -1
            else:
                selectors = [refmap.get(m, m) for m in values.get("method", []) or []]
                at = values.get("at")
                if isinstance(at, list):
                    at = at[0] if at else None
                at_values = at[1] if at else {}
                at_value = at_values.get("value", "")
                at_target = refmap.get(at_values.get("target", ""), at_values.get("target", ""))
                if label == "ModifyConstant":
                    constant = values.get("constant")
                    constant = constant[0] if isinstance(constant, list) and constant else constant
                    at_value, at_target = "CONSTANT", json.dumps(constant[1] if constant else {}, sort_keys=True)
                ordinal = at_values.get("ordinal", -1) if at else -1
            require = values.get("require")
            for target in targets:
                for selector in selectors:
                    out.append({
                        "mod": mod_id, "config": config, "mixin": internal, "handler": name, "kind": label,
                        "side": side, "priority": priority, "require": require,
                        "exclusive": kind in EXCLUSIVE,
                        "class": target, "method": member_key(selector), "at": at_value,
                        "target": member_key(at_target) if at_value != "CONSTANT" else at_target,
                        "ordinal": ordinal,
                    })


def scan(paths):
    out = []
    for path in paths:
        path = Path(path)
        for jar in (sorted(path.glob("*.jar")) if path.is_dir() else [path]):
            try:
                scan_jar(jar.read_bytes(), jar.name, out)
            except zipfile.BadZipFile:
                print(f"warning: not a jar: {jar}", file=sys.stderr)
    return out


def needs_body(entry):
    return entry["kind"] != "Overwrite" and entry["at"] not in BODY_INDEPENDENT


def collides(a, b):
    if a["class"] != b["class"] or a["method"] != b["method"]:
        return False
    if not a.get("exclusive", True) or not b.get("exclusive", True):
        # A body-dependent injector cannot find its point in a method someone else overwrote.
        return (a["kind"] == "Overwrite" and needs_body(b)) or (b["kind"] == "Overwrite" and needs_body(a))
    if a["kind"] == "Overwrite" or b["kind"] == "Overwrite":
        return True
    if a["kind"] != b["kind"] or a["at"] != b["at"] or a["target"] != b["target"]:
        return False
    return a["ordinal"] == -1 or b["ordinal"] == -1 or a["ordinal"] == b["ordinal"]


def describe(entry):
    return (f"{entry['mod']} {entry['kind']} {entry['mixin'].split('/')[-1]}.{entry['handler']} -> "
            f"{entry['class'].split('/')[-1]}.{entry['method']} @{entry['at']} {entry['target']}"
            f"{'' if entry['ordinal'] == -1 else ' ordinal=' + str(entry['ordinal'])}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pack", nargs="*", default=[], help="modpack mods directories or jars")
    parser.add_argument("--targets", help="JSON list written earlier by --dump-pack")
    parser.add_argument("--ports", nargs="+", required=True, help="our production jars")
    parser.add_argument("--dump-pack", help="write the pack's exclusive injectors to this JSON file")
    args = parser.parse_args(argv)
    ours = scan(args.ports)
    if not ours:
        print("error: no mixin injectors found in --ports; nothing was checked", file=sys.stderr)
        return 2
    our_mods = {e["mod"] for e in ours}
    pack = [e for e in scan(args.pack) if e["mod"] not in our_mods] if args.pack else []
    if args.targets:
        pack += json.loads(Path(args.targets).read_text())
    if args.dump_pack:
        keep = ("mod", "kind", "mixin", "handler", "class", "method", "at", "target", "ordinal")
        rows = sorted({json.dumps({k: e[k] for k in keep}, sort_keys=True) for e in pack if e.get("exclusive", True)})
        Path(args.dump_pack).write_text("[\n" + ",\n".join(rows) + "\n]\n")
    problems = 0
    for mine in ours:
        for theirs in pack:
            if collides(mine, theirs) and not (mine["require"] == 0 and theirs["kind"] == "Overwrite"):
                problems += 1
                print(f"CONFLICT {describe(mine)}\n    with {describe(theirs)}")
    for i, a in enumerate(ours):
        for b in ours[i + 1:]:
            if a["mod"] != b["mod"] and collides(a, b) and not any(
                    x["require"] == 0 and y["kind"] == "Overwrite" for x, y in ((a, b), (b, a))):
                problems += 1
                print(f"CONFLICT {describe(a)}\n    with {describe(b)}")
    print(f"{len(ours)} injectors in our jars, {len(pack)} in the pack, {problems} conflict(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
