#!/usr/bin/env python3
"""Fail when a mixin merges an unprefixed field into a Minecraft class.

Fields declared in a ``@Mixin`` class are copied into the target class unless
they are ``@Shadow``. Another mod's mixin that declares the same plain name then
fails with "conflicts with static field in the target" (Tekxit 4: Tree Chop's
``EntityChopSettingsMixin.KEY`` against JurassiCraft's ``Entity`` mixin). Every
merged field must therefore carry a mod prefix such as ``jurassicraft$data``.

Usage: check_mixin_member_names.py [ROOT ...]   (defaults to mods/)
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

MIXIN = re.compile(r"^\s*@Mixin\b", re.M)
# A field declaration: modifiers, a type, a name, then '=' or ';'. Methods have '('.
FIELD = re.compile(
    r"^(?P<indent>\s*)(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?P<mods>(?:(?:public|protected|private|static|final|volatile|transient)\s+)+)"
    r"(?P<type>[\w.$<>,?\[\] ]+?)\s+(?P<name>[A-Za-z_$][\w$]*)\s*(?:=|;)"
)
ANNOTATION = re.compile(r"@(\w+)")
STRING = re.compile(r'"(?:\\.|[^"\\])*"' + r"|'(?:\\.|[^'\\])'")


def offenders(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    if not MIXIN.search(text):
        return []
    found = []
    lines = text.splitlines()
    depth = 0
    for number, line in enumerate(lines, 1):
        code = STRING.sub('""', line.split("//", 1)[0])
        line_depth = depth
        depth += code.count("{") - code.count("}")
        match = FIELD.match(code)
        # Only direct members of the top-level mixin class are merged into the target.
        if not match or line_depth != 1:
            continue
        annotations = set(ANNOTATION.findall(code))
        back = number - 2
        while back >= 0 and lines[back].strip().startswith("@"):
            annotations.update(ANNOTATION.findall(lines[back]))
            back -= 1
        if "Shadow" in annotations or "$" in match["name"]:
            continue
        found.append(f"{path}:{number}: field '{match['name']}' is merged into the target without a mod prefix")
    return found


def main(argv: list[str]) -> int:
    roots = [Path(a) for a in argv] or [Path("mods")]
    problems = []
    for root in roots:
        files = [root] if root.is_file() else sorted(root.rglob("*.java"))
        for path in files:
            if "/mixin" in path.as_posix():
                problems.extend(offenders(path))
    for problem in problems:
        print(problem)
    if problems:
        print(f"FAIL: {len(problems)} unprefixed mixin field(s)")
        return 1
    print("PASS: every merged mixin field carries a mod prefix")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
