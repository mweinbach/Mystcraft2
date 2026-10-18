#!/usr/bin/env python3
"""List, validate, and create independently packaged Forge ports."""
import argparse
from pathlib import Path
import re
import shutil
import sys
import tempfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
MOD_ID = re.compile(r"[a-z][a-z0-9_]{1,63}\Z")
REQUIRED = {"mod_id", "mod_name", "mod_version", "mod_group", "mod_license",
            "mod_authors", "mod_description", "upstream_url", "upstream_revision", "port_status"}
STATUSES = {"scaffold", "in-progress", "playable", "released"}


def read_metadata(path):
    metadata = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not separator or key in metadata:
            raise ValueError(f"{path}:{number}: malformed or duplicate property")
        if "\\" in value:
            raise ValueError(f"{path}:{number}: use literal values without backslash escapes")
        metadata[key] = value
    missing = sorted(key for key in REQUIRED if not metadata.get(key))
    if missing:
        raise ValueError(f"{path}: missing {', '.join(missing)}")
    if not MOD_ID.fullmatch(metadata["mod_id"]) or metadata["mod_id"] != path.parent.name:
        raise ValueError(f"{path}: mod_id must be a valid ID matching its directory")
    if metadata["port_status"] not in STATUSES:
        raise ValueError(f"{path}: invalid port_status")
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+", metadata["mod_group"]):
        raise ValueError(f"{path}: mod_group must be a Java package")
    address = urlsplit(metadata["upstream_url"])
    if address.scheme != "https" or not address.hostname or address.username or address.password:
        raise ValueError(f"{path}: upstream_url must be an HTTPS URL without credentials")
    return metadata


def ports(root):
    found = []
    for directory in sorted((root / "mods").iterdir()):
        if not directory.is_dir() or directory.name.startswith("."):
            continue
        path = directory / "port.properties"
        if not path.is_file():
            raise ValueError(f"{directory}: missing port.properties")
        metadata = read_metadata(path)
        for resource in ("build.gradle", "README.md", "src/main/resources/META-INF/mods.toml",
                         "src/main/resources/pack.mcmeta"):
            if not (directory / resource).is_file():
                raise ValueError(f"{directory}: missing {resource}")
        if not list((directory / "src/main/java").rglob("*.java")):
            raise ValueError(f"{directory}: missing Java source")
        found.append(metadata)
    if not found:
        raise ValueError("No ports found under mods/.")
    return found


def create_port(root, mod_id, name, upstream, license_name):
    if not MOD_ID.fullmatch(mod_id):
        raise ValueError("Mod ID must contain 2-64 lowercase letters, digits, or underscores and start with a letter.")
    values = {"MOD_ID": mod_id, "MOD_NAME": name, "UPSTREAM_URL": upstream, "MOD_LICENSE": license_name}
    for value in values.values():
        if not value.strip() or any(char in value for char in ('\n', '\r', '\\', '"', "'", '$', '@@')) or not value.isascii():
            raise ValueError("Use nonempty ASCII metadata without quotes, backslashes, newlines, $, or @@.")
    destination = root / "mods" / mod_id
    if destination.exists():
        raise ValueError(f"Port already exists: {mod_id}")
    with tempfile.TemporaryDirectory(dir=root / "mods", prefix=".new-port-") as staging:
        port = Path(staging) / mod_id
        shutil.copytree(root / "templates/forge-mod", port)
        for path in port.rglob("*"):
            if path.is_file():
                content = path.read_text(encoding="utf-8")
                for key, value in values.items():
                    content = content.replace(f"@@{key}@@", value)
                path.write_text(content, encoding="utf-8")
        java = port / "src/main/java/ports" / mod_id / "PortMod.java"
        java.parent.mkdir(parents=True)
        (port / "PortMod.java").rename(java)
        read_metadata(port / "port.properties")
        port.rename(destination)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("list")
    commands.add_parser("check")
    new = commands.add_parser("new")
    new.add_argument("mod_id")
    new.add_argument("--name", required=True)
    new.add_argument("--upstream", required=True)
    new.add_argument("--license", required=True, dest="license_name")
    args = parser.parse_args()
    try:
        if args.command == "new":
            result = create_port(ROOT, args.mod_id, args.name, args.upstream, args.license_name)
            print(f"Created {result.relative_to(ROOT)}. Add upstream license text before importing code.")
        else:
            entries = ports(ROOT)
            if args.command == "list":
                for entry in entries:
                    print(f'{entry["mod_id"]:24} {entry["port_status"]:14} {entry["mod_name"]}')
            else:
                print(f"Validated {len(entries)} port(s).")
    except (ValueError, OSError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
