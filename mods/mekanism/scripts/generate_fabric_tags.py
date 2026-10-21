#!/usr/bin/env python3
"""Generates the Fabric tag bridges in src/fabric/resources/data.

Forge defines forge: tags for vanilla content in its own data and Mekanism's recipes rely on them. Fabric has no such
tags, and Fabric mods tag their content with c: tags instead. For every forge: tag Forge defines, and every forge: tag
Mekanism's shared data defines that has a c: equivalent, this writes a Fabric forge: tag holding Mekanism's shared
values, Forge's values and the c: tag as an optional entry. Fabric resources replace shared resources at the same path,
so the shared values are repeated. It also writes c: tags holding Mekanism's own content so Fabric mods see it.

c: tags never reference forge: tags, so the two namespaces cannot form a cycle.

  generate_fabric_tags.py                         rewrite the bridges from scripts/forge-tags.json
  generate_fabric_tags.py --check                 fail if the checked-in bridges are out of date
  generate_fabric_tags.py --forge-jar FORGE.jar   refresh scripts/forge-tags.json from a Forge universal JAR first
"""
import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
SHARED_TAGS = MOD / 'src/main/resources/data/forge/tags'
FABRIC_DATA = MOD / 'src/fabric/resources/data'
OUTPUTS = (FABRIC_DATA / 'forge/tags', FABRIC_DATA / 'c/tags')
FORGE_TAGS = MOD / 'scripts/forge-tags.json'

# Kinds whose forge: tags have c: counterparts in Fabric's conventions.
CONVENTION_KINDS = ('items', 'blocks', 'fluids')
# Fabric convention names that do not follow the <material>_<form>s pattern.
SPECIAL_NAMES = {
    'gems/diamond': 'diamonds',
    'gems/emerald': 'emeralds',
    'gems/lapis': 'lapis',
    'gems/quartz': 'quartz',
    'gems/amethyst': 'amethysts',
    'rods/wooden': 'wooden_rods',
    'glass': 'glass_blocks',
    'glass_panes': 'glass_panes',
    'chests/wooden': 'wooden_chests',
    'barrels/wooden': 'wooden_barrels',
    'buckets/empty': 'empty_buckets',
    'shears': 'shears',
    'ores': 'ores',
}
FORMS = ('ingots', 'nuggets', 'dusts', 'ores', 'gems', 'plates', 'gears', 'rods', 'dyes', 'pellets')
TOOLS = ('swords', 'axes', 'pickaxes', 'shovels', 'hoes', 'bows', 'crossbows', 'tridents', 'shields', 'fishing_rods')


def convention_name(kind, path):
    """The c: tag Fabric mods use for forge:<path>, or None when there is no established equivalent."""
    if kind == 'fluids':
        return path if '/' not in path and path != 'gaseous' else None
    if path in SPECIAL_NAMES:
        return SPECIAL_NAMES[path]
    form, _, material = path.partition('/')
    if not material or '/' in material:
        return None
    if form in FORMS:
        return f'{material}_{form}'
    if form == 'storage_blocks':
        return f'{material}_blocks'
    if form == 'raw_materials':
        return f'raw_{material}_ores'
    if form == 'buckets':
        return f'{material}_buckets'
    if form == 'tools' and material in TOOLS:
        return material
    return None


def read_forge_jar(jar):
    tags = {}
    with zipfile.ZipFile(jar) as archive:
        for name in sorted(archive.namelist()):
            match = re.fullmatch(r'data/forge/tags/(.+)\.json', name)
            if match:
                tags[match.group(1)] = json.loads(archive.read(name))['values']
    return tags


def read_shared():
    return {path.relative_to(SHARED_TAGS).with_suffix('').as_posix(): json.loads(path.read_text())['values']
            for path in sorted(SHARED_TAGS.rglob('*.json'))}


def entry_id(entry):
    return entry if isinstance(entry, str) else entry['id']


def optional(tag):
    return {'id': tag, 'required': False}


def merge(*lists):
    merged, seen = [], set()
    for values in lists:
        for value in values:
            if entry_id(value) not in seen:
                seen.add(entry_id(value))
                merged.append(value)
    return merged


def to_convention(kind, entry):
    """A shared value as a c: tag value: forge: references become their c: equivalents or are dropped."""
    ident = entry_id(entry)
    if not ident.startswith('#forge:'):
        return entry
    name = convention_name(kind, ident.removeprefix('#forge:'))
    return optional(f'#c:{name}') if name else None


def bridges(forge, shared):
    files = {}
    for key in sorted(set(forge) | set(shared)):
        kind, _, path = key.partition('/')
        name = convention_name(kind, path) if kind in CONVENTION_KINDS else None
        if key not in forge and name is None:
            continue
        convention = [optional(f'#c:{name}')] if name else []
        files[f'forge/tags/{key}.json'] = merge(shared.get(key, []), forge.get(key, []), convention)
        if name and key in shared:
            own = [value for value in (to_convention(kind, entry) for entry in shared[key]) if value is not None]
            if own:
                target = f'c/tags/{kind}/{name}.json'
                files[target] = merge(files.get(target, []), own)
    return {path: json.dumps({'replace': False, 'values': values}, indent=2) + '\n' for path, values in files.items()}


def existing():
    return {path.relative_to(FABRIC_DATA).as_posix(): path.read_text()
            for output in OUTPUTS if output.exists() for path in sorted(output.rglob('*.json'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--forge-jar', type=Path)
    args = parser.parse_args()
    if args.forge_jar:
        FORGE_TAGS.write_text(json.dumps(read_forge_jar(args.forge_jar), indent=2) + '\n')
    wanted = bridges(json.loads(FORGE_TAGS.read_text()), read_shared())
    current = existing()
    if args.check:
        stale = sorted(path for path in set(wanted) | set(current) if wanted.get(path) != current.get(path))
        for path in stale:
            print(f'out of date: src/fabric/resources/data/{path}', file=sys.stderr)
        if stale:
            sys.exit(f'{len(stale)} Fabric tag bridges are out of date; run {Path(__file__).name}')
        print(f'{len(wanted)} Fabric tag bridges are up to date.')
        return
    for path in set(current) - set(wanted):
        (FABRIC_DATA / path).unlink()
    for path, text in wanted.items():
        target = FABRIC_DATA / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    print(f'Wrote {len(wanted)} Fabric tag bridges.')


if __name__ == '__main__':
    main()
