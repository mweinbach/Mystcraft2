"""Shared artifact checks for the per-mod loader parity gates.

Each port keeps ``mods/<mod>/scripts/check_loader_parity.py`` for the assertions that
are specific to it.  The checks every gate repeats live here, so a fix (GameTest
exclusion, nested libraries, descriptor isolation, mixin refmaps, ...) lands once
instead of drifting between six hand-written copies.

Every gate calls, for each built jar it owns (Mekanism's module jars included):

* ``assert_built`` before opening anything;
* ``check_descriptors`` with ``license_id`` (both descriptors carry the port license);
* ``check_mixin_refmaps`` - every mixin config the jar declares exists, its classes
  are in the jar and the refmap it names is in *the same jar* and maps something;
* ``check_entrypoints`` - every ``fabric.mod.json`` entrypoint class is in the jar;
* ``check_depends_floor`` - ``fabric.mod.json`` minimum versions match the build;
* ``check_access_widener`` - a shipped widener widens at least one member;
* ``check_nested_libraries`` - the Fabric ``include(...)`` jars, none on Forge;
* ``check_gametests`` / ``check_gametest_fixtures`` - no GameTest code or fixtures ship;
* ``check_gametest_symmetry`` - Forge and Fabric register the same GameTest names,
  except those listed in ``mods/<mod>/gametest-loader-only.txt``.

The per-mod scripts add this directory to ``sys.path`` from their own location, so the
gates run from any working directory.  They run on Python 3.9+: ``mods.toml`` is read
with ``tomllib`` on 3.11+, otherwise with ``tomli`` (installed, or the copy in the
repository's ``.local/tomli``).
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
MODS = ROOT / 'mods'

# Fabric mods published by this repository's own included builds: coordinate -> mods/<dir>.
INCLUDED_BUILDS = {'ports.fluidphysics:fluidphysics-fabric': 'fluidphysics'}
# fabric.mod.json dependency id -> the Gradle coordinate whose version is its floor.
DEPENDENCY_COORDINATES = {
    'fabricloader': 'net.fabricmc:fabric-loader',
    'fabric-api': 'net.fabricmc.fabric-api:fabric-api',
    'minecraft': 'com.mojang:minecraft',
    'team_reborn_energy': 'teamreborn:energy',
    'fabric-permissions-api-v0': 'me.lucko:fabric-permissions-api',
    'fluidphysics': 'ports.fluidphysics:fluidphysics-fabric',
}
# Every Fabric descriptor must pin these, at the versions the build compiles against.
REQUIRED_DEPENDENCIES = ('fabricloader', 'fabric-api', 'minecraft', 'java')
# A refmap maps to intermediary on Fabric and to SRG on Forge.
FABRIC_REFMAP_MARKERS = ('method_', 'field_', 'class_')
FORGE_REFMAP_MARKERS = ('m_', 'f_')


def require(condition: object, message: str) -> None:
    """Abort the gate with ``message`` when ``condition`` is false."""
    if not condition:
        raise SystemExit(message)


def toml_module():
    """``tomllib`` on Python 3.11+, else ``tomli`` (installed or in ``.local/tomli``)."""
    try:
        import tomllib
        return tomllib
    except ModuleNotFoundError:
        pass
    try:
        import tomli
        return tomli
    except ModuleNotFoundError:
        pass
    shim = ROOT / '.local' / 'tomli'
    if (shim / 'tomli').is_dir():
        sys.path.insert(0, str(shim))
        import tomli
        return tomli
    raise SystemExit('FAIL: reading mods.toml needs Python 3.11+ or tomli '
                     '(python3 -m pip install --target .local/tomli tomli)')


def load_toml(text: str) -> dict:
    return toml_module().loads(text)


def read_properties(path: Path) -> dict[str, str]:
    """Parse a ``port.properties`` or ``gradle.properties`` file into a dictionary."""
    return dict((key.strip(), value.strip()) for key, value in
                (line.split('=', 1) for line in path.read_text().splitlines()
                 if '=' in line and not line.lstrip().startswith('#')))


def outer_class(name: str) -> str:
    """``a/b/C$Inner.class`` -> ``a/b/C``; strips only the class suffix and inner name."""
    if name.endswith('.class'):
        name = name[:-len('.class')]
    return name.split('$', 1)[0]


def class_entry(name: str) -> str:
    """``a.b.C`` (or ``a.b.C::member``) -> ``a/b/C.class``."""
    return name.split('::', 1)[0].replace('.', '/') + '.class'


def assert_built(*paths: Path) -> None:
    """Require each expected artifact to exist before opening it."""
    for path in paths:
        require(path.is_file(), f'missing {path.name}; build both variants first')


def check_descriptors(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile, *,
                      mod_id: str, version: str, description: str | None = None,
                      license_id: str | None = None, fabric_license_id: str | None = None,
                      fail=require) -> tuple[dict, dict]:
    """Check descriptor isolation and identity; return ``(forge_mods_toml, fabric_mod_json)``.

    ``license_id`` checks the top-level ``license`` of ``mods.toml`` and, unless
    ``fabric_license_id`` overrides it (a port without an SPDX license gives Fabric a
    ``LicenseRef-``), ``fabric.mod.json``.  ``description`` is optional because the
    ports historically checked different subsets.
    """
    fail('fabric.mod.json' not in forge_jar.namelist(), 'Fabric descriptor leaked into Forge')
    fail('META-INF/mods.toml' not in fabric_jar.namelist(), 'Forge descriptor leaked into Fabric')
    forge_toml = load_toml(forge_jar.read('META-INF/mods.toml').decode())
    forge_mod = forge_toml['mods'][0]
    fabric_mod = json.loads(fabric_jar.read('fabric.mod.json'))
    fail(forge_mod['modId'] == fabric_mod['id'] == mod_id, 'Mod ID mismatch')
    fail(forge_mod['version'] == fabric_mod['version'] == version, 'Version mismatch')
    if description is not None:
        fail(forge_mod['description'].strip() == fabric_mod['description'] == description,
             'Description mismatch')
    if license_id is not None:
        fabric_license = fabric_license_id or license_id
        fail(fabric_mod.get('license') == fabric_license,
             f'License mismatch: fabric.mod.json {fabric_mod.get("license")!r}, expected {fabric_license!r}')
        fail(forge_toml.get('license') == license_id,
             f'License mismatch: mods.toml {forge_toml.get("license")!r}, expected {license_id!r}')
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


# --- Mixins -------------------------------------------------------------------------


def manifest_mixin_configs(jar: zipfile.ZipFile) -> list[str]:
    """The configs a Forge jar's manifest ``MixinConfigs`` attribute names."""
    if 'META-INF/MANIFEST.MF' not in jar.namelist():
        return []
    # Manifest lines wrap at 72 bytes; a continuation line starts with one space.
    text = jar.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n').replace('\n ', '')
    for line in text.splitlines():
        key, _, value = line.partition(':')
        if key.strip() == 'MixinConfigs':
            return [name.strip() for name in value.split(',') if name.strip()]
    return []


def declared_mixin_configs(jar: zipfile.ZipFile, fabric_mod: dict | None = None) -> list[str]:
    """Mixin configs a jar asks its loader to apply: ``fabric.mod.json`` and the manifest."""
    configs = manifest_mixin_configs(jar)
    for entry in (fabric_mod or {}).get('mixins', []):
        configs.append(entry['config'] if isinstance(entry, dict) else entry)
    return list(dict.fromkeys(configs))


def check_mixin_refmaps(jar: zipfile.ZipFile, *, label: str, fabric_mod: dict | None = None,
                        markers: tuple[str, ...] = (), fail=require) -> int:
    """Check every mixin config in ``jar`` against that jar alone; return how many.

    The configs are the ones the jar declares (``fabric.mod.json`` ``mixins`` or the
    manifest's ``MixinConfigs``) plus any ``*mixins.json`` at the jar root.  Each must
    parse, list only classes the jar contains and, when it lists any mixin, name a
    refmap that is in this same jar, parses and maps at least one member of a class in
    the config's package.  Mixin resolves the refmap from the config's own jar at
    runtime, so a refmap in a sibling jar (Mekanism's main jar, for its modules) does
    not count - that is how MEK-1 crashed while every gate passed.  ``markers`` (e.g.
    ``FABRIC_REFMAP_MARKERS``) must appear in the mappings: a refmap that still holds
    development names is as broken as a missing one.
    """
    names = set(jar.namelist())
    declared = declared_mixin_configs(jar, fabric_mod)
    for config_name in declared:
        fail(config_name in names, f'{label}: declared mixin config {config_name} is not in the jar')
    root_configs = [name for name in names if '/' not in name and name.endswith('mixins.json')]
    configs = list(dict.fromkeys(declared + sorted(root_configs)))
    for config_name in configs:
        if config_name not in names:
            continue
        fail(config_name in declared, f'{label}: {config_name} ships but no descriptor applies it')
        try:
            config = json.loads(jar.read(config_name))
        except ValueError as error:
            fail(False, f'{label}: {config_name} is not valid JSON: {error}')
            continue
        package = config.get('package', '').replace('.', '/')
        mixins = [name for side in ('mixins', 'client', 'server') for name in config.get(side, [])]
        for mixin in mixins:
            entry = f'{package}/{mixin.replace(".", "/")}.class'
            fail(entry in names, f'{label}: {config_name} lists {mixin}, which is not in the jar')
        if config.get('plugin'):
            fail(class_entry(config['plugin']) in names,
                 f'{label}: {config_name} plugin {config["plugin"]} is not in the jar')
        if not mixins:
            continue
        refmap_name = config.get('refmap')
        fail(refmap_name, f'{label}: {config_name} lists mixins but names no refmap')
        if not refmap_name:
            continue
        fail(refmap_name in names, f'{label}: {config_name} names refmap {refmap_name}, '
                                   'which is not in the same jar')
        if refmap_name not in names:
            continue
        try:
            mappings = json.loads(jar.read(refmap_name)).get('mappings')
        except ValueError as error:
            fail(False, f'{label}: refmap {refmap_name} is not valid JSON: {error}')
            continue
        fail(isinstance(mappings, dict) and any(mappings.values()),
             f'{label}: refmap {refmap_name} for {config_name} is empty')
        if not isinstance(mappings, dict):
            continue
        own = {key: value for key, value in mappings.items()
               if value and key.startswith(package + '/')}
        fail(own, f'{label}: refmap {refmap_name} maps no mixin in {config_name}\'s package '
                  f'{config.get("package")}')
        if markers and own:
            text = json.dumps(own)
            fail(any(marker in text for marker in markers),
                 f'{label}: refmap {refmap_name} has no production ({"/".join(markers)}) names')
    return len(configs)


# --- fabric.mod.json -----------------------------------------------------------------


def check_entrypoints(jar: zipfile.ZipFile, fabric_mod: dict, *, label: str = 'Fabric',
                      fail=require) -> int:
    """Require the class behind every ``fabric.mod.json`` entrypoint in the jar; return how many."""
    names = set(jar.namelist())
    count = 0
    for key, entries in fabric_mod.get('entrypoints', {}).items():
        for entry in entries:
            value = entry['value'] if isinstance(entry, dict) else entry
            count += 1
            fail(class_entry(value) in names, f'{label}: {key} entrypoint {value} is not in the jar')
    return count


def gradle_versions(build_dir: Path) -> dict[str, str]:
    """Versions a Loom build resolves: ``group:artifact`` -> version, plus ``java``.

    Reads ``build.gradle`` literal coordinates and resolves ``${project.x}`` / ``${x}``
    from ``gradle.properties``; this repository's included builds (Fluid Physics) take
    their ``mod_version`` from their ``port.properties``.
    """
    text = (build_dir / 'build.gradle').read_text()
    properties = read_properties(build_dir / 'gradle.properties') \
        if (build_dir / 'gradle.properties').is_file() else {}
    versions = {}
    for group, artifact, version in re.findall(r"['\"]([\w.\-]+):([\w.\-]+):([^'\":@]+)['\"]", text):
        coordinate = f'{group}:{artifact}'
        variable = re.fullmatch(r'\$\{?(?:project\.)?(\w+)\}?', version)
        if variable:
            if variable.group(1) in properties:
                version = properties[variable.group(1)]
            elif coordinate in INCLUDED_BUILDS:
                version = read_properties(MODS / INCLUDED_BUILDS[coordinate] / 'port.properties')['mod_version']
            else:
                continue
        versions.setdefault(coordinate, version)
    java = re.search(r'JavaLanguageVersion\.of\((\d+)\)', text)
    if java:
        versions['java'] = java.group(1)
    return versions


def version_floor(constraint: str) -> str | None:
    """The minimum version a Fabric dependency constraint admits, or None for ``*``."""
    constraint = constraint.strip()
    if constraint in ('*', ''):
        return None
    return re.sub(r'^(>=|=|\^|~)', '', constraint).strip()


def check_depends_floor(fabric_mod: dict, build_dir: Path, *, label: str = 'Fabric',
                        required: tuple[str, ...] = REQUIRED_DEPENDENCIES,
                        extra: dict[str, str] | None = None, fail=require) -> int:
    """``fabric.mod.json`` ``depends`` floors must equal the versions ``build_dir`` builds with.

    ``required`` ids must be declared with a floor.  Any other declared dependency with a
    known coordinate (``DEPENDENCY_COORDINATES``) or listed in ``extra`` (id -> version)
    is compared when it has a floor.  Fabric API's ``+1.19.2`` build suffix is ignored.
    Returns how many floors were compared.
    """
    built = gradle_versions(build_dir)
    expected = {'java': built.get('java')}
    for dependency, coordinate in DEPENDENCY_COORDINATES.items():
        if coordinate in built:
            expected[dependency] = built[coordinate].split('+', 1)[0]
    expected.update(extra or {})
    depends = fabric_mod.get('depends', {})
    compared = 0
    for dependency in required:
        fail(expected.get(dependency), f'{label}: cannot find the {dependency} version in {build_dir}')
        fail(dependency in depends, f'{label}: fabric.mod.json does not depend on {dependency}')
        fail(dependency not in depends or version_floor(depends[dependency]),
             f'{label}: fabric.mod.json {dependency} has no minimum version')
    for dependency, constraint in depends.items():
        floor = version_floor(constraint)
        if floor is None or not expected.get(dependency):
            continue
        compared += 1
        fail(floor == expected[dependency],
             f'{label}: fabric.mod.json depends on {dependency} {constraint}, '
             f'but the build uses {expected[dependency]}')
    return compared


def check_access_widener(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile, fabric_mod: dict, *,
                         label: str = 'Fabric', fail=require) -> int:
    """A widener that ships must be declared, valid and widen something; return its entries.

    A header-only widener passes a "not empty" check but widens nothing, so it hides a
    missing access entry instead of documenting one.  Ship none instead.
    """
    declared = fabric_mod.get('accessWidener')
    shipped = {name for name in fabric_jar.namelist() if name.endswith('.accesswidener')}
    fail(not any(name.endswith('.accesswidener') for name in forge_jar.namelist()),
         f'{label}: an access widener leaked into the Forge jar')
    fail(shipped <= ({declared} if declared else set()),
         f'{label}: undeclared access widener in the jar: {sorted(shipped - {declared})}')
    if not declared:
        return 0
    fail(declared in shipped, f'{label}: fabric.mod.json names {declared}, which is not in the jar')
    if declared not in shipped:
        return 0
    lines = [line.split('#', 1)[0].strip()
             for line in fabric_jar.read(declared).decode().splitlines()]
    lines = [line for line in lines if line]
    header = lines[0].split() if lines else []
    fail(len(header) == 3 and header[0] == 'accessWidener' and header[1] in ('v1', 'v2'),
         f'{label}: {declared} has no accessWidener header')
    entries = lines[1:]
    fail(entries, f'{label}: {declared} widens nothing; delete it and its references instead')
    return len(entries)


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


def included_libraries(build_dir: Path) -> tuple[str, ...]:
    """The ``META-INF/jars`` names Loom gives the ``include(...)`` dependencies of a build.

    A Maven dependency keeps its artifact file name (``energy-2.3.0.jar``).  An included
    build's jar is named after its ``base.archivesName`` and ``port.properties`` version.
    Resolved rather than restated so a version bump cannot skip the check.
    """
    text = (build_dir / 'build.gradle').read_text()
    versions = gradle_versions(build_dir)
    libraries = []
    for coordinate in re.findall(r"include\(\s*\w+\(\s*['\"]([^'\"]+)['\"]", text):
        group, artifact, _ = coordinate.split(':', 2)
        key = f'{group}:{artifact}'
        if key in INCLUDED_BUILDS:
            project = MODS / INCLUDED_BUILDS[key]
            archive = re.search(r"base\.archivesName\s*=\s*['\"]([^'\"]+)['\"]",
                                (project / 'build.gradle').read_text()).group(1)
            libraries.append(f'{archive}-{versions[key]}.jar')
        else:
            libraries.append(f'{artifact}-{versions[key]}.jar')
    return tuple(f'META-INF/jars/{name}' for name in libraries)


# --- GameTests ----------------------------------------------------------------------


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


def resource_files(root: Path) -> set[str]:
    return {path.relative_to(root).as_posix() for path in root.rglob('*') if path.is_file()} \
        if root.is_dir() else set()


def check_gametest_fixtures(jars: tuple[zipfile.ZipFile, ...], src: Path, *, fail=require) -> int:
    """No file from a GameTest resource root may ship, by path; return how many were checked.

    Generalises BuildCraft's arena check: its fixture is ``data/buildcraft/structures/arena.nbt``,
    which no name-based rule (``/gametest/``, ``.snbt``) recognises.  GameTest resource
    roots are ``src/gametest*/resources``; paths that a production root also provides
    (each GameTest mod's own ``fabric.mod.json``, for example) are not fixtures.
    """
    gametest, production = set(), set()
    for root in src.glob('**/resources'):
        parts = root.relative_to(src).parts
        (gametest if any(part.startswith('gametest') for part in parts) else production) \
            .update(resource_files(root))
    fixtures = gametest - production
    for jar in jars:
        for name in sorted(fixtures & set(jar.namelist())):
            fail(False, f'GameTest fixture shipped: {name}')
    return len(fixtures)


_JAVA_TOKEN = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
_ANNOTATION_ARGS = r'(?:\s*\((?:[^()]|\([^()]*\))*\))?'
_GAMETEST_METHOD = re.compile(
    r'@GameTest\b' + _ANNOTATION_ARGS + r'(?:\s*@\w+(?:\.\w+)*' + _ANNOTATION_ARGS +
    r'|\s*(?:public|protected|private|static|final|synchronized))*\s+void\s+(\w+)\s*\(')
_GENERATOR = re.compile(r'@GameTestGenerator\b')


def java_code(path: Path) -> str:
    """A Java source with comments removed and string literals emptied."""
    def replace(match):
        token = match.group(0)
        return ' ' if token.startswith('/') else token[0] * 2
    return _JAVA_TOKEN.sub(replace, path.read_text(encoding='utf-8'))


def gametest_methods(path: Path) -> set[str]:
    """Names of the ``@GameTest`` methods a Java source declares.

    Forge's are static; Fabric's ``FabricGameTest`` classes may declare instance methods.
    """
    return set(_GAMETEST_METHOD.findall(java_code(path)))


class GameTestSources:
    """The Java sources one loader's GameTest source set compiles, indexed by class."""

    def __init__(self, *roots: Path):
        self.files = {}
        for root in roots:
            for path in sorted(root.rglob('*.java')) if root.is_dir() else ():
                self.files[path.relative_to(root).with_suffix('').as_posix().replace('/', '.')] = path

    def resolve(self, simple: str, near: str) -> str | None:
        """The class a simple name refers to from ``near``: same package first, else unique."""
        package = near.rpartition('.')[0]
        if f'{package}.{simple}' in self.files:
            return f'{package}.{simple}'
        matches = [name for name in self.files if name.rpartition('.')[2] == simple]
        return matches[0] if len(matches) == 1 else None

    def tests(self, entry_classes: Iterable[str]) -> dict[str, str]:
        """``{test method: declaring class}`` for the classes a loader registers.

        A registered class contributes its static ``@GameTest`` methods.  A
        ``@GameTestGenerator`` in it contributes, in this order of evidence: the tests
        of every GameTest class it names as ``X.class``; the generators of classes it
        calls (``Shared.sharedTests()``); or, when it scans its package at runtime
        (``Class.forName``/``getResource``), every GameTest class in that package.  A
        generator none of these explain raises, so a new pattern is taught here instead
        of silently counting no tests.
        """
        found: dict[str, str] = {}
        seen: set[str] = set()

        def visit(name: str) -> None:
            if name in seen:
                return
            seen.add(name)
            path = self.files.get(name)
            require(path is not None, f'FAIL: GameTest class {name} has no source')
            for method in gametest_methods(path):
                found.setdefault(method, name)
            code = java_code(path)
            if not _GENERATOR.search(code):
                return
            simple = name.rpartition('.')[2]
            targets = {self.resolve(ref, name) for ref in re.findall(r'\b([A-Z]\w*)\.class\b', code)
                       if ref != simple}
            targets = {target for target in targets
                       if target and gametest_methods(self.files[target])}
            callees = {self.resolve(ref, name) for ref in re.findall(r'\b([A-Z]\w*)\.\w+\s*\(', code)
                       if ref != simple}
            callees = {callee for callee in callees
                       if callee and _GENERATOR.search(java_code(self.files[callee]))}
            if not targets and not callees and re.search(r'Class\.forName|getResource', code):
                package = name.rpartition('.')[0]
                targets = {other for other in self.files
                           if other.rpartition('.')[0] == package and gametest_methods(self.files[other])}
            require(targets or callees, f'FAIL: cannot tell which tests the generator in {name} registers')
            for target in sorted(targets | callees):
                visit(target)

        for entry in entry_classes:
            visit(entry)
        return found


def forge_gametest_holders(sources: GameTestSources) -> list[str]:
    """Classes Forge registers: those annotated ``@GameTestHolder``."""
    return [name for name, path in sources.files.items()
            if re.search(r'@GameTestHolder\b', java_code(path))]


def fabric_gametest_entrypoints(descriptor: Path) -> list[str]:
    """Classes Fabric registers: the ``fabric-gametest`` entrypoints of the GameTest mod."""
    entries = json.loads(descriptor.read_text()).get('entrypoints', {}).get('fabric-gametest', [])
    return [entry['value'] if isinstance(entry, dict) else entry for entry in entries]


def loader_gametests(mod: Path) -> tuple[dict[str, str], dict[str, str]]:
    """``(forge_tests, fabric_tests)`` registered by a port's standard GameTest source sets.

    Forge compiles ``src/gametestCommon`` + ``src/gametestForge`` and registers the
    ``@GameTestHolder`` classes; Fabric compiles ``src/gametestCommon`` +
    ``src/gametestFabric`` and registers the ``fabric-gametest`` entrypoints of
    ``src/gametestFabric/resources/fabric.mod.json``.  Tests are compared by method
    name, because each loader forwards shared bodies from its own class
    (``ExtremeReactorsForgeGameTests`` / ``ExtremeReactorsFabricGameTests``).
    """
    common = mod / 'src/gametestCommon/java'
    forge = GameTestSources(common, mod / 'src/gametestForge/java')
    fabric = GameTestSources(common, mod / 'src/gametestFabric/java')
    return (forge.tests(forge_gametest_holders(forge)),
            fabric.tests(fabric_gametest_entrypoints(mod / 'src/gametestFabric/resources/fabric.mod.json')))


ALLOWLIST_NAME = 'gametest-loader-only.txt'
_ALLOWLIST_LINE = re.compile(r'(forge|fabric)\s+(\w+)\s+#\s*(\S.*)')


def read_gametest_allowlist(path: Path, *, fail=require) -> dict[str, str]:
    """``{test: loader}`` from a ``gametest-loader-only.txt``.

    Each non-comment line is ``<forge|fabric> <test method>  # reason``; the reason is
    required, and a test may appear once.
    """
    allowed: dict[str, str] = {}
    if not path.is_file():
        return allowed
    for number, raw in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        match = _ALLOWLIST_LINE.fullmatch(line)
        fail(match, f'{path.name}:{number}: expected "<forge|fabric> <test>  # reason", got {raw!r}')
        if not match:
            continue
        loader, test, _reason = match.groups()
        fail(test not in allowed, f'{path.name}:{number}: {test} is listed twice')
        allowed[test] = loader
    return allowed


def check_gametest_symmetry(forge_tests: Iterable[str], fabric_tests: Iterable[str], allowlist: Path, *,
                            fail=require) -> tuple[int, int]:
    """Both loaders must run the same GameTests, except the allowlisted ones.

    A test only one loader runs must be listed for that loader; a listed test that now
    runs on both loaders (or on neither, or on the other one) must be removed, so the
    list only shrinks as tests move to shared code.  Returns ``(shared, allowlisted)``.
    """
    forge, fabric = set(forge_tests), set(fabric_tests)
    allowed = read_gametest_allowlist(allowlist, fail=fail)
    for loader, only in (('forge', forge - fabric), ('fabric', fabric - forge)):
        for test in sorted(only):
            fail(allowed.get(test) == loader,
                 f'GameTest {test} runs only on {loader.capitalize()}; share it or list it in '
                 f'{allowlist.name} with a reason')
    for test, loader in sorted(allowed.items()):
        actual = forge - fabric if loader == 'forge' else fabric - forge
        fail(test in actual, f'{allowlist.name} lists {test} as {loader}-only, but it is not; remove the line')
    return len(forge & fabric), len(allowed)


def check_loader_gametests(mod: Path, *, fail=require) -> tuple[int, int]:
    """``check_gametest_symmetry`` over ``loader_gametests(mod)`` and its allowlist."""
    forge, fabric = loader_gametests(mod)
    fail(forge and fabric, f'no GameTests discovered for {mod.name}: Forge {len(forge)}, Fabric {len(fabric)}')
    return check_gametest_symmetry(forge, fabric, mod / ALLOWLIST_NAME, fail=fail)


def check_artifact_pair(forge_jar: zipfile.ZipFile, fabric_jar: zipfile.ZipFile, fabric_mod: dict, *,
                        build_dir: Path, src: Path, nested: tuple[str, ...], label: str,
                        extra_depends: dict[str, str] | None = None, fail=require) -> dict[str, int]:
    """Run every shared jar check on one Forge/Fabric pair; return counts for the PASS line.

    Mixin configs and refmaps (each jar on its own), Fabric entrypoints, ``depends``
    floors against ``build_dir``, the access widener, nested libraries and GameTest
    resource fixtures.  Descriptors, loader isolation and GameTest classes stay
    explicit in the gates because their options differ per port.
    """
    return {
        'mixin configs': check_mixin_refmaps(forge_jar, label=f'{label} Forge', markers=FORGE_REFMAP_MARKERS,
                                             fail=fail)
        + check_mixin_refmaps(fabric_jar, label=f'{label} Fabric', fabric_mod=fabric_mod,
                              markers=FABRIC_REFMAP_MARKERS, fail=fail),
        'entrypoints': check_entrypoints(fabric_jar, fabric_mod, label=f'{label} Fabric', fail=fail),
        'dependency floors': check_depends_floor(fabric_mod, build_dir, label=f'{label} Fabric',
                                                 extra=extra_depends, fail=fail),
        'access entries': check_access_widener(forge_jar, fabric_jar, fabric_mod, label=f'{label} Fabric',
                                               fail=fail),
        'nested libraries': check_nested_libraries(forge_jar, fabric_jar, fabric_mod, nested, fail=fail),
        'fixture paths': check_gametest_fixtures((forge_jar, fabric_jar), src, fail=fail),
    }


def summary(counts: dict[str, int]) -> str:
    return ', '.join(f'{value} {key}' for key, value in counts.items())


def print_hashes(*paths: Path) -> None:
    """Print the SHA-256 of each artifact, matching the existing gate output."""
    for path in paths:
        print(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
