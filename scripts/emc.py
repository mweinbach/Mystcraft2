#!/usr/bin/env python3
"""Generate and check EMC data for ProjectE 1.1.0 (Forge) and Item Alchemy 1.1.3 (Fabric).

Each port keeps a hand-written ``emc/policy.json`` and the live item registry dumped by its
GameTest (``emc/registry.txt``). This tool resolves every item the way ProjectE's graph mapper
would (recipes, tags, fixed values, minimum-cost propagation, plus ProjectE's ore blacklists,
metals.json and TagMapper) against vanilla baselines from both mods, then writes ``emc/values.json``,
``emc/README.md`` and the shipped data:
``data/<ns>/pe_custom_conversions/<ns>.json`` and ``data/<ns>/default_emcs/<ns>.json``.
A policy may also set ``projecte_mappers`` so Forge ProjectE's recipe mappers are checked against a
generated guard file. See docs/emc.md.
"""
import argparse
import fnmatch
import itertools
import json
import re
import sys
import zipfile
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASELINES = ROOT / "scripts" / "emc"
SYSTEMS = ("projecte", "itemalchemy")
LOADERS = ("forge", "fabric")
NO_EMC = "FAKE|no_emc"
CRAFTING = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"}
COOKING = {"minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"}
# Vanilla Item#getCraftingRemainingItem; ProjectE subtracts these from recipe costs.
REMAINDERS = {
    "minecraft:water_bucket": "minecraft:bucket", "minecraft:lava_bucket": "minecraft:bucket",
    "minecraft:milk_bucket": "minecraft:bucket", "minecraft:powder_snow_bucket": "minecraft:bucket",
    "minecraft:honey_bottle": "minecraft:glass_bottle", "minecraft:dragon_breath": "minecraft:glass_bottle",
}
ITEM_ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+\Z")
NSS = re.compile(r"(#[a-z0-9_.-]+:[a-z0-9_./-]+|FAKE\|[A-Za-z0-9_.:-]+|[a-z0-9_.-]+:[a-z0-9_./-]+(\{.*\})?)\Z")
PLACEHOLDER = re.compile(r"\{([a-z]+)\}")
# ProjectE 1.1.0's OresBlacklistMapper and RawOresBlacklistMapper set every member of these Forge tags to 0 both
# before and after mapping. Such an item has no EMC, and no conversion that needs it resolves.
PROJECTE_BLACKLISTS = {"forge:ores": "OresBlacklistMapper", "forge:raw_materials": "RawOresBlacklistMapper"}
# Where a conversion's rid starts with this, it is a ProjectE metals.json tag conversion (setValueFromConversion).
METALS = "ProjectE metals.json"
# ProjectE's TagMapper runs last and adds `tag = 1 member` and `member = 1 tag` for every item tag any custom
# conversion names, so every member of such a tag is worth at most its cheapest member. ProjectE's own files name
# the tags in projecte-tags.json; a tag only a port's file names is written as an item when that would lower a member.
TAG_MAPPER = "ProjectE TagMapper"


class PolicyError(ValueError):
    pass


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dumps(data):
    return json.dumps(data, indent=2, sort_keys=False) + "\n"


def floor_value(value):
    """ProjectE's HiddenBigFractionArithmetic: keep fractions below one, floor anything larger."""
    return value if value < 1 else Fraction(int(value))


def _guard_value(values, item):
    """Static EMC of at least one for a plain item id, or None when a mapper must refuse it."""
    if not isinstance(item, str) or "|" in item or "{" in item or item.startswith("#"):
        return None
    value = values.get(item)
    if isinstance(value, bool) or not isinstance(value, (int, Fraction)) or value < 1:
        return None
    return int(value)


def mapper_permits(ingredients, output, count, values):
    """Whether a ProjectE mapper may add this conversion without undercutting the static table.

    Every ingredient and the output must be a plain item whose static EMC is at least one.
    Chemical and fluid stacks (a ``|`` in the id), tags, NBT variants and nonpositive amounts are
    refused. ``cost // count`` matches the Forge guard and must be at least the output's value.
    """
    if isinstance(count, bool) or not isinstance(count, int) or count < 1:
        return False
    output_value = _guard_value(values, output)
    if output_value is None or not isinstance(ingredients, dict) or not ingredients:
        return False
    cost = 0
    for item, amount in ingredients.items():
        if isinstance(amount, bool) or not isinstance(amount, int) or amount < 1:
            return False
        value = _guard_value(values, item)
        if value is None:
            return False
        cost += amount * value
    return cost // count >= output_value


def _item_spec(spec):
    if not isinstance(spec, dict) or "ingredient" not in spec:
        return None
    amount = spec.get("amount", 1)
    if isinstance(amount, bool) or not isinstance(amount, int) or amount < 1:
        return None
    return spec["ingredient"], amount


def _stack_output(output):
    if not isinstance(output, dict) or not isinstance(output.get("item"), str):
        return None
    count = output.get("count", 1)
    if isinstance(count, bool) or not isinstance(count, int) or count < 1:
        return None
    return output["item"], count


def item_only_machine_recipe(recipe):
    """``(output, count, [(ingredient, amount), ...])`` for a machine recipe whose inputs are items.

    Sawmill recipes that also return a byproduct are not included: the mapper subtracts that
    byproduct, and a negative amount is refused. Chemical and fluid recipes return None.
    """
    kind = recipe.get("type")
    if kind in ("mekanism:crushing", "mekanism:enriching"):
        parsed = _stack_output(recipe.get("output"))
        spec = _item_spec(recipe.get("input"))
        if parsed and spec:
            return (*parsed, [spec])
    elif kind == "mekanism:combining":
        parsed = _stack_output(recipe.get("output"))
        main = _item_spec(recipe.get("mainInput"))
        extra = _item_spec(recipe.get("extraInput"))
        if parsed and main and extra:
            return (*parsed, [main, extra])
    elif kind == "mekanism:sawing" and not recipe.get("secondaryOutput"):
        parsed = _stack_output(recipe.get("mainOutput"))
        spec = _item_spec(recipe.get("input"))
        if parsed and spec:
            return (*parsed, [spec])
    return None


# ----------------------------------------------------------------------------- tags


class Tags:
    """Item tags as the loader would merge them: vendored vanilla/loader tags plus mod tag files."""

    def __init__(self, raw):
        self.raw = {tag: list(entries) for tag, entries in raw.items()}
        self._cache = {}

    @classmethod
    def for_loader(cls, loader):
        raw = {}
        for name in ("minecraft", "forge" if loader == "forge" else "c"):
            raw.update(read_json(BASELINES / f"tags-{name}.json"))
        return cls(raw)

    def add_resources(self, roots):
        for root in roots:
            data = root / "data"
            if not data.is_dir():
                continue
            for namespace in sorted(p for p in data.iterdir() if p.is_dir()):
                folder = namespace / "tags" / "items"
                for file in sorted(folder.rglob("*.json")):
                    tag = f"{namespace.name}:{file.relative_to(folder).with_suffix('').as_posix()}"
                    content = read_json(file)
                    entries = [e if isinstance(e, str) else e["id"] for e in content.get("values", [])]
                    if content.get("replace"):
                        self.raw[tag] = entries
                    else:
                        self.raw.setdefault(tag, []).extend(entries)
        self._cache.clear()

    def members(self, tag, seen=None):
        if tag in self._cache:
            return self._cache[tag]
        seen = (seen or set()) | {tag}
        result = set()
        for entry in self.raw.get(tag, []):
            if entry.startswith("#"):
                if entry[1:] not in seen:
                    result |= self.members(entry[1:], seen)
            else:
                result.add(entry)
        self._cache[tag] = frozenset(result)
        return self._cache[tag]

    def tags_of(self, item):
        return sorted(tag for tag in self.raw if item in self.members(tag))


# ----------------------------------------------------------------------------- recipes


def ingredient_items(ingredient, tags):
    if isinstance(ingredient, list):
        result = set()
        for part in ingredient:
            items = ingredient_items(part, tags)
            if items is None:
                return None
            result |= items
        return frozenset(result)
    if "item" in ingredient:
        return frozenset([ingredient["item"]])
    if "tag" in ingredient:
        return tags.members(ingredient["tag"])
    return None


def result_of(recipe):
    result = recipe.get("result")
    if isinstance(result, str):
        return result, int(recipe.get("count", 1))
    if isinstance(result, dict) and "item" in result:
        return result["item"], int(result.get("count", 1))
    return None, 0


def recipe_ingredients(recipe):
    kind = recipe.get("type")
    if kind in ("minecraft:crafting_shaped", "mekanismtools:paxel"):
        counts = {}
        for row in recipe["pattern"]:
            for symbol in row:
                if symbol != " ":
                    counts[symbol] = counts.get(symbol, 0) + 1
        return [recipe["key"][symbol] for symbol, n in counts.items() for _ in range(n)]
    if kind == "minecraft:crafting_shapeless":
        return list(recipe["ingredients"])
    if kind in COOKING or kind == "minecraft:stonecutting":
        return [recipe["ingredient"]]
    if kind == "minecraft:smithing":
        return [recipe["base"], recipe["addition"]]
    return None


def condition_holds(condition, loader, tags, mods, custom=None):
    """Evaluate a Forge ``conditions`` or Fabric ``fabric:load_conditions`` entry for a standalone port.

    ``custom`` maps a port's own condition types to the outcome the policy fixes for them."""
    kind = condition.get("condition" if loader == "fabric" else "type")
    if custom and kind in custom:
        return custom[kind]
    if kind in ("fabric:not", "forge:not"):
        return not condition_holds(condition["value"], loader, tags, mods, custom)
    if kind in ("fabric:and", "forge:and"):
        return all(condition_holds(c, loader, tags, mods, custom) for c in condition["values"])
    if kind in ("fabric:or", "forge:or"):
        return any(condition_holds(c, loader, tags, mods, custom) for c in condition["values"])
    if kind == "fabric:tags_populated" and condition.get("registry", "minecraft:item") == "minecraft:item":
        return all(tags.members(tag) for tag in condition["values"])
    if kind == "forge:tag_empty":
        return not tags.members(condition["tag"])
    if kind == "fabric:all_mods_loaded":
        return all(mod in mods for mod in condition["values"])
    if kind == "fabric:any_mod_loaded":
        return any(mod in mods for mod in condition["values"])
    if kind == "forge:mod_loaded":
        return condition["modid"] in mods
    if kind in ("forge:true", "forge:false"):
        return kind == "forge:true"
    raise PolicyError(f"unsupported recipe condition {kind}")


def condition_possible(condition, loader, mods, custom=None):
    """``(can hold, can fail)`` for a load condition when the loaded mods are fixed but item tags are not.

    A pack's other mods and datapacks decide which tags are populated, so a tag condition may go either way;
    mod and port conditions are fixed. Atoms are treated as independent, which can only widen the answer.
    """
    kind = condition.get("condition" if loader == "fabric" else "type")
    if custom and kind in custom:
        return custom[kind], not custom[kind]
    if kind in ("fabric:not", "forge:not"):
        can_hold, can_fail = condition_possible(condition["value"], loader, mods, custom)
        return can_fail, can_hold
    if kind in ("fabric:and", "forge:and", "fabric:or", "forge:or"):
        parts = [condition_possible(c, loader, mods, custom) for c in condition["values"]]
        if kind.endswith(":and"):
            return all(h for h, _f in parts), any(f for _h, f in parts)
        return any(h for h, _f in parts), all(f for _h, f in parts)
    if kind in ("fabric:tags_populated", "forge:tag_empty"):
        return True, True
    holds = condition_holds(condition, loader, Tags({}), mods, custom)
    return holds, not holds


def condition_mods(condition):
    """Every mod id a Forge or Fabric load condition (or a list of them) names, at any depth."""
    found = set()
    if isinstance(condition, list):
        for part in condition:
            found |= condition_mods(part)
    elif isinstance(condition, dict):
        kind = condition.get("condition", condition.get("type"))
        if kind in ("fabric:all_mods_loaded", "fabric:any_mod_loaded"):
            found |= set(condition.get("values", []))
        elif kind == "forge:mod_loaded":
            found.add(condition.get("modid"))
        for key in ("value", "values"):
            if isinstance(condition.get(key), (dict, list)):
                found |= condition_mods(condition[key])
    return {mod for mod in found if isinstance(mod, str)}


def iter_recipe_files(roots):
    """Every ``(recipe id, recipe)`` under the resource roots, whatever its load conditions."""
    for root in roots:
        data = root / "data"
        if not data.is_dir():
            continue
        for namespace in sorted(p for p in data.iterdir() if p.is_dir()):
            folder = namespace / "recipes"
            for file in sorted(folder.rglob("*.json")):
                yield f"{namespace.name}:{file.relative_to(folder).with_suffix('').as_posix()}", read_json(file)


def load_recipes(roots, loader, tags, mods, custom=None):
    """Recipes ProjectE maps (vanilla types) whose load conditions hold; other types are reported as skipped."""
    recipes, skipped = {}, {}
    key = "fabric:load_conditions" if loader == "fabric" else "conditions"
    for rid, recipe in iter_recipe_files(roots):
        if not all(condition_holds(c, loader, tags, mods, custom) for c in recipe.get(key, [])):
            continue
        if recipe_ingredients(recipe) is None:
            skipped[rid] = recipe.get("type")
        else:
            recipes[rid] = recipe
    return recipes, skipped


def conversions_from(recipes, tags, remainders):
    """(recipe id, output, count, {node: amount}); a node is an item or a frozenset group."""
    found = []
    for rid, recipe in sorted(recipes.items()):
        output, count = result_of(recipe)
        if not output or count < 1:
            continue
        ingredients = {}
        valid = True
        for ingredient in recipe_ingredients(recipe):
            items = ingredient_items(ingredient, tags)
            if not items:
                valid = False
                break
            node = next(iter(items)) if len(items) == 1 else items
            ingredients[node] = ingredients.get(node, 0) + 1
            if isinstance(node, str) and node in remainders:
                remainder = remainders[node]
                ingredients[remainder] = ingredients.get(remainder, 0) - 1
        if valid:
            found.append((rid, output, count, ingredients))
    return found


# ----------------------------------------------------------------------------- resolution


def node_value(node, values, remainders):
    if isinstance(node, str):
        return values.get(node)
    best = None
    for member in node:
        value = values.get(member)
        if value is None or value <= 0:
            continue
        if member in remainders and remainders[member] in values:
            value -= values[remainders[member]]
        if value > 0 and (best is None or value < best):
            best = value
    return best


def conversion_cost(ingredients, values, remainders):
    cost = Fraction(0)
    for node, amount in ingredients.items():
        value = node_value(node, values, remainders)
        if value is None or value == 0:
            return None
        cost += amount * value
    return cost if cost > 0 else None


def resolve(conversions, fixed, blocked, remainders):
    """Minimum-cost fixpoint over recipe conversions, as ProjectE's SimpleGraphMapper computes it."""
    values = dict(fixed)
    changed = True
    while changed:
        changed = False
        for _rid, output, count, ingredients in conversions:
            if output in fixed or output in blocked:
                continue
            cost = conversion_cost(ingredients, values, remainders)
            if cost is None:
                continue
            value = floor_value(cost / count)
            if value > 0 and (output not in values or value < values[output]):
                values[output] = value
                changed = True
    return values


# ----------------------------------------------------------------------------- policy


def expand(pattern, universe):
    if "*" not in pattern:
        return [pattern] if pattern in universe else []
    return sorted(item for item in universe if fnmatch.fnmatchcase(item, pattern))


def specificity(key):
    return float("inf") if "*" not in key else len(key.replace("*", ""))


def assign_patterns(sections, universe, problems):
    """Map every item to its (section, key); the most specific pattern wins and ties are problems.

    ``sections`` maps a label to {key: [patterns]}.
    """
    owner, rank = {}, {}
    for label, section in sections.items():
        for key, patterns in section.items():
            for pattern in patterns:
                matches = expand(pattern, universe)
                if not matches:
                    problems.append(f"{label} entry {pattern} matches no registered item")
                for item in matches:
                    if item not in owner or specificity(pattern) > rank[item]:
                        owner[item], rank[item] = (label, key), specificity(pattern)
                    elif specificity(pattern) == rank[item]:
                        problems.append(f"{item} matches both {owner[item][0]} {owner[item][1]} and {label} {key}")
    return owner


def patterns_of(key, entry):
    """A values or exclude entry covers its key as a pattern, or the patterns listed in ``items``."""
    return entry.get("items", [key]) if isinstance(entry, dict) else [key]


def reason_of(entry):
    return entry if isinstance(entry, str) else entry.get("why")


def explicit_value(spec, system, baseline, key):
    if isinstance(spec.get(system), int):
        return Fraction(spec[system])
    if "emc" in spec:
        return Fraction(spec["emc"])
    if "like" in spec:
        anchor = baseline.get(spec["like"])
        if anchor is None:
            raise PolicyError(f"{key}: {spec['like']} has no {system} baseline value")
        return Fraction(int(anchor * Fraction(str(spec.get("times", 1)))))
    raise PolicyError(f"{key}: give emc, like, or per-system values")


NAMESPACE_ID = re.compile(r"[a-z][a-z0-9_]{0,63}\Z")


def _relative_root(value):
    return isinstance(value, str) and bool(value) and not value.startswith(("/", "\\")) and ".." not in Path(value).parts


def policy_namespaces(policy):
    """The mod ids one policy prices. The first is ``namespace``; extra modules ship their own data files."""
    primary = policy.get("namespace")
    if not isinstance(primary, str) or not NAMESPACE_ID.fullmatch(primary):
        raise PolicyError("namespace must be the port's mod id")
    listed = policy.get("namespaces", [primary])
    if (not isinstance(listed, list) or not listed
            or any(not isinstance(name, str) or not NAMESPACE_ID.fullmatch(name) for name in listed)
            or listed[0] != primary or len(set(listed)) != len(listed)):
        raise PolicyError("namespaces must list unique mod ids and start with namespace")
    outputs = policy.get("outputs")
    if outputs is not None:
        if not isinstance(outputs, dict) or set(outputs) != set(listed):
            raise PolicyError("outputs must map every namespace to its resource root")
        for name, relative in outputs.items():
            if not _relative_root(relative):
                raise PolicyError(f"outputs.{name} must be a relative resource root")
        single = policy.get("output")
        if single is not None and single != outputs[primary]:
            raise PolicyError("output must match outputs for the primary namespace")
    elif "output" in policy and not _relative_root(policy["output"]):
        raise PolicyError("output must be a relative resource root")
    return listed


def validate_policy(policy):
    allowed = {"namespace", "namespaces", "loaders", "resources", "output", "outputs", "ignored", "values",
               "vanilla", "exclude", "conversions", "processes", "remainders", "conditions", "notes",
               "projecte_mappers", "optional_mods"}
    unknown = set(policy) - allowed
    if unknown:
        raise PolicyError(f"unknown policy keys: {', '.join(sorted(unknown))}")
    for system in SYSTEMS:
        if policy["loaders"].get(system) not in LOADERS:
            raise PolicyError(f"loaders.{system} must be one of {LOADERS}")
        if not policy["resources"].get(system):
            raise PolicyError(f"resources.{system} must list resource roots")
    for key, spec in policy.get("values", {}).items():
        if not isinstance(spec, dict) or not spec.get("why"):
            raise PolicyError(f"values.{key} needs a why")
        extra = set(spec) - {"emc", "like", "times", "why", "items", *SYSTEMS}
        if extra:
            raise PolicyError(f"values.{key}: unknown keys {', '.join(sorted(extra))}")
        items = spec.get("items")
        if items is not None and (not isinstance(items, list) or not items
                                  or not all(isinstance(i, str) and i for i in items)):
            raise PolicyError(f"values.{key}: items must be a non-empty list of item ids or patterns")
    for key, spec in policy.get("vanilla", {}).items():
        if not key.startswith("minecraft:") or not ITEM_ID.match(key):
            raise PolicyError(f"vanilla.{key}: only minecraft: items can be given a value here")
        if not isinstance(spec, dict) or not spec.get("why") or set(spec) - {"emc", "like", "times", "why", "systems"}:
            raise PolicyError(f"vanilla.{key}: needs why, systems and emc or like (with optional times) only")
        systems = spec.get("systems")
        if not isinstance(systems, list) or not systems or set(systems) - set(SYSTEMS):
            raise PolicyError(f"vanilla.{key}: systems must list some of {', '.join(SYSTEMS)}")
    for key, entry in policy.get("exclude", {}).items():
        if isinstance(entry, dict) and (set(entry) - {"items", "why"} or not isinstance(entry.get("items"), list)
                                        or not entry["items"]):
            raise PolicyError(f"exclude.{key}: give a reason, or an object with items and why")
        if not reason_of(entry):
            raise PolicyError(f"exclude.{key} needs a reason")
    for key, entry in policy.get("remainders", {}).items():
        if isinstance(entry, str) and ITEM_ID.match(entry):
            continue
        if (not isinstance(entry, dict) or set(entry) != {"item", "systems", "why"} or not entry["why"]
                or not isinstance(entry["item"], str) or not ITEM_ID.match(entry["item"])
                or not isinstance(entry["systems"], list) or not entry["systems"]
                or set(entry["systems"]) - set(SYSTEMS)):
            raise PolicyError(f"remainders.{key}: give the remainder item, or an object with item, systems and why")
    for key, why in policy.get("ignored", {}).items():
        if not isinstance(why, str) or not why:
            raise PolicyError(f"ignored.{key} needs a reason")
    namespaces = policy_namespaces(policy)
    for kind, entry in policy.get("conditions", {}).items():
        if (not isinstance(entry, dict) or set(entry) != {"holds", "why"} or not isinstance(entry["holds"], bool)
                or not entry["why"] or not any(kind.startswith(namespace + ":") for namespace in namespaces)):
            raise PolicyError(f"conditions.{kind}: the port's own condition type, with a boolean holds and a why")
    optional = policy.get("optional_mods", {})
    if not isinstance(optional, dict) or len(optional) > 4:
        raise PolicyError("optional_mods must map at most four mod ids to their ports")
    for mod, entry in optional.items():
        if (not isinstance(mod, str) or not NAMESPACE_ID.fullmatch(mod) or mod in namespaces
                or not isinstance(entry, dict) or set(entry) != {"port", "why"} or not entry["why"]
                or not isinstance(entry["port"], str) or not NAMESPACE_ID.fullmatch(entry["port"])):
            raise PolicyError(f"optional_mods.{mod}: another mod's id, with the port directory that prices it and a why")
    mappers = policy.get("projecte_mappers")
    if mappers is not None:
        guard = mappers.get("guard") if isinstance(mappers, dict) else None
        if (not isinstance(mappers, dict) or set(mappers) != {"guard", "why"} or not mappers.get("why")
                or not _relative_root(guard) or not str(guard).endswith(".json")):
            raise PolicyError("projecte_mappers needs a why and a relative guard json path")
    names = set()
    for conversion in policy.get("conversions", []):
        name = conversion.get("name")
        if not name or not re.fullmatch(r"[a-z0-9_]+", name) or name in names:
            raise PolicyError(f"conversion name {name!r} must be unique lowercase snake_case")
        names.add(name)
        if set(conversion) - {"name", "why", "output", "count", "ingredients"} or not conversion.get("why"):
            raise PolicyError(f"conversion {name}: needs name, why, output and ingredients only")
        count = conversion.get("count", 1)
        ingredients = conversion.get("ingredients")
        if not isinstance(count, int) or count < 1 or not isinstance(ingredients, dict) or not ingredients:
            raise PolicyError(f"conversion {name}: count must be at least one and ingredients a non-empty object")
        if any(not isinstance(a, int) or isinstance(a, bool) or a == 0 for a in ingredients.values()):
            raise PolicyError(f"conversion {name}: ingredient amounts must be nonzero integers")
    for process in policy.get("processes", []):
        if set(process) - {"name", "why", "in", "out", "systems"} or not all(process.get(k) for k in ("name", "why", "in", "out")):
            raise PolicyError(f"process {process.get('name')!r}: needs name, why, in and out, and optional systems")
        if set(process.get("systems", SYSTEMS)) - set(SYSTEMS) or not process.get("systems", SYSTEMS):
            raise PolicyError(f"process {process['name']!r}: systems must list some of {', '.join(SYSTEMS)}")


# ----------------------------------------------------------------------------- the model


class Port:
    def __init__(self, directory):
        self.dir = Path(directory)
        self.policy = read_json(self.dir / "emc" / "policy.json")
        validate_policy(self.policy)
        self.namespaces = policy_namespaces(self.policy)
        self.namespace = self.policy["namespace"]
        lines = (self.dir / "emc" / "registry.txt").read_text(encoding="utf-8").split()
        ignored = self.policy.get("ignored", {})
        self.ignored = sorted(i for i in lines if any(fnmatch.fnmatchcase(i, p) for p in ignored))
        self.items = sorted(set(lines) - set(self.ignored))
        self.problems = []

    def system_inputs(self, system):
        loader = self.policy["loaders"][system]
        roots = [self.dir / r for r in self.policy["resources"][system]]
        tags = Tags.for_loader(loader)
        tags.add_resources(roots)
        remainders = dict(REMAINDERS)
        for key, entry in self.policy.get("remainders", {}).items():
            if isinstance(entry, dict) and system not in entry["systems"]:
                continue
            for item in (expand(key, set(self.items)) if "*" in key else [key]):
                remainders[item] = entry if isinstance(entry, str) else entry["item"]
        mods = self._mods(loader)
        custom = {kind: entry["holds"] for kind, entry in self.policy.get("conditions", {}).items()}
        recipes, skipped = load_recipes(roots, loader, tags, mods, custom)
        return tags, remainders, recipes, skipped

    def _mods(self, loader):
        """The mods a standalone port runs with."""
        return {"minecraft", loader, *self.namespaces} | ({"fabric-api"} if loader == "fabric" else set())

    def _optional_ports(self):
        """{mod id: (port directory, its policy, its generated values.json)} for every declared optional mod."""
        if getattr(self, "_optional_cache", None) is None:
            self._optional_cache = {}
            for mod, entry in sorted(self.policy.get("optional_mods", {}).items()):
                directory = self.dir.parent / entry["port"]
                policy_file, values_file = directory / "emc" / "policy.json", directory / "emc" / "values.json"
                if not policy_file.is_file() or not values_file.is_file():
                    raise PolicyError(f"optional_mods.{mod}: {entry['port']} has no emc/policy.json and values.json")
                self._optional_cache[mod] = (directory, read_json(policy_file), read_json(values_file))
        return self._optional_cache

    def _optional_tags(self, system, chosen):
        """The port's tags plus the tags of the chosen optional mods' ports, read for the same loader."""
        loader = self.policy["loaders"][system]
        tags = Tags.for_loader(loader)
        tags.add_resources([self.dir / r for r in self.policy["resources"][system]])
        for mod in chosen:
            directory, policy, _values = self._optional_ports()[mod]
            same = [s for s in (system, *SYSTEMS) if policy["loaders"].get(s) == loader]
            if not same:
                raise PolicyError(f"optional_mods.{mod}: {directory.name} has no {loader} resources")
            tags.add_resources([directory / r for r in policy["resources"][same[0]]])
        return tags

    def _optional_values(self, system):
        """The optional mods' items at the values their own ports ship for this system (excluded items are left out)."""
        found = {}
        for _mod, (_directory, _policy, document) in self._optional_ports().items():
            for item, entry in document["items"].items():
                value = entry.get(system)
                if ("excluded" not in entry and isinstance(value, int) and not isinstance(value, bool)
                        and value >= 1 and item not in self.items):
                    found[item] = Fraction(value)
        return found

    def _foreign_mods(self, recipe, loader):
        key = "fabric:load_conditions" if loader == "fabric" else "conditions"
        return condition_mods(recipe.get(key, [])) - self._mods(loader) - {"forge", "fabric"}

    def recipe_alternatives(self, system):
        """Recipe sets beyond the standalone port's, as ``(label, recipes, tags, priced)`` tuples.

        For every combination of declared optional mods: every recipe that loads with those mods and their ports'
        tags, read against those merged tags. That includes the standalone recipes, because another mod filling
        a tag they accept can make them cheaper (Mekanism's sulfur dust in ICBM's sulfur tag makes the battery
        cheaper). Added to that is every recipe whose conditions name an
        optional mod and could hold with those mods whatever the tags are. The second part matters when the mod
        fills a tag that switches its own recipe off (Mekanism fills the uranium tags, which turns off ICBM's
        atomic-alloy nuclear recipe): a pack whose tags differ still loads it, so it bounds the value too. These
        sets are ``priced``: outputs take the minimum over them. Last, recipes whose conditions name a mod the
        policy does not declare, read with their conditions ignored and not priced, so the gain check can refuse
        them.
        """
        loader = self.policy["loaders"][system]
        roots = [self.dir / r for r in self.policy["resources"][system]]
        custom = {kind: entry["holds"] for kind, entry in self.policy.get("conditions", {}).items()}
        key = "fabric:load_conditions" if loader == "fabric" else "conditions"
        base_tags = self._optional_tags(system, ())
        base, _skipped = load_recipes(roots, loader, base_tags, self._mods(loader), custom)
        optional = sorted(self.policy.get("optional_mods", {}))
        gated, undeclared = {}, {}
        for rid, recipe in iter_recipe_files(roots):
            if rid in base or recipe_ingredients(recipe) is None:
                continue
            mods = self._foreign_mods(recipe, loader)
            if mods & set(optional):
                gated[rid] = recipe
            elif mods:
                undeclared[rid] = recipe
        found = []
        for size in range(1, len(optional) + 1):
            for chosen in itertools.combinations(optional, size):
                tags = self._optional_tags(system, chosen)
                mods = self._mods(loader) | set(chosen)
                active, _skipped = load_recipes(roots, loader, tags, mods, custom)
                for rid, recipe in gated.items():
                    if all(condition_possible(c, loader, mods, custom)[0] for c in recipe.get(key, [])):
                        active.setdefault(rid, recipe)
                found.append((f"with {' and '.join(chosen)}", active, tags, True))
        if undeclared:
            found.append(("gated on undeclared mods", undeclared, base_tags, False))
        return found

    def compute(self):
        problems = self.problems
        for item in self.items:
            if self._namespace_of(item) is None:
                problems.append(f"{item} is outside the policy namespaces ({', '.join(self.namespaces)})")
        universe = set(self.items)
        owners = assign_patterns({
            "exclude": {key: patterns_of(key, entry) for key, entry in self.policy.get("exclude", {}).items()},
            "values": {key: patterns_of(key, spec) for key, spec in self.policy.get("values", {}).items()},
        }, universe, problems)
        excluded_by = {item: key for item, (label, key) in owners.items() if label == "exclude"}
        explicit_by = {item: key for item, (label, key) in owners.items() if label == "values"}
        self.excluded_by, self.explicit_by = excluded_by, explicit_by
        self.policy_conversions = self._expand_conversions()
        self.results, self.sources, self.inputs, self.vanilla, self.alternatives = {}, {}, {}, {}, {}
        # ProjectE-only state: tag node values, the items the ore blacklists zero (and why), the items
        # metals.json prices, and the item (and why) a policy conversion names in place of a tag.
        self.tag_values, self.projecte_unvalued, self.projecte_overwritten, self.projecte_tag_items = {}, {}, {}, {}
        self.projecte_blacklisted, self.projecte_tag_reasons = {}, {}
        for system in SYSTEMS:
            tags, remainders, recipes, skipped = self.system_inputs(system)
            baseline = {k: Fraction(v) for k, v in read_json(BASELINES / f"{system}-vanilla.json").items()}
            tag_defaults = read_json(BASELINES / f"{system}-tag-values.json")
            fixed, source = dict(baseline), {}
            for tag, value in tag_defaults.items():
                for item in sorted(tags.members(tag)):
                    if item in universe and item not in source:
                        fixed[item] = Fraction(value)
                        source[item] = f"tag #{tag}"
            self.vanilla[system] = {}
            for item, spec in self.policy.get("vanilla", {}).items():
                if system not in spec["systems"]:
                    continue
                if item in baseline:
                    problems.append(f"vanilla.{item}: {system} already values it at {baseline[item]}; a port may "
                                    f"only fill a missing vanilla value")
                    continue
                fixed[item] = explicit_value(spec, system, baseline, f"vanilla.{item}")
                self.vanilla[system][item] = int(fixed[item])
            for item, key in explicit_by.items():
                value = explicit_value(self.policy["values"][key], system, baseline, key)
                if item in source and fixed[item] != value:
                    if system == "itemalchemy":
                        problems.append(f"{item}: Item Alchemy's {source[item]} ({fixed[item]}) overrides the "
                                        f"explicit value {value}; set it to match")
                        continue
                    problems.append(f"{item}: ProjectE's {source[item]} ({fixed[item]}) conflicts with explicit {value}")
                fixed[item] = value
                source[item] = "explicit"
            blocked = set(excluded_by)
            for item in sorted(blocked):
                if item in source and source[item].startswith("tag") and system == "itemalchemy":
                    problems.append(f"{item} is excluded but Item Alchemy gives it {fixed[item]} via {source[item]}")
                fixed.pop(item, None)
            alternatives = self.recipe_alternatives(system)
            for item, value in self._optional_values(system).items():
                fixed.setdefault(item, value)
            unblacklisted = dict(fixed)
            blacklisted = self._projecte_blacklist(tags, universe) if system == "projecte" else {}
            for item in blacklisted:
                fixed.pop(item, None)
            blocked |= set(blacklisted)
            metals = read_json(BASELINES / "projecte-tag-conversions.json") if system == "projecte" else []
            overwritten_tags = {entry["output"] for entry in metals}
            tag_nodes = {f"#{tag}": Fraction(value) for tag, value in tag_defaults.items()} if system == "projecte" else {}
            conversions = conversions_from(recipes, tags, remainders)
            for label, extra, extra_tags, priced in alternatives:
                if priced:
                    conversions += [(f"{rid} ({label})", output, count, ingredients)
                                    for rid, output, count, ingredients in conversions_from(extra, extra_tags, remainders)]
            self.alternatives[system] = [("standalone", recipes, tags, True), *alternatives]
            for name, output, count, ingredients in self.policy_conversions:
                nodes = {}
                for ingredient, amount in ingredients.items():
                    if ingredient.startswith("#") and ingredient[1:] in overwritten_tags:
                        node = self._projecte_tag_item(ingredient[1:], tags, universe, baseline, name)
                    else:
                        node = tags.members(ingredient[1:]) if ingredient.startswith("#") else ingredient
                    if isinstance(node, frozenset) and len(node) == 1:
                        node = next(iter(node))
                    nodes[node] = nodes.get(node, 0) + amount
                conversions.append((f"conversion {name}", output, count, nodes))
            created = set(read_json(BASELINES / "projecte-tags.json")) - overwritten_tags \
                if system == "projecte" else set()
            self._tag_mapper_conversions(created, tags, universe, baseline, conversions)
            overwritten = self._metals_conversions(metals, tags, universe, fixed, blocked, conversions, source)
            shadowed = [c for c in conversions if c[1] in overwritten and not c[0].startswith(METALS)]
            conversions = [c for c in conversions if c[1] not in overwritten or c[0].startswith(METALS)]
            values = resolve(conversions, {**fixed, **tag_nodes}, blocked, remainders)
            nodes = {key: values.pop(key) for key in [key for key in values if key.startswith("#")]}
            if system == "projecte":
                self.tag_values = nodes
                self.projecte_blacklisted, self.projecte_overwritten = blacklisted, overwritten
                self.projecte_unvalued = dict(blacklisted)
                if blacklisted:
                    others = resolve(conversions, {**unblacklisted, **tag_nodes}, blocked - set(blacklisted), remainders)
                    for item in self.items:
                        if item not in values and item not in blacklisted and others.get(item, 0) >= 1:
                            self.projecte_unvalued[item] = "made only from items ProjectE's ore blacklists zero"
                self._choose_projecte_tag_items(created | overwritten_tags, tags, universe, baseline, values)
            self.inputs[system] = (tags, remainders, conversions, baseline, fixed, skipped)
            self.results[system] = values
            self.sources[system] = source
            self._check_system(system)
            self._check_recipe_gains(system)
            if system == "projecte":
                self._check_overwritten(shadowed)
        self._check_processes()
        self._check_projecte_mappers()
        self._check_unmanaged_projecte_files()

    def _projecte_blacklist(self, tags, universe):
        """Port items ProjectE's ore blacklist mappers zero, with the reason, in registry order."""
        found = {}
        for tag, mapper in PROJECTE_BLACKLISTS.items():
            for item in sorted(tags.members(tag)):
                if item in universe and item not in found:
                    found[item] = f"#{tag} ({mapper})"
        return found

    def _projecte_tag_item(self, tag, tags, universe, baseline, name):
        """The one item a policy conversion means by a tag that ProjectE's metals.json prices on its own.

        ProjectE values such a tag node by its metals.json conversion, not by its cheapest member, so a
        conversion naming the tag could price its output above what the item it consumes is worth.
        """
        members = sorted(i for i in tags.members(tag) if i in universe or i in baseline)
        if len(members) != 1:
            self.problems.append(f"conversion {name}: ProjectE prices #{tag} through metals.json, not by its "
                                 f"members ({', '.join(members) or 'none'}); name the item instead")
            return f"#{tag}"
        self.projecte_tag_items[tag] = members[0]
        self.projecte_tag_reasons[tag] = "ProjectE prices the tag through metals.json, not by its members"
        return members[0]

    def _tag_mapper_conversions(self, created, tags, universe, baseline, conversions):
        """Add ProjectE TagMapper's `tag = member` and `member = tag` conversions for the tags ProjectE creates.

        An excluded item in such a tag would take the tag's value, so that is a problem.
        """
        for tag in sorted(created):
            for member in sorted(m for m in tags.members(tag) if m in universe or m in baseline):
                if member in self.excluded_by:
                    self.problems.append(f"{member} is excluded, but ProjectE's TagMapper values it through #{tag}")
                    continue
                conversions.append((f"{TAG_MAPPER} #{tag}", f"#{tag}", 1, {member: 1}))
                conversions.append((f"{TAG_MAPPER} #{tag}", member, 1, {f"#{tag}": 1}))

    def _choose_projecte_tag_items(self, created, tags, universe, baseline, values):
        """Name an item instead of a tag in the ProjectE file where TagMapper would lower a member.

        Writing ``#tag`` in a custom conversion makes ProjectE's TagMapper value every member at the cheapest
        member's value, which would devalue vanilla members and give excluded members EMC. A tag only this
        port's file names is kept when all its members are worth the same; otherwise the conversion names the
        cheapest member, which costs the output exactly what the tag would.
        """
        for _name, _output, _count, ingredients in self.policy_conversions:
            for ingredient in ingredients:
                tag = ingredient[1:]
                if not ingredient.startswith("#") or tag in created or tag in self.projecte_tag_items:
                    continue
                members = sorted(m for m in tags.members(tag) if m in universe or m in baseline)
                priced = {m: values[m] for m in members if values.get(m, 0) > 0}
                if not priced or (len(priced) == len(members) and len(set(priced.values())) == 1):
                    continue
                cheapest = min(priced, key=lambda m: (priced[m], m))
                self.projecte_tag_items[tag] = cheapest
                self.projecte_tag_reasons[tag] = ("naming the tag would let ProjectE's TagMapper lower every member "
                                                  "to the cheapest one's value")

    @staticmethod
    def _metals_conversions(metals, tags, universe, fixed, blocked, conversions, source):
        """Add ProjectE metals.json's tag conversions; return {port item: tag} for members they price.

        metals.json uses ``setValueFromConversion`` with ``propagateTags``: the tag node and every member
        take their value only from that conversion, unless a fixed (``before``) value wins.
        """
        overwritten, chosen = {}, {}
        for entry in metals:
            tag, count = entry["output"], entry.get("count", 1)
            nodes = {f"#{name}": amount for name, amount in entry["ingredients"].items()}
            conversions.append((f"{METALS} #{tag}", f"#{tag}", count, nodes))
            for member in sorted(tags.members(tag)):
                if member in universe and member not in fixed and member not in blocked:
                    # A later setValueFromConversion for the same item replaces the earlier one.
                    overwritten[member], chosen[member] = tag, (count, nodes)
        for member, tag in overwritten.items():
            source[member] = f"tag #{tag} (ProjectE metals.json)"
            conversions.append((f"{METALS} #{tag}", member, *chosen[member]))
        return overwritten

    def _check_overwritten(self, shadowed):
        """A recipe that makes a metals.json-priced item for less would be an EMC gain; ProjectE only logs it."""
        values = self.results["projecte"]
        remainders = self.inputs["projecte"][1]
        for rid, output, count, ingredients in shadowed:
            cost = conversion_cost(ingredients, values, remainders)
            if cost is not None and output in values and floor_value(cost / count) < values[output]:
                self.problems.append(f"ProjectE: metals.json prices {output} at {values[output]} through "
                                     f"#{self.projecte_overwritten[output]}, but {rid} makes it for "
                                     f"{float(cost / count):g}; give it an explicit value")

    def _check_unmanaged_projecte_files(self):
        """Every ProjectE custom conversion file the port ships must be generated, so nothing else sets values."""
        managed = {(self.output_root(namespace) / "data" / namespace / "pe_custom_conversions" / f"{namespace}.json")
                   .resolve() for namespace in self.namespaces}
        for relative in self.policy["resources"]["projecte"]:
            data = self.dir / relative / "data"
            if not data.is_dir():
                continue
            for file in sorted(data.glob("*/pe_custom_conversions/**/*.json")):
                if file.resolve() not in managed:
                    self.problems.append(f"{file.relative_to(self.dir)} is a ProjectE custom conversion file the "
                                         f"policy does not generate; move its values into emc/policy.json")

    def _check_recipe_gains(self, system):
        """No recipe, conditional or not, may make a port item worth more EMC than its ingredients.

        Recipes are read again in every set ``recipe_alternatives`` returns, including those gated on mods the
        policy does not declare, and priced with the shipped values (other ports' items at their own values).
        An alternative that needs an item with no known EMC fails too: its output would have to be excluded,
        or the item priced, because the mod could make that item outside EMC and craft it into a valued one.
        Item Alchemy outputs priced by its own tag defaults are skipped, as in ``_check_system``.
        """
        label = "ProjectE" if system == "projecte" else "Item Alchemy"
        loader = self.policy["loaders"][system]
        remainders = self.inputs[system][1]
        values = self.results[system]
        source = self.sources[system]
        reported = set()
        for set_label, recipes, tags, priced in self.alternatives[system]:
            for rid, recipe in sorted(recipes.items()):
                for _rid, output, count, ingredients in conversions_from({rid: recipe}, tags, remainders):
                    if (output not in self.items or output in self.excluded_by
                            or (system == "itemalchemy" and source.get(output, "").startswith("tag"))):
                        continue
                    mods = ", ".join(sorted(self._foreign_mods(recipe, loader)))
                    where = ("" if set_label == "standalone" else f" ({set_label})" if priced
                             else f" (loads only with {mods})")
                    cost, unknown = Fraction(0), None
                    for node, amount in ingredients.items():
                        value = node_value(node, values, remainders)
                        if value is None or value == 0:
                            unknown = node if isinstance(node, str) else ", ".join(sorted(node))
                            break
                        cost += amount * value
                    if unknown is not None:
                        if set_label != "standalone" and (rid, output) not in reported:
                            reported.add((rid, output))
                            fix = "price it in that mod's port" if priced else "declare the mod in optional_mods"
                            self.problems.append(f"{label}: recipe {rid}{where} needs {unknown}, which has no EMC "
                                                 f"here; {fix} or exclude {output}")
                        continue
                    value = values.get(output)
                    if value is not None and value * count > cost and (rid, output) not in reported:
                        reported.add((rid, output))
                        fix = "" if priced else "; declare the mod in optional_mods so the value takes this recipe"
                        self.problems.append(f"{label}: recipe {rid}{where} makes {count} {output} worth "
                                             f"{float(value * count):g} from {float(cost):g} EMC of ingredients{fix}")

    def _check_system(self, system):
        problems = self.problems
        tags, remainders, conversions, baseline, fixed, _skipped = self.inputs[system]
        values = self.results[system]
        label = "ProjectE" if system == "projecte" else "Item Alchemy"
        source = self.sources[system]
        for rid, output, count, ingredients in conversions:
            if output not in fixed:
                continue
            if system == "itemalchemy" and source.get(output, "").startswith("tag"):
                continue
            cost = conversion_cost(ingredients, values, remainders)
            if cost is None:
                continue
            if floor_value(cost / count) < fixed[output]:
                where = "devalues vanilla" if output in baseline else "undercuts the fixed value of"
                problems.append(f"{label}: recipe {rid} {where} {output}: costs {float(cost / count):g} "
                                f"each, value {fixed[output]}")
        # Mod items in vanilla tags feed vanilla recipes through ProjectE's ingredient groups.
        if system == "projecte":
            for tag in read_json(BASELINES / "vanilla-recipe-tags.json"):
                members = tags.members(tag)
                ours = [i for i in members if i in values and self._namespace_of(i)]
                vanilla = [baseline[i] for i in members if i in baseline]
                if ours and vanilla:
                    low = min(ours, key=lambda i: values[i])
                    if values[low] < min(vanilla):
                        problems.append(f"{label}: {low} ({values[low]}) is cheaper than every vanilla member of "
                                        f"#{tag} ({min(vanilla)}); vanilla recipes using the tag would be devalued")

    def _bindings(self, patterns, required, universe):
        """Placeholder bindings that fill every required pattern with a known item.

        Each placeholder ranges over the values it takes in any matching pattern (possibly empty), and
        bindings are the cross product of those ranges.
        """
        names = sorted({n for p in patterns for n in PLACEHOLDER.findall(p)})
        if not names:
            return [{}]
        ranges = {name: set() for name in names}
        for pattern in patterns:
            regex = re.compile(PLACEHOLDER.sub(lambda m: f"(?P<{m.group(1)}>[a-z0-9_]*)",
                                               re.escape(pattern).replace(r"\{", "{").replace(r"\}", "}")) + r"\Z")
            if not regex.groupindex:
                continue
            for item in universe:
                match = regex.match(item)
                if match:
                    for name in regex.groupindex:
                        ranges[name].add(match.group(name))
        bindings = []
        for values in itertools.product(*(sorted(ranges[n]) for n in names)):
            binding = dict(zip(names, values))
            if all(self._fill(p, binding) in universe for p in required):
                bindings.append(binding)
        return bindings

    def _expand_conversions(self):
        universe = set(self.items)
        for system in SYSTEMS:
            universe |= set(read_json(BASELINES / f"{system}-vanilla.json"))
        expanded = []
        self.conversion_counts = {}
        for conversion in self.policy.get("conversions", []):
            items = [p for p in conversion["ingredients"] if not p.startswith("#")] + [conversion["output"]]
            bindings = self._bindings(items, items, universe)
            if not bindings:
                self.problems.append(f"conversion {conversion['name']!r} matches no items")
            for binding in bindings:
                ingredients = {self._fill(p, binding): amount for p, amount in conversion["ingredients"].items()}
                expanded.append((conversion["name"], self._fill(conversion["output"], binding),
                                 conversion.get("count", 1), ingredients))
            self.conversion_counts[conversion["name"]] = len(bindings)
        return expanded

    @staticmethod
    def _alternatives(section):
        return section if isinstance(section, list) else [section]

    @classmethod
    def _outputs(cls, process):
        return cls._alternatives(process["out"])

    @staticmethod
    def _fill(pattern, binding):
        return PLACEHOLDER.sub(lambda m: binding[m.group(1)], pattern)

    def _check_processes(self):
        self.process_counts = {}
        universe = set(self.items) | set(self.results["projecte"]) | set(self.results["itemalchemy"])
        for process in self.policy.get("processes", []):
            outputs = [p for out in self._outputs(process) for p in out]
            cases = [(inputs, binding) for inputs in self._alternatives(process["in"])
                     for binding in self._bindings(list(inputs) + outputs, list(inputs), universe)]
            if not cases:
                self.problems.append(f"process {process['name']!r} matches no items")
            checked = 0
            for inputs, binding in cases:
                for system in process.get("systems", SYSTEMS):
                    values = self.results[system]
                    cost = Fraction(0)
                    for pattern, amount in inputs.items():
                        item = self._fill(pattern, binding)
                        if item not in values:
                            cost = None
                            break
                        cost += amount * values[item]
                    if cost is None:
                        continue
                    best, best_out = Fraction(0), None
                    for out in self._outputs(process):
                        gain = sum((amount * values.get(self._fill(p, binding), 0) for p, amount in out.items()),
                                   Fraction(0))
                        if gain > best:
                            best, best_out = gain, out
                    checked += 1
                    if best > cost:
                        names = ", ".join(self._fill(p, binding) for p in best_out)
                        self.problems.append(f"{system}: process {process['name']!r} turns {float(cost):g} EMC of "
                                             f"input into {float(best):g} EMC ({names})")
            self.process_counts[process["name"]] = checked

    def _loaded_recipes(self, system):
        loader = self.policy["loaders"][system]
        tags = self.inputs[system][0]
        mods = {"minecraft", loader, *self.namespaces} | ({"fabric-api"} if loader == "fabric" else set())
        custom = {kind: entry["holds"] for kind, entry in self.policy.get("conditions", {}).items()}
        key = "fabric:load_conditions" if loader == "fabric" else "conditions"
        for relative in self.policy["resources"][system]:
            data = self.dir / relative / "data"
            if not data.is_dir():
                continue
            for namespace in sorted(path for path in data.iterdir() if path.is_dir()):
                folder = namespace / "recipes"
                if not folder.is_dir():
                    continue
                for file in sorted(folder.rglob("*.json")):
                    recipe = read_json(file)
                    if not all(condition_holds(c, loader, tags, mods, custom) for c in recipe.get(key, [])):
                        continue
                    rid = f"{namespace.name}:{file.relative_to(folder).with_suffix('').as_posix()}"
                    yield rid, recipe

    def _projecte_guard_values(self):
        guard = {}
        for item, value in self.inputs["projecte"][3].items():
            number = _guard_value({item: value}, item)
            if number is not None:
                guard[item] = number
        for item, value in self.vanilla["projecte"].items():
            guard[item] = int(value)
        for item in self.items:
            number = self.final("projecte", item)
            if number is not None:
                guard[item] = number
        return dict(sorted(guard.items()))

    @staticmethod
    def _cheapest_priced(specs, tags, values):
        cost = 0
        amounts = 0
        ingredients = {}
        for ingredient, amount in specs:
            items = ingredient_items(ingredient, tags)
            if not items:
                return None
            priced = [(item, number) for item in items if (number := _guard_value(values, item)) is not None]
            if not priced:
                return None
            item, value = min(priced, key=lambda pair: (pair[1], pair[0]))
            cost += amount * value
            amounts += amount
            ingredients[item] = ingredients.get(item, 0) + amount
        return cost, amounts, ingredients

    def _check_projecte_mappers(self):
        self.mapper_stats = None
        if "projecte_mappers" not in self.policy:
            return
        self.mapper_guard = self._projecte_guard_values()
        permitted = refused = 0
        tags = self.inputs["projecte"][0]
        guard = self.mapper_guard
        for rid, recipe in self._loaded_recipes("projecte"):
            if not str(recipe.get("type", "")).startswith("mekanism"):
                continue
            parsed = item_only_machine_recipe(recipe)
            if parsed is None:
                continue
            output, count, specs = parsed
            priced = self._cheapest_priced(specs, tags, guard)
            if output not in guard or priced is None:
                refused += 1
                continue
            cost, _amounts, ingredients = priced
            if mapper_permits(ingredients, output, count, guard):
                permitted += 1
                if cost // count < guard[output]:
                    self.problems.append(f"ProjectE mapper guard would undercut {output}: recipe {rid} "
                                         f"costs {cost // count} against {guard[output]}")
            else:
                refused += 1
        self.mapper_stats = {"guard": len(guard), "permitted": permitted, "refused": refused}
        for system in SYSTEMS:
            self._check_machine_gains(system)

    def _check_machine_gains(self, system):
        """A one-for-one item recipe must not make a derived port item worth more than its inputs.

        Stack-multiplying recipes (ore factories, the sawmill's extra planks) and explicit values
        (sawdust, sulfur) are the cases the mapper guard refuses instead of repricing.
        """
        label = "ProjectE" if system == "projecte" else "Item Alchemy"
        tags = self.inputs[system][0]
        values = self.results[system]
        for rid, recipe in self._loaded_recipes(system):
            if not str(recipe.get("type", "")).startswith("mekanism"):
                continue
            parsed = item_only_machine_recipe(recipe)
            if parsed is None:
                continue
            output, count, specs = parsed
            if output not in self.items or output in self.explicit_by or output in self.excluded_by:
                continue
            priced = self._cheapest_priced(specs, tags, values)
            if priced is None:
                continue
            cost, amounts, _ingredients = priced
            if count > amounts:
                continue
            output_value = _guard_value(values, output)
            if output_value is None:
                continue
            if output_value * count > cost:
                self.problems.append(f"{label}: {rid} makes {count} {output} worth {output_value * count} "
                                     f"from {cost} EMC without producing extra items")

    def mapper_guard_document(self):
        return {"comment": "Generated by scripts/emc.py from emc/policy.json. A Forge ProjectE mapper may add a "
                           "machine recipe only when every ingredient and the output are items in this table and "
                           "the integer cost per output is at least the output's value. Excluded items and items "
                           "worth less than one EMC are absent, so a mapper cannot price them.",
                "values": self.mapper_guard}

    # ------------------------------------------------------------------------- outputs

    def final(self, system, item):
        value = self.results[system].get(item)
        return int(value) if value is not None and value >= 1 else None

    def values_document(self):
        items = {}
        for item in self.items:
            if item in self.excluded_by:
                items[item] = {"excluded": reason_of(self.policy["exclude"][self.excluded_by[item]])}
                continue
            entry = {}
            for system in SYSTEMS:
                value = self.final(system, item)
                below_one = self.results[system].get(item)
                if value is not None:
                    entry[system] = value
                elif below_one is not None and 0 < below_one < 1:
                    entry[system] = 0
                    entry.setdefault("below_one", {})[system] = str(below_one)
                elif system == "projecte" and item in self.projecte_unvalued:
                    entry[system] = 0
                    entry.setdefault("no_value", {})[system] = self.projecte_unvalued[item]
                else:
                    self.problems.append(f"{item} resolves to no {system} value; give it an explicit value or exclude it")
            entry["source"] = self.source_of(item)
            items[item] = entry
        document = {"namespace": self.namespace,
                    "generator": "scripts/emc.py from emc/policy.json and emc/registry.txt",
                    "ignored": self.ignored, "items": items}
        vanilla = {item: {system: self.vanilla[system][item] for system in SYSTEMS if item in self.vanilla[system]}
                   for item in self.policy.get("vanilla", {})}
        if vanilla:
            document["vanilla"] = vanilla
        return document

    def source_of(self, item):
        if item in self.explicit_by:
            return "explicit"
        tag = self.sources["projecte"].get(item) or self.sources["itemalchemy"].get(item)
        if tag:
            return tag
        names = sorted({name for name, output, _count, _ingredients in self.policy_conversions if output == item})
        return f"conversion {', '.join(names)}" if names else "recipe"

    def _namespace_of(self, item):
        for namespace in self.namespaces:
            if item.startswith(namespace + ":"):
                return namespace
        return None

    def projecte_document(self, namespace):
        before = {}
        for item in self.items:
            # A blacklisted item's before value would race the ore blacklist mappers, so it is left out.
            if (self._namespace_of(item) == namespace and self.explicit_by.get(item)
                    and item not in self.projecte_blacklisted):
                before[item] = int(self.results["projecte"][item])
        if namespace == self.namespace:
            before.update(self.vanilla["projecte"])
        conversion = [{"output": item, "ingredients": [NO_EMC]} for item in self.items
                      if item in self.excluded_by and self._namespace_of(item) == namespace]
        groups = {}
        for spec in self.policy.get("conversions", []):
            entries = []
            for name, output, count, ingredients in self.policy_conversions:
                if name == spec["name"] and self._namespace_of(output) == namespace:
                    entry = {"output": output}
                    if count != 1:
                        entry["count"] = count
                    written = {}
                    for ingredient, amount in ingredients.items():
                        key = self.projecte_tag_items.get(ingredient[1:], ingredient) \
                            if ingredient.startswith("#") else ingredient
                        written[key] = written.get(key, 0) + amount
                    entry["ingredients"] = written
                    entries.append(entry)
            if entries:
                groups[f"{namespace}_{spec['name']}"] = {"comment": spec["why"], "conversions": entries}
        vanilla = (" (plus vanilla items ProjectE's defaults leave unvalued, which its recipes need)"
                   if namespace == self.namespace and self.vanilla["projecte"] else "")
        document = {"comment": f"Generated by scripts/emc.py from mods/<port>/emc/policy.json. Explicit values for "
                               f"uncraftable {namespace} items{vanilla} and conversions for machine steps ProjectE "
                               f"cannot see; the {NO_EMC} conversion never resolves, so excluded items get no EMC. "
                               f"Everything else is derived from recipes by ProjectE."}
        if groups:
            document["groups"] = groups
        document["values"] = {"before": before, "conversion": conversion}
        return document

    def itemalchemy_document(self, namespace):
        document = {}
        for item in self.items:
            if self._namespace_of(item) == namespace:
                document[item] = 0 if item in self.excluded_by else self.final("itemalchemy", item) or 0
        if namespace == self.namespace:
            document.update(self.vanilla["itemalchemy"])
        return document

    def readme(self, values):
        items = values["items"]
        counts = {}
        for entry in items.values():
            key = "excluded" if "excluded" in entry else entry["source"].split(" ")[0]
            counts[key] = counts.get(key, 0) + 1
        lines = [f"# EMC values for `{self.namespace}`", ""]
        if len(self.namespaces) > 1:
            others = ", ".join(f"`{namespace}`" for namespace in self.namespaces if namespace != self.namespace)
            lines += [f"This policy also covers {others}. Each namespace's shipped files go to that namespace's output root.", ""]
        lines += ["Generated by `python3 scripts/emc.py generate` from `policy.json` and `registry.txt`; do not edit. "
                  "See [docs/emc.md](../../../docs/emc.md) for the method and the ProjectE/Item Alchemy loader notes.",
                  "",
                  f"{len(items)} registered items: " + ", ".join(f"{n} {k}" for k, n in sorted(counts.items())) + ". "
                  "`recipe` values are derived from crafting, smelting, stonecutting and smithing recipes, `tag` values "
                  "come from the EMC mod's own tag defaults, and `explicit` values are listed below. "
                  "Full per-item values are in `values.json`.",
                  ""]
        notes = self.policy.get("notes")
        if notes:
            lines += [notes if isinstance(notes, str) else "\n".join(notes), ""]
        lines += ["## Explicit values", "", "| Item | ProjectE | Item Alchemy | Why |", "| --- | ---: | ---: | --- |"]
        for key, spec in self.policy.get("values", {}).items():
            matched = [i for i, k in self.explicit_by.items() if k == key]
            pe = sorted({self.final("projecte", i) for i in matched} - {None})
            ia = sorted({self.final("itemalchemy", i) for i in matched} - {None})
            lines.append(f"| {entry_label(key, spec, matched)} | {fmt_range(pe)} | {fmt_range(ia)} | {spec['why']} |")
        if self.policy.get("vanilla"):
            lines += ["", "## Vanilla values filled in", "",
                      "These vanilla items have no value in one EMC mod's defaults. The port ships one so its recipes "
                      "resolve; the value applies pack-wide, and a system's existing default is never overridden.", "",
                      "| Item | ProjectE | Item Alchemy | Why |", "| --- | ---: | ---: | --- |"]
            for item, spec in self.policy["vanilla"].items():
                cells = []
                for system in SYSTEMS:
                    if item in self.vanilla[system]:
                        cells.append(str(self.vanilla[system][item]))
                    else:
                        default = self.inputs[system][3].get(item)
                        cells.append(f"{int(default)} (default)" if default is not None else "none")
                lines.append(f"| `{item}` | {cells[0]} | {cells[1]} | {spec['why']} |")
        if self.policy.get("exclude"):
            lines += ["", "## Excluded (no EMC)", "", "| Item | Why |", "| --- | --- |"]
            for key, entry in self.policy["exclude"].items():
                matched = [i for i, k in self.excluded_by.items() if k == key]
                lines.append(f"| {entry_label(key, entry, matched)} | {reason_of(entry)} |")
        else:
            lines += ["", "## Excluded (no EMC)", "", "None."]
        below = {item: entry["below_one"] for item, entry in items.items() if "below_one" in entry}
        if below:
            lines += ["", "## Worth less than one EMC", "",
                      "ProjectE drops values below one (as it does for vanilla glass panes), so these have no EMC and "
                      "ship as 0 for Item Alchemy; raising them would let their recipes create EMC.", "",
                      "| Item | ProjectE | Item Alchemy |", "| --- | ---: | ---: |"]
            for item, fractions in below.items():
                lines.append(f"| `{item}` | {fractions.get('projecte', items[item].get('projecte'))} | "
                             f"{fractions.get('itemalchemy', items[item].get('itemalchemy'))} |")
        lines += self._projecte_readme()
        lines += self._optional_readme()
        if self.policy.get("conversions"):
            lines += ["", "## Machine conversions", "",
                      "Shipped to ProjectE as custom conversion groups and applied to Item Alchemy values the same way.",
                      "", "| Conversion | Output | Ingredients | Items | Why |", "| --- | --- | --- | ---: | --- |"]
            for spec in self.policy["conversions"]:
                count = spec.get("count", 1)
                output = f"`{spec['output']}`" + (f" ×{count}" if count != 1 else "")
                ingredients = ", ".join(f"`{name}`" + (f" ×{amount}" if amount != 1 else "")
                                        for name, amount in spec["ingredients"].items())
                lines.append(f"| {spec['name']} | {output} | {ingredients} | "
                             f"{self.conversion_counts.get(spec['name'], 0)} | {spec['why']} |")
        if self.policy.get("processes"):
            lines += ["", "## Processes checked for EMC gain", "",
                      "ProjectE cannot see these machine or world processes, so `emc.py check` verifies that no "
                      "output is worth more than its input.", "", "| Process | Checks | Why |", "| --- | ---: | --- |"]
            labels = {"projecte": "ProjectE", "itemalchemy": "Item Alchemy"}
            for process in self.policy["processes"]:
                name = process["name"]
                if set(process.get("systems", SYSTEMS)) != set(SYSTEMS):
                    name += " (" + ", ".join(labels[s] for s in process["systems"]) + " only)"
                lines.append(f"| {name} | {self.process_counts.get(process['name'], 0)} | {process['why']} |")
        if self.mapper_stats is not None:
            stats = self.mapper_stats
            lines += ["", "## ProjectE recipe mappers", "", self.policy["projecte_mappers"]["why"], "",
                      f"The guard lists {stats['guard']} items. Of the item-only machine recipes, "
                      f"{stats['permitted']} cost at least as much as the static value and may be mapped, and "
                      f"{stats['refused']} are refused. Chemical, fluid and sawmill byproduct recipes are refused "
                      "because an ingredient is not an item in the table.", ""]
        if self.ignored:
            lines += ["", "## Ignored registry entries", "", "| Item | Why |", "| --- | --- |"]
            for key, why in self.policy["ignored"].items():
                lines.append(f"| `{key}` | {why} |")
        skipped = sorted(set(self.inputs["projecte"][5].values()) | set(self.inputs["itemalchemy"][5].values()))
        if skipped:
            lines += ["", "Recipe types ProjectE does not map, so items made only by them need a conversion, value or "
                      "exclusion: "
                      + ", ".join(f"`{t}`" for t in skipped) + "."]
        return "\n".join(lines) + "\n"

    def _optional_readme(self):
        """README rows for the optional mods and the recipe alternatives they enable."""
        optional = self.policy.get("optional_mods")
        if not optional:
            return []
        lines = ["", "## Recipes other mods enable", "",
                 "These recipes load only alongside another mod, or accept more items there because that mod fills a "
                 "tag they use. Their ingredients are priced at the values that mod's port ships, and every output "
                 "takes the cheapest of all its recipes, so no recipe gains EMC with or without the mod.", "",
                 "| Mod | Port | Why |", "| --- | --- | --- |"]
        for mod, entry in sorted(optional.items()):
            lines.append(f"| `{mod}` | [`mods/{entry['port']}`](../../{entry['port']}/emc/README.md) | {entry['why']} |")
        rows = {}
        for system in SYSTEMS:
            remainders = self.inputs[system][1]
            _label, standalone, standalone_tags, _priced = self.alternatives[system][0]
            for label, recipes, tags, priced in self.alternatives[system][1:]:
                if not priced:
                    continue
                for rid, recipe in recipes.items():
                    output, _count = result_of(recipe)
                    if output not in self.items:
                        continue
                    if rid not in standalone:
                        how = "loads"
                    elif (conversions_from({rid: recipe}, tags, remainders)
                          != conversions_from({rid: recipe}, standalone_tags, remainders)):
                        how = "wider tags"
                    else:
                        continue
                    rows.setdefault((rid, output), set()).add(f"{how} {label}")
        if rows:
            lines += ["", "| Recipe | Output | Changes | ProjectE | Item Alchemy |", "| --- | --- | --- | ---: | ---: |"]
            for (rid, output), labels in sorted(rows.items()):
                lines.append(f"| `{rid}` | `{output}` | {', '.join(sorted(labels))} | "
                             f"{self.final('projecte', output)} | {self.final('itemalchemy', output)} |")
        return lines

    def _projecte_readme(self):
        """README rows for the values ProjectE's own mappers decide rather than the port's recipes."""
        unvalued = {i: r for i, r in self.projecte_unvalued.items() if i not in self.excluded_by}
        priced = {i: t for i, t in self.projecte_overwritten.items() if self.final("projecte", i)}
        if not (unvalued or priced or self.projecte_tag_items):
            return []
        lines = ["", "## ProjectE mapper effects", "",
                 "ProjectE's own mappers decide these values, so the static table predicts them; the Forge GameTest "
                 "compares every value with the live ProjectE mapping."]
        if unvalued:
            lines += ["", "No ProjectE EMC (`OresBlacklistMapper` and `RawOresBlacklistMapper` zero `#forge:ores` and "
                      "`#forge:raw_materials`):", "", "| Item | Item Alchemy | Why |", "| --- | ---: | --- |"]
            for item, reason in unvalued.items():
                lines.append(f"| `{item}` | {self.final('itemalchemy', item) or 0} | {reason} |")
        if priced:
            lines += ["", "Priced by ProjectE's `metals.json` tag conversions:", "",
                      "| Item | ProjectE | Tag |", "| --- | ---: | --- |"]
            for item, tag in sorted(priced.items()):
                lines.append(f"| `{item}` | {self.final('projecte', item)} | `#{tag}` |")
        if self.projecte_tag_items:
            lines += ["", "Tags the shipped ProjectE conversions name as one item:", "",
                      "| Tag | Item | Why |", "| --- | --- | --- |"]
            for tag, item in sorted(self.projecte_tag_items.items()):
                lines.append(f"| `#{tag}` | `{item}` | {self.projecte_tag_reasons.get(tag, '')} |")
        return lines

    def output_root(self, namespace):
        mapped = self.policy.get("outputs")
        relative = mapped[namespace] if mapped else self.policy["output"]
        return self.dir / relative

    def outputs(self):
        self.compute()
        values = self.values_document()
        written = {
            self.dir / "emc" / "values.json": dumps(values),
            self.dir / "emc" / "README.md": self.readme(values),
        }
        for namespace in self.namespaces:
            data = self.output_root(namespace) / "data" / namespace
            written[data / "pe_custom_conversions" / f"{namespace}.json"] = dumps(self.projecte_document(namespace))
            written[data / "default_emcs" / f"{namespace}.json"] = dumps(self.itemalchemy_document(namespace))
        if self.policy.get("projecte_mappers"):
            written[self.dir / self.policy["projecte_mappers"]["guard"]] = dumps(self.mapper_guard_document())
        return written


def entry_label(key, entry, matched):
    if isinstance(entry, dict) and "items" in entry:
        return f"{key} ({len(matched)} items: " + ", ".join(f"`{i}`" for i in entry["items"]) + ")"
    return f"`{key}`" + (f" ({len(matched)} items)" if "*" in key else "")


def fmt_range(values):
    if not values:
        return "none"
    return str(values[0]) if len(values) == 1 else f"{values[0]}–{values[-1]}"


# ----------------------------------------------------------------------------- validation


def validate_projecte(document, known):
    """Strict check of ProjectE 1.1.0's CustomConversionFile grammar."""
    problems = []
    if not isinstance(document, dict) or set(document) - {"comment", "replace", "groups", "values"}:
        return ["top level must be an object with comment, replace, groups or values"]
    values = document.get("values", {})
    if set(values) - {"before", "after", "conversion"}:
        problems.append("values may only contain before, after and conversion")

    def check_nss(name):
        if not isinstance(name, str) or not NSS.match(name):
            problems.append(f"invalid ingredient or output {name!r}")
        elif ITEM_ID.match(name) and name not in known:
            problems.append(f"unknown item {name}")

    for section in ("before", "after"):
        for name, value in values.get(section, {}).items():
            check_nss(name)
            if not (value == "free" or (isinstance(value, int) and not isinstance(value, bool) and value >= 1)):
                problems.append(f"{section}.{name}: EMC value must be at least one or \"free\"")
    conversions = list(values.get("conversion", []))
    groups = document.get("groups", {})
    if not isinstance(groups, dict):
        problems.append("groups must be an object")
        groups = {}
    for name, group in groups.items():
        if not isinstance(group, dict) or set(group) - {"comment", "conversions"} or not group.get("conversions"):
            problems.append(f"group {name} must hold an optional comment and a non-empty conversions list")
            continue
        conversions += group["conversions"]
    for conversion in conversions:
        if set(conversion) - {"output", "count", "ingredients", "propagateTags"}:
            problems.append(f"conversion has unknown keys: {conversion}")
        check_nss(conversion.get("output"))
        if int(conversion.get("count", 1)) < 1:
            problems.append("conversion count must be at least one")
        ingredients = conversion.get("ingredients")
        if isinstance(ingredients, dict):
            for name, amount in ingredients.items():
                check_nss(name)
                if not isinstance(amount, int) or amount == 0:
                    problems.append(f"ingredient {name} needs a nonzero integer amount")
        elif isinstance(ingredients, list) and ingredients:
            for name in ingredients:
                check_nss(name)
        else:
            problems.append(f"conversion for {conversion.get('output')} needs ingredients")
    return problems


def validate_itemalchemy(document, known):
    problems = []
    for name, value in document.items():
        if name not in known:
            problems.append(f"unknown item {name}")
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            problems.append(f"{name}: Item Alchemy values must be integers of at least zero")
    return problems


# ----------------------------------------------------------------------------- commands


def discover(names):
    found = sorted(p.parent.parent for p in (ROOT / "mods").glob("*/emc/policy.json"))
    if names:
        chosen = [p for p in found if p.name in names]
        missing = set(names) - {p.name for p in chosen}
        if missing:
            raise SystemExit(f"no emc/policy.json for: {', '.join(sorted(missing))}")
        found = chosen
    # A port prices its optional mods with their ports' generated values, so those ports go first.
    ordered, visiting = [], set()

    def visit(directory):
        if directory in ordered or directory in visiting:
            return
        visiting.add(directory)
        policy = read_json(directory / "emc" / "policy.json")
        for entry in policy.get("optional_mods", {}).values():
            dependency = directory.parent / entry.get("port", "")
            if dependency in found:
                visit(dependency)
        ordered.append(directory)

    for directory in found:
        visit(directory)
    return ordered


def build(directory):
    port = Port(directory)
    outputs = port.outputs()
    known = set(port.items) | set(port.policy.get("vanilla", {}))
    known |= {i for system in SYSTEMS for i in read_json(BASELINES / f"{system}-vanilla.json")}
    for path, text in outputs.items():
        document = json.loads(text) if path.suffix == ".json" else None
        if path.parent.name == "pe_custom_conversions":
            port.problems += [f"{path.name}: {p}" for p in validate_projecte(document, known)]
        elif path.parent.name == "default_emcs":
            port.problems += [f"{path.name}: {p}" for p in validate_itemalchemy(document, known)]
    return port, outputs


def command_generate(args):
    failed = False
    for directory in discover(args.ports):
        port, outputs = build(directory)
        for path, text in outputs.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        failed |= report(port)
    return 1 if failed else 0


def command_check(args):
    failed = False
    for directory in discover(args.ports):
        port, outputs = build(directory)
        for path, text in outputs.items():
            if not path.is_file() or path.read_text(encoding="utf-8") != text:
                port.problems.append(f"{path.relative_to(ROOT)} is stale; run python3 scripts/emc.py generate {port.dir.name}")
        failed |= report(port)
    return 1 if failed else 0


def report(port):
    for problem in port.problems:
        print(f"{port.dir.name}: {problem}", file=sys.stderr)
    status = "FAILED" if port.problems else "ok"
    print(f"{port.dir.name}: {len(port.items)} items, {len(port.excluded_by)} excluded, "
          f"{len(port.explicit_by)} explicit: {status}")
    return bool(port.problems)


def command_merge(args):
    """Write every port's Item Alchemy values into an existing emc_config.json (Tekxit ships one)."""
    config_path = Path(args.config)
    config = read_json(config_path)
    changed = 0
    for directory in discover(args.ports):
        values = read_json(directory / "emc" / "values.json")
        for item, entry in values["items"].items():
            value = 0 if "excluded" in entry else entry["itemalchemy"]
            if config.get(item) != value:
                config[item] = value
                changed += 1
        for item, entry in values.get("vanilla", {}).items():
            if "itemalchemy" in entry and item not in config:
                config[item] = entry["itemalchemy"]
                changed += 1
    config_path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    print(f"{config_path}: {changed} entries updated; restart the server or run /itemalchemy reloademc")
    return 0


def projecte_tag_conversions(document):
    """ProjectE ``values.conversion`` entries whose output is a tag they propagate to every member.

    Returned as ``{"output": tag, "count": n, "ingredients": {tag: amount}}`` without the ``#``. ProjectE 1.1.0's
    metals.json uses these to price other mods' ingots (tin as one iron ingot, lead as two, and so on).
    """
    found = []
    for conversion in document.get("values", {}).get("conversion", []):
        output = conversion.get("output", "")
        ingredients = conversion.get("ingredients")
        if isinstance(ingredients, list):
            counted = {}
            for name in ingredients:
                counted[name] = counted.get(name, 0) + 1
            ingredients = counted
        if not output.startswith("#") or not conversion.get("propagateTags") or not isinstance(ingredients, dict) \
                or not all(name.startswith("#") for name in ingredients):
            raise PolicyError(f"unsupported ProjectE default conversion {conversion}")
        entry = {"output": output[1:]}
        if conversion.get("count", 1) != 1:
            entry["count"] = conversion["count"]
        entry["ingredients"] = {name[1:]: amount for name, amount in ingredients.items()}
        found.append(entry)
    return found


def read_tag_files(jar, namespace):
    tags = {}
    prefix = f"data/{namespace}/tags/items/"
    with zipfile.ZipFile(jar) as archive:
        for name in sorted(archive.namelist()):
            if name.startswith(prefix) and name.endswith(".json"):
                content = json.loads(archive.read(name))
                entries = [e if isinstance(e, str) else e["id"] for e in content.get("values", [])]
                tags[f"{namespace}:{name[len(prefix):-5]}"] = entries
    return tags


def with_fractions(values, minecraft_jar, loader):
    """Add the sub-1 vanilla values that EMC dumps omit (ProjectE floors every value of one or more)."""
    tags = Tags.for_loader(loader)
    recipes = {}
    with zipfile.ZipFile(minecraft_jar) as archive:
        for name in archive.namelist():
            if name.startswith("data/minecraft/recipes/") and name.endswith(".json"):
                recipe = json.loads(archive.read(name))
                if recipe_ingredients(recipe) is not None:
                    recipes[name] = recipe
    fixed = {k: Fraction(v) for k, v in values.items()}
    resolved = resolve(conversions_from(recipes, tags, REMAINDERS), fixed, set(), REMAINDERS)
    result = dict(values)
    for item, value in resolved.items():
        if item not in result and value < 1:
            result[item] = str(value)
    return dict(sorted(result.items()))


def command_refresh(args):
    """Rebuild the vendored baselines from the original jars and EMC dumps."""
    BASELINES.mkdir(parents=True, exist_ok=True)
    if (args.projecte_dump or args.itemalchemy_config) and not args.minecraft_jar:
        raise SystemExit("--minecraft-jar is required to add fractional vanilla values")
    if args.minecraft_jar:
        (BASELINES / "tags-minecraft.json").write_text(dumps(read_tag_files(args.minecraft_jar, "minecraft")))
        used = set()
        with zipfile.ZipFile(args.minecraft_jar) as archive:
            for name in archive.namelist():
                if name.startswith("data/minecraft/recipes/") and name.endswith(".json"):
                    used |= set(re.findall(r'"tag":\s*"([^"]+)"', archive.read(name).decode()))
        (BASELINES / "vanilla-recipe-tags.json").write_text(dumps(sorted(used)))
    if args.forge_jar:
        (BASELINES / "tags-forge.json").write_text(dumps(read_tag_files(args.forge_jar, "forge")))
    if args.convention_jar:
        (BASELINES / "tags-c.json").write_text(dumps(read_tag_files(args.convention_jar, "c")))
    if args.projecte_jar:
        values, conversions, created = {}, [], set()
        with zipfile.ZipFile(args.projecte_jar) as archive:
            for name in ("defaults", "metals"):
                text = archive.read(f"data/projecte/pe_custom_conversions/{name}.json").decode()
                document = json.loads(text)
                for key, value in document.get("values", {}).get("before", {}).items():
                    if key.startswith("#"):
                        values[key[1:]] = value
                conversions += projecte_tag_conversions(document)
                created |= set(re.findall(r'"#([a-z0-9_.-]+:[a-z0-9_./-]+)"', text))
        (BASELINES / "projecte-tag-values.json").write_text(dumps(dict(sorted(values.items()))))
        (BASELINES / "projecte-tag-conversions.json").write_text(dumps(conversions))
        (BASELINES / "projecte-tags.json").write_text(dumps(sorted(created)))
    if args.projecte_dump:
        dump = read_json(args.projecte_dump)
        vanilla = {k: v for k, v in dump.items() if k.startswith("minecraft:") and "{" not in k}
        (BASELINES / "projecte-vanilla.json").write_text(dumps(with_fractions(vanilla, args.minecraft_jar, "forge")))
    if args.itemalchemy_config:
        merged = {}
        for path in reversed(args.itemalchemy_config):
            merged.update({k: v for k, v in read_json(path).items() if k.startswith("minecraft:") and v > 0})
        (BASELINES / "itemalchemy-vanilla.json").write_text(dumps(with_fractions(merged, args.minecraft_jar, "fabric")))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    for name, function, text in (("generate", command_generate, "write values, docs and shipped data"),
                                 ("check", command_check, "fail if anything is unresolved, exploitable or stale")):
        sub = commands.add_parser(name, help=text)
        sub.add_argument("ports", nargs="*", help="port directory names (default: every port with emc/policy.json)")
        sub.set_defaults(function=function)
    merge = commands.add_parser("merge-itemalchemy", help="write port values into an Item Alchemy emc_config.json")
    merge.add_argument("config")
    merge.add_argument("ports", nargs="*")
    merge.set_defaults(function=command_merge)
    refresh = commands.add_parser("refresh-baselines", help="rebuild scripts/emc from jars and EMC dumps")
    refresh.add_argument("--minecraft-jar")
    refresh.add_argument("--forge-jar")
    refresh.add_argument("--convention-jar")
    refresh.add_argument("--projecte-jar")
    refresh.add_argument("--projecte-dump", help="config/ProjectE/pregenerated_emc.json")
    refresh.add_argument("--itemalchemy-config", nargs="+",
                         help="emc_config.json files, highest priority first (e.g. Tekxit's, then a fresh one)")
    refresh.set_defaults(function=command_refresh)
    args = parser.parse_args(argv)
    try:
        return args.function(args)
    except PolicyError as error:
        print(f"policy error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
