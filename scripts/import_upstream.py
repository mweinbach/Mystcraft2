#!/usr/bin/env python3
"""Copy one Mekanism module from an upstream checkout into this port.

Upstream is mekanism/Mekanism tag v1.19.2-10.3.9.13. The core module already lives in
``mods/mekanism/src/main``. Generators, Tools and Additions are separate mod ids built from
the same port, so this script copies a module into ``mods/mekanism/src/<module>``:

- ``src/<module>/java`` from upstream ``src/<module>/java``
- ``src/<module>/resources`` from upstream resources plus the checked-in datagen output
  ``src/datagen/generated/mekanism<module>`` (the datagen ``.cache`` directory is skipped)
- ``src/<module>/forge/resources/META-INF/mods.toml`` from upstream's ``mods.toml``, with
  the port's version token and Forge loader range written in
- ``pack.mcmeta`` with the 1.19.2 pack formats written in, because the port builds do not
  expand upstream's template variables

Files that import the JEI API (``mezz.jei``) are not copied. Recipe-viewer types that do
not import JEI are kept, matching the core module. Port-owned files under
``src/<module>/fabric`` are left alone.

    python3 scripts/import_upstream.py --upstream /path/to/Mekanism --module generators
"""
import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = ROOT / "mods" / "mekanism"
MODULES = ("generators", "tools", "additions")
PACK_FORMATS = {"pack_format": "10", "resource_pack_format": "9", "data_pack_format": "10"}
JEI_IMPORT = "mezz.jei."


def imports_excluded_api(text):
    """True when a Java file imports an integration this port does not ship."""
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("import ") and JEI_IMPORT in stripped:
            return True
    return False


def normalize_pack_mcmeta(text):
    for name, value in PACK_FORMATS.items():
        text = text.replace("${" + name + "}", value).replace(f"${name}", value)
    if "$" in text:
        raise ValueError(f"pack.mcmeta still has a template token: {text}")
    return text


def normalize_mods_toml(text):
    """Upstream expands ``version`` and ``loader_version`` in its build. This port expands port.properties."""
    text = text.replace("${loader_version}", "[43,44)")
    text = text.replace("${version}", "${mod_version}")
    return text


def _files(root):
    if not root.is_dir():
        raise FileNotFoundError(root)
    found = []
    for path in sorted(root.rglob("*")):
        if not path.is_file() or ".cache" in path.parts:
            continue
        found.append(path)
    return found


def import_module(upstream, module, destination=None):
    if module not in MODULES:
        raise ValueError(f"module must be one of {', '.join(MODULES)}")
    upstream = Path(upstream)
    java_src = upstream / "src" / module / "java"
    resources_src = upstream / "src" / module / "resources"
    datagen_src = upstream / "src" / "datagen" / "generated" / f"mekanism{module}"
    if not java_src.is_dir() or not resources_src.is_dir():
        raise FileNotFoundError(f"{upstream} has no src/{module} sources")

    destination = Path(destination) if destination is not None else MOD / "src" / module
    java_dest = destination / "java"
    resources_dest = destination / "resources"
    skipped = []
    copied = 0
    if java_dest.exists():
        shutil.rmtree(java_dest)
    for path in _files(java_src):
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(java_src)
        if imports_excluded_api(text):
            skipped.append(relative.as_posix())
            continue
        target = java_dest / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        copied += 1

    preserved = []
    if resources_dest.is_dir():
        for folder in ("pe_custom_conversions", "default_emcs"):
            for path in resources_dest.rglob(folder):
                if path.is_dir():
                    for file in path.rglob("*"):
                        if file.is_file():
                            preserved.append((file.relative_to(resources_dest), file.read_bytes()))
        shutil.rmtree(resources_dest)
    for path in _files(resources_src):
        relative = path.relative_to(resources_src)
        if relative.as_posix() == "META-INF/mods.toml":
            toml = normalize_mods_toml(path.read_text(encoding="utf-8"))
            target = destination / "forge" / "resources" / "META-INF" / "mods.toml"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(toml, encoding="utf-8")
            continue
        if relative.as_posix() == "pack.mcmeta":
            text = normalize_pack_mcmeta(path.read_text(encoding="utf-8"))
            target = resources_dest / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8")
            continue
        target = resources_dest / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)

    datagen = 0
    if datagen_src.is_dir():
        for path in _files(datagen_src):
            relative = path.relative_to(datagen_src)
            target = resources_dest / relative
            if target.exists():
                raise FileExistsError(f"datagen overwrites {relative}")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            datagen += 1
    for relative, data in preserved:
        target = resources_dest / relative
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    return {"java": copied, "skipped": skipped, "datagen": datagen, "destination": destination}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--upstream", required=True, type=Path, help="checkout of mekanism/Mekanism at v1.19.2-10.3.9.13")
    parser.add_argument("--module", required=True, choices=MODULES)
    args = parser.parse_args(argv)
    try:
        result = import_module(args.upstream, args.module)
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1
    print(f"Imported {args.module}: {result['java']} Java files, {result['datagen']} datagen files "
          f"-> {result['destination'].relative_to(ROOT)}")
    for path in result["skipped"]:
        print(f"skipped {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
