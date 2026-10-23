"""Shared artifact checks for the per-mod loader parity gates.

Each port keeps ``mods/<mod>/scripts/check_loader_parity.py`` for the assertions that
are specific to it.  The checks every gate repeats live here, so a fix (GameTest
exclusion, nested libraries, descriptor isolation, ...) lands once instead of
drifting between six hand-written copies.

The per-mod scripts add this directory to ``sys.path`` from their own location, so the
gates run from any working directory.
"""

from __future__ import annotations

import hashlib
import json
import zipfile
from pathlib import Path


def require(condition: object, message: str) -> None:
    """Abort the gate with ``message`` when ``condition`` is false."""
    if not condition:
        raise SystemExit(message)


def read_properties(path: Path) -> dict[str, str]:
    """Parse a ``port.properties`` file into a dictionary."""
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.startswith('#'))


def outer_class(name: str) -> str:
    """``a/b/C$Inner.class`` -> ``a/b/C``; strips only the class suffix and inner name."""
    return name.removesuffix('.class').split('$', 1)[0]


def assert_built(*paths: Path) -> None:
    """Require each expected artifact to exist before opening it."""
    for path in paths:
        require(path.is_file(), f'missing {path.name}; build both variants first')


def check_descriptors(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile, *,
                      mod_id: str, version: str, description: str | None = None,
                      license_id: str | None = None, fail=require) -> tuple[dict, dict]:
    """Check descriptor isolation and identity; return ``(forge_mods_toml, fabric_mod_json)``.

    ``description`` and ``license_id`` are optional because the ports historically
    checked different subsets.  Passing them opts into the stricter check.
    """
    import tomllib  # Lazy: the unit tests run on Python versions without tomllib.

    fail('fabric.mod.json' not in forge_jar.namelist(), 'Fabric descriptor leaked into Forge')
    fail('META-INF/mods.toml' not in fabric_jar.namelist(), 'Forge descriptor leaked into Fabric')
    forge_mod = tomllib.loads(forge_jar.read('META-INF/mods.toml').decode())['mods'][0]
    fabric_mod = json.loads(fabric_jar.read('fabric.mod.json'))
    fail(forge_mod['modId'] == fabric_mod['id'] == mod_id, 'Mod ID mismatch')
    fail(forge_mod['version'] == fabric_mod['version'] == version, 'Version mismatch')
    if description is not None:
        fail(forge_mod['description'].strip() == fabric_mod['description'] == description,
             'Description mismatch')
    if license_id is not None:
        fail(fabric_mod['license'] == license_id, 'License mismatch')
    return forge_mod, fabric_mod


def check_cross_loader(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile, *,
                       forge_forbidden: tuple[bytes, ...] = (b'net/fabricmc/',),
                       fabric_forbidden: tuple[bytes, ...] = (b'net/minecraftforge/',),
                       fail=require) -> None:
    """Reject class bytes that reference the other loader."""
    for jar, forbidden, label in ((forge_jar, forge_forbidden, 'Forge'),
                                  (fabric_jar, fabric_forbidden, 'Fabric')):
        for name in jar.namelist():
            if not name.endswith('.class'):
                continue
            body = jar.read(name)
            for marker in forbidden:
                fail(marker not in body, f'{label} class references {marker.decode()} in {name}')


def gameplay_paths(jar: zipfile.ZipFile, *,
                   roots: tuple[str, ...] = ('assets/', 'data/'),
                   extra: tuple[str, ...] = ()) -> set[str]:
    """The non-directory gameplay entries a jar ships."""
    return {name for name in jar.namelist()
            if not name.endswith('/') and (name.startswith(roots) or name in extra)}


def gametest_class_set(*roots: Path) -> set[str]:
    """The relative class paths a GameTest source set would compile to."""
    return {path.relative_to(root).with_suffix('').as_posix()
            for root in roots if root.is_dir() for path in root.rglob('*.java')}


def check_gametests(jars: tuple[zipfile.ZipFile, ...], *roots: Path,
                    class_substrings: tuple[str, ...] = (),
                    entry_substrings: tuple[str, ...] = ('/gametest/',),
                    entry_suffixes: tuple[str, ...] = ('.snbt',),
                    entry_prefixes: tuple[str, ...] = (),
                    fail=require) -> None:
    """Reject compiled GameTest classes and their package-relative fixtures.

    ``class_substrings`` catches classes that are not in the known source roots but
    still advertise themselves as GameTests (the pre-helper gates used this broad
    marker); ``roots`` catches the normally compiled ones.
    """
    classes = gametest_class_set(*roots)
    for jar in jars:
        for name in jar.namelist():
            if name.endswith('.class'):
                fail(outer_class(name) not in classes, f'GameTest class shipped: {name}')
                fail(not any(part in name.lower() for part in class_substrings),
                     f'GameTest class shipped: {name}')
            fail(not any(part in name for part in entry_substrings)
                 and not any(name.endswith(suffix) for suffix in entry_suffixes)
                 and not any(name.startswith(prefix) for prefix in entry_prefixes),
                 f'GameTest fixture shipped: {name}')


def check_nested_libraries(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile,
                           fabric_mod: dict, expected: tuple[str, ...], *, fail=require) -> int:
    """Verify the Fabric ``include(...)`` libraries and that Forge bundles none."""
    nested = {name for name in fabric_jar.namelist()
              if name.startswith('META-INF/jars/') and name.endswith('.jar')}
    declared = {entry.get('file') for entry in fabric_mod.get('jars', [])}
    for library in expected:
        fail(library in nested, f'Missing bundled library in the Fabric JAR: {library}')
        fail({'file': library} in fabric_mod.get('jars', []),
             f'Missing bundled library in fabric.mod.json jars: {library}')
    fail(nested == declared == set(expected),
         f'Bundled libraries differ: JAR {sorted(nested)}, descriptor {sorted(declared)}, '
         f'expected {sorted(expected)}')
    fail(not any(name.startswith('META-INF/jars/') for name in forge_jar.namelist()),
         'The Forge JAR nests Fabric libraries')
    return len(expected)


def print_hashes(*paths: Path) -> None:
    """Print the SHA-256 of each artifact, matching the existing gate output."""
    for path in paths:
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
