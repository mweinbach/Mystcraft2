#!/usr/bin/env python3
"""Generates the Fabric tag bridges in ProjectE's Fabric resources.

Adapted from the Mekanism port's generator (mods/mekanism/scripts/generate_fabric_tags.py), MIT.

Forge defines forge: tags for vanilla content in its own data, and ProjectE's recipes, EMC defaults
(pe_custom_conversions) and Java code rely on them. Fabric has no such tags, and Fabric mods tag their content with c:
tags instead. For every forge: tag Forge defines, and every forge: tag ProjectE's shared data defines that has a c:
equivalent, this writes a Fabric forge: tag holding ProjectE's shared values, Forge's values and the c: tag as an
optional entry. Fabric resources replace shared resources at the same path, so the shared values are repeated. It
also writes c: tags holding ProjectE's own content so Fabric mods see it. c: tags never reference forge: tags, so the
two namespaces cannot form a cycle.

  generate_fabric_tags.py          rewrite the bridges from scripts/forge-tags.json (Forge 43.5.0's tag data)
  generate_fabric_tags.py --check  fail if the checked-in bridges are out of date
"""
import argparse
import json
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
SHARED_TAGS = MOD / 'src/main/resources/data/forge/tags'
FABRIC_DATA = MOD / 'src/fabric/resources/data'
FORGE_TAGS = MOD / 'scripts/forge-tags.json'

# Kinds whose forge: tags have c: counterparts in Fabric's conventions.
CONVENTION_KINDS = ('items', 'blocks', 'fluids')
# Fabric convention names that do not follow the <material>_<form>s pattern. Fabric API 0.77's conventional tags
# define only some of them (diamonds, emeralds, lapis, quartz, ores, shears, empty_buckets, glass_blocks, glass_panes,
# and bookshelves as a block tag); the rest are the plural names Fabric mods use for 1.19.2. Every c: reference is
# optional, so a name no installed mod fills is an empty tag.
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
    'wrenches': 'wrenches',
    'tools/wrench': 'wrenches',
    # Vanilla materials the recipes use.
    'cobblestone/normal': 'cobblestone',
    'cobblestone/deepslate': 'cobbled_deepslate',
    'slimeballs': 'slime_balls',
    'string': 'string',
    'leather': 'leather',
    'obsidian': 'obsidian',
    'gravel': 'gravel',
    'gunpowder': 'gunpowder',
    'nether_stars': 'nether_stars',
    'mushrooms': 'mushrooms',
    'bookshelves': 'bookshelves',
    'crops/wheat': 'wheat_crops',
    'fences/wooden': 'wooden_fences',
}
COLORS = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
          'blue', 'brown', 'green', 'red', 'black')
SPECIAL_NAMES.update({f'glass/{color}': f'{color}_glass_blocks' for color in COLORS})
SPECIAL_NAMES.update({f'glass_panes/{color}': f'{color}_glass_panes' for color in COLORS})
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


def read_shared(root):
    """A module's shared forge: tags, keyed by <kind>/<path>."""
    if not root.is_dir():
        return {}
    return {path.relative_to(root).with_suffix('').as_posix(): json.loads(path.read_text())['values']
            for path in sorted(root.rglob('*.json'))}


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


def bridges(forge, shared, core=True, reserved=()):
    """The bridge values for one module: {'forge/tags/...' or 'c/tags/...': values}.

    The core module bridges every tag Forge defines and every forge: tag of its own that has a c: name. Another module
    bridges only its forge: tags that Forge does not define, and those bridges hold just the optional c: reference: the
    module's own values stay in its shared file, and they go in the core jar (see module_bridges). Every module writes
    c: tags for its own content. ``reserved`` are c: names the module already ships from its shared resources.
    """
    files = {}
    for key in sorted((set(forge) if core else set()) | set(shared)):
        kind, _, path = key.partition('/')
        name = convention_name(kind, path) if kind in CONVENTION_KINDS else None
        if key not in forge and name is None:
            continue
        convention = [optional(f'#c:{name}')] if name else []
        if core:
            files[f'forge/tags/{key}.json'] = merge(shared.get(key, []), forge.get(key, []), convention)
        elif key not in forge:
            files[f'forge/tags/{key}.json'] = convention
        if name and key in shared and name not in reserved:
            own = [value for value in (to_convention(kind, entry) for entry in shared[key]) if value is not None]
            if own:
                target = f'c/tags/{kind}/{name}.json'
                files[target] = merge(files.get(target, []), own)
    return files


def wanted_files():
    files = bridges(json.loads(FORGE_TAGS.read_text()), read_shared(SHARED_TAGS))
    return {(FABRIC_DATA / path).relative_to(MOD).as_posix(): json.dumps({'replace': False, 'values': values}, indent=2) + '\n'
            for path, values in files.items()}


def existing():
    return {path.relative_to(MOD).as_posix(): path.read_text()
            for output in (FABRIC_DATA / 'forge/tags', FABRIC_DATA / 'c/tags') if output.exists()
            for path in sorted(output.rglob('*.json'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    wanted = wanted_files()
    current = existing()
    if args.check:
        stale = sorted(path for path in set(wanted) | set(current) if wanted.get(path) != current.get(path))
        for path in stale:
            print(f'out of date: {path}', file=sys.stderr)
        if stale:
            sys.exit(f'{len(stale)} Fabric tag bridges are out of date; run {Path(__file__).name}')
        print(f'{len(wanted)} Fabric tag bridges are up to date.')
        return
    for path in set(current) - set(wanted):
        (MOD / path).unlink()
    for path, text in wanted.items():
        target = MOD / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    print(f'Wrote {len(wanted)} Fabric tag bridges.')


if __name__ == '__main__':
    main()
