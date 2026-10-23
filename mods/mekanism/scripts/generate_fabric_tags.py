#!/usr/bin/env python3
"""Generates the Fabric tag bridges in each module's Fabric resources.

Forge defines forge: tags for vanilla content in its own data and Mekanism's recipes rely on them. Fabric has no such
tags, and Fabric mods tag their content with c: tags instead. For every forge: tag Forge defines, and every forge: tag
Mekanism's shared data defines that has a c: equivalent, this writes a Fabric forge: tag holding Mekanism's shared
values, Forge's values and the c: tag as an optional entry. Fabric resources replace shared resources at the same path,
so the shared values are repeated. It also writes c: tags holding Mekanism's own content so Fabric mods see it.

Every module is scanned: core (src/main), additions, generators and tools (src/<module>/resources). Forge's own tags
are bridged once, in the core jar, which every module depends on. A module's c: tags go in its own Fabric resources
(src/<module>/fabric/resources); its forge: tags that Forge does not define get a core bridge holding only the c:
reference, since its own values are already in the module's shared file.

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
# Module: (its shared forge: tags, the Fabric data folder its bridges go to).
MODULES = {
    'main': (MOD / 'src/main/resources/data/forge/tags', MOD / 'src/fabric/resources/data'),
    'additions': (MOD / 'src/additions/resources/data/forge/tags', MOD / 'src/additions/fabric/resources/data'),
    'generators': (MOD / 'src/generators/resources/data/forge/tags', MOD / 'src/generators/fabric/resources/data'),
    'tools': (MOD / 'src/tools/resources/data/forge/tags', MOD / 'src/tools/fabric/resources/data'),
}
TOOLS_DATA = MOD / 'src/tools/resources/data'
FORGE_TAGS = MOD / 'scripts/forge-tags.json'
# Tools forge: tags flattened into c: tags that ship in the tools jar. Paxels stay out of the tool tags.
TOOLS_C_TAGS = {
    'items/tools/swords': 'swords',
    'items/tools/axes': 'axes',
    'items/tools/pickaxes': 'pickaxes',
    'items/tools/shovels': 'shovels',
    'items/tools/hoes': 'hoes',
    'items/tools/shields': 'shields',
    'items/tools/paxels': 'paxels',
    'items/armors/helmets': 'helmets',
    'items/armors/chestplates': 'chestplates',
    'items/armors/leggings': 'leggings',
    'items/armors/boots': 'boots',
}

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
    # Mekanism's configurators tag holds both wrench tags, so Fabric wrenches work on Mekanism blocks.
    'wrenches': 'wrenches',
    'tools/wrench': 'wrenches',
    # Vanilla materials Mekanism's recipes use.
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


def read_forge_jar(jar):
    tags = {}
    with zipfile.ZipFile(jar) as archive:
        for name in sorted(archive.namelist()):
            match = re.fullmatch(r'data/forge/tags/(.+)\.json', name)
            if match:
                tags[match.group(1)] = json.loads(archive.read(name))['values']
    return tags


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


def module_bridges(forge):
    """Every module's bridge files, keyed by path relative to the mod folder.

    c: tags go in the module's own Fabric resources. A module's forge: bridges go in core's: Loom fails on a resource
    path a module's Fabric and shared folders both have, and core reaches the c: tag for every module that depends on it.
    """
    files = {}
    core_data = MODULES['main'][1]
    for module, (shared_root, fabric_data) in MODULES.items():
        reserved = set(TOOLS_C_TAGS.values()) if module == 'tools' else ()
        for path, values in bridges(forge, read_shared(shared_root), module == 'main', reserved).items():
            folder = fabric_data if path.startswith('c/') else core_data
            target = (folder / path).relative_to(MOD).as_posix()
            files[target] = merge(files.get(target, []), values)
    return {path: json.dumps({'replace': False, 'values': values}, indent=2) + '\n' for path, values in files.items()}


def existing():
    """The checked-in bridge files of every module, keyed by path relative to the mod folder."""
    return {path.relative_to(MOD).as_posix(): path.read_text()
            for _, fabric_data in MODULES.values()
            for output in (fabric_data / 'forge/tags', fabric_data / 'c/tags') if output.exists()
            for path in sorted(output.rglob('*.json'))}


def flatten_tag(tags, key, seen):
    """Item ids behind a tools forge: tag, following nested #forge: references in that module's data."""
    if key in seen:
        return []
    seen = seen | {key}
    values = []
    for entry in tags.get(key, []):
        ident = entry_id(entry)
        if ident.startswith('#forge:'):
            values.extend(flatten_tag(tags, 'items/' + ident.removeprefix('#forge:'), seen))
        elif not ident.startswith('#'):
            values.append(ident)
    return values


def tools_c_tags():
    """c: tool and armor tags for the tools module, stored in the shared tools resources so both jars carry them."""
    forge_root = TOOLS_DATA / 'forge/tags'
    if not forge_root.is_dir():
        return {}
    tags = {path.relative_to(forge_root).with_suffix('').as_posix(): json.loads(path.read_text()).get('values', [])
            for path in sorted(forge_root.rglob('*.json'))}
    files = {}
    for source, name in TOOLS_C_TAGS.items():
        values, seen = [], set()
        for item in flatten_tag(tags, source, set()):
            if item not in seen:
                seen.add(item)
                values.append(item)
        files[(TOOLS_DATA / f'c/tags/items/{name}.json').relative_to(MOD).as_posix()] = \
            json.dumps({'replace': False, 'values': values}, indent=2) + '\n'
    return files


def existing_tools_c():
    folder = TOOLS_DATA / 'c'
    if not folder.exists():
        return {}
    return {path.relative_to(MOD).as_posix(): path.read_text() for path in sorted(folder.rglob('*.json'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--forge-jar', type=Path)
    args = parser.parse_args()
    if args.forge_jar:
        FORGE_TAGS.write_text(json.dumps(read_forge_jar(args.forge_jar), indent=2) + '\n')
    wanted_bridges = module_bridges(json.loads(FORGE_TAGS.read_text()))
    wanted_tools = tools_c_tags()
    wanted = {**wanted_bridges, **wanted_tools}
    current = {**existing(), **existing_tools_c()}
    summary = f'{len(wanted_bridges)} Fabric tag bridges across {len(MODULES)} modules and {len(wanted_tools)} tools c: tags'
    if args.check:
        stale = sorted(path for path in set(wanted) | set(current) if wanted.get(path) != current.get(path))
        for path in stale:
            print(f'out of date: {path}', file=sys.stderr)
        if stale:
            sys.exit(f'{len(stale)} Fabric tag bridges are out of date; run {Path(__file__).name}')
        print(f'{summary} are up to date.')
        return
    for path in set(current) - set(wanted):
        (MOD / path).unlink()
    for path, text in wanted.items():
        target = MOD / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)
    print(f'Wrote {summary}.')


if __name__ == '__main__':
    main()
