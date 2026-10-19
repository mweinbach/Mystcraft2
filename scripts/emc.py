#!/usr/bin/env python3
"""Generate and check EMC data for ProjectE 1.1.0 (Forge) and Item Alchemy 1.1.3 (Fabric).

Each port keeps a hand-written ``emc/policy.json`` and the live item registry dumped by its
GameTest (``emc/registry.txt``). This tool resolves every item the way ProjectE's graph mapper
would (recipes, tags, fixed values, minimum-cost propagation) against vanilla baselines from
both mods, then writes ``emc/values.json``, ``emc/README.md`` and the shipped data:
``data/<ns>/pe_custom_conversions/<ns>.json`` and ``data/<ns>/default_emcs/<ns>.json``.
See docs/emc.md.
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
    if kind == "minecraft:crafting_shaped":
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


def load_recipes(roots):
    """Recipes ProjectE maps (vanilla types); other types are reported as skipped."""
    recipes, skipped = {}, {}
    for root in roots:
        data = root / "data"
        if not data.is_dir():
            continue
        for namespace in sorted(p for p in data.iterdir() if p.is_dir()):
            folder = namespace / "recipes"
            for file in sorted(folder.rglob("*.json")):
                rid = f"{namespace.name}:{file.relative_to(folder).with_suffix('').as_posix()}"
                recipe = read_json(file)
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


def validate_policy(policy):
    allowed = {"namespace", "loaders", "resources", "output", "ignored", "values", "exclude",
               "conversions", "processes", "remainders", "notes"}
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
    for key, entry in policy.get("exclude", {}).items():
        if isinstance(entry, dict) and (set(entry) - {"items", "why"} or not isinstance(entry.get("items"), list)
                                        or not entry["items"]):
            raise PolicyError(f"exclude.{key}: give a reason, or an object with items and why")
        if not reason_of(entry):
            raise PolicyError(f"exclude.{key} needs a reason")
    for key, why in policy.get("ignored", {}).items():
        if not isinstance(why, str) or not why:
            raise PolicyError(f"ignored.{key} needs a reason")
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
        if set(process) - {"name", "why", "in", "out"} or not all(process.get(k) for k in ("name", "why", "in", "out")):
            raise PolicyError(f"process {process.get('name')!r}: needs name, why, in and out only")


# ----------------------------------------------------------------------------- the model


class Port:
    def __init__(self, directory):
        self.dir = Path(directory)
        self.policy = read_json(self.dir / "emc" / "policy.json")
        validate_policy(self.policy)
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
        remainders = {**REMAINDERS, **self.policy.get("remainders", {})}
        recipes, skipped = load_recipes(roots)
        return tags, remainders, recipes, skipped

    def compute(self):
        problems = self.problems
        universe = set(self.items)
        owners = assign_patterns({
            "exclude": {key: patterns_of(key, entry) for key, entry in self.policy.get("exclude", {}).items()},
            "values": {key: patterns_of(key, spec) for key, spec in self.policy.get("values", {}).items()},
        }, universe, problems)
        excluded_by = {item: key for item, (label, key) in owners.items() if label == "exclude"}
        explicit_by = {item: key for item, (label, key) in owners.items() if label == "values"}
        self.excluded_by, self.explicit_by = excluded_by, explicit_by
        self.policy_conversions = self._expand_conversions()
        self.results, self.sources, self.inputs = {}, {}, {}
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
            conversions = conversions_from(recipes, tags, remainders)
            for name, output, count, ingredients in self.policy_conversions:
                nodes = {}
                for ingredient, amount in ingredients.items():
                    node = tags.members(ingredient[1:]) if ingredient.startswith("#") else ingredient
                    if isinstance(node, frozenset) and len(node) == 1:
                        node = next(iter(node))
                    nodes[node] = nodes.get(node, 0) + amount
                conversions.append((f"conversion {name}", output, count, nodes))
            values = resolve(conversions, fixed, blocked, remainders)
            self.inputs[system] = (tags, remainders, conversions, baseline, fixed, skipped)
            self.results[system] = values
            self.sources[system] = source
            self._check_system(system)
        self._check_processes()

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
                ours = [i for i in members if i in values and i.startswith(self.namespace + ":")]
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
                for system in SYSTEMS:
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
                else:
                    self.problems.append(f"{item} resolves to no {system} value; give it an explicit value or exclude it")
            entry["source"] = self.source_of(item)
            items[item] = entry
        return {"namespace": self.namespace,
                "generator": "scripts/emc.py from emc/policy.json and emc/registry.txt",
                "ignored": self.ignored, "items": items}

    def source_of(self, item):
        if item in self.explicit_by:
            return "explicit"
        tag = self.sources["projecte"].get(item) or self.sources["itemalchemy"].get(item)
        if tag:
            return tag
        names = sorted({name for name, output, _count, _ingredients in self.policy_conversions if output == item})
        return f"conversion {', '.join(names)}" if names else "recipe"

    def projecte_document(self):
        before = {}
        for item in self.items:
            if self.explicit_by.get(item):
                before[item] = int(self.results["projecte"][item])
        conversion = [{"output": item, "ingredients": [NO_EMC]} for item in self.items if item in self.excluded_by]
        groups = {}
        for spec in self.policy.get("conversions", []):
            entries = []
            for name, output, count, ingredients in self.policy_conversions:
                if name == spec["name"]:
                    entry = {"output": output}
                    if count != 1:
                        entry["count"] = count
                    entry["ingredients"] = ingredients
                    entries.append(entry)
            groups[f"{self.namespace}_{spec['name']}"] = {"comment": spec["why"], "conversions": entries}
        document = {"comment": f"Generated by scripts/emc.py from mods/<port>/emc/policy.json. Explicit values for "
                               f"uncraftable {self.namespace} items and conversions for machine steps ProjectE cannot "
                               f"see; the {NO_EMC} conversion never resolves, so excluded items get no EMC. Everything "
                               f"else is derived from recipes by ProjectE."}
        if groups:
            document["groups"] = groups
        document["values"] = {"before": before, "conversion": conversion}
        return document

    def itemalchemy_document(self):
        document = {}
        for item in self.items:
            document[item] = 0 if item in self.excluded_by else self.final("itemalchemy", item) or 0
        return document

    def readme(self, values):
        items = values["items"]
        counts = {}
        for entry in items.values():
            key = "excluded" if "excluded" in entry else entry["source"].split(" ")[0]
            counts[key] = counts.get(key, 0) + 1
        lines = [f"# EMC values for `{self.namespace}`", "",
                 "Generated by `python3 scripts/emc.py generate` from `policy.json` and `registry.txt`; do not edit. "
                 "See [docs/emc.md](../../../docs/emc.md) for the method and the ProjectE/Item Alchemy loader notes.", "",
                 f"{len(items)} registered items: " + ", ".join(f"{n} {k}" for k, n in sorted(counts.items())) + ". "
                 "`recipe` values are derived from crafting, smelting, stonecutting and smithing recipes, `tag` values "
                 "come from the EMC mod's own tag defaults, and `explicit` values are listed below. "
                 "Full per-item values are in `values.json`.", ""]
        notes = self.policy.get("notes")
        if notes:
            lines += [notes if isinstance(notes, str) else "\n".join(notes), ""]
        lines += ["## Explicit values", "", "| Item | ProjectE | Item Alchemy | Why |", "| --- | ---: | ---: | --- |"]
        for key, spec in self.policy.get("values", {}).items():
            matched = [i for i, k in self.explicit_by.items() if k == key]
            pe = sorted({self.final("projecte", i) for i in matched} - {None})
            ia = sorted({self.final("itemalchemy", i) for i in matched} - {None})
            lines.append(f"| {entry_label(key, spec, matched)} | {fmt_range(pe)} | {fmt_range(ia)} | {spec['why']} |")
        lines += ["", "## Excluded (no EMC)", "", "| Item | Why |", "| --- | --- |"]
        for key, entry in self.policy.get("exclude", {}).items():
            matched = [i for i, k in self.excluded_by.items() if k == key]
            lines.append(f"| {entry_label(key, entry, matched)} | {reason_of(entry)} |")
        below = {item: entry["below_one"] for item, entry in items.items() if "below_one" in entry}
        if below:
            lines += ["", "## Worth less than one EMC", "",
                      "ProjectE drops values below one (as it does for vanilla glass panes), so these have no EMC and "
                      "ship as 0 for Item Alchemy; raising them would let their recipes create EMC.", "",
                      "| Item | ProjectE | Item Alchemy |", "| --- | ---: | ---: |"]
            for item, fractions in below.items():
                lines.append(f"| `{item}` | {fractions.get('projecte', items[item].get('projecte'))} | "
                             f"{fractions.get('itemalchemy', items[item].get('itemalchemy'))} |")
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
            for process in self.policy["processes"]:
                lines.append(f"| {process['name']} | {self.process_counts.get(process['name'], 0)} | {process['why']} |")
        if self.ignored:
            lines += ["", "## Ignored registry entries", "", "| Item | Why |", "| --- | --- |"]
            for key, why in self.policy["ignored"].items():
                lines.append(f"| `{key}` | {why} |")
        skipped = sorted(set(self.inputs["projecte"][5].values()) | set(self.inputs["itemalchemy"][5].values()))
        if skipped:
            lines += ["", "Recipe types not mapped by ProjectE and not declared in `policy.json` `recipes`: "
                      + ", ".join(f"`{t}`" for t in skipped) + "."]
        return "\n".join(lines) + "\n"

    def outputs(self):
        self.compute()
        values = self.values_document()
        output = self.dir / self.policy["output"] / "data" / self.namespace
        return {
            self.dir / "emc" / "values.json": dumps(values),
            self.dir / "emc" / "README.md": self.readme(values),
            output / "pe_custom_conversions" / f"{self.namespace}.json": dumps(self.projecte_document()),
            output / "default_emcs" / f"{self.namespace}.json": dumps(self.itemalchemy_document()),
        }


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
        return chosen
    return found


def build(directory):
    port = Port(directory)
    outputs = port.outputs()
    known = set(port.items) | {i for system in SYSTEMS for i in read_json(BASELINES / f"{system}-vanilla.json")}
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
    config_path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    print(f"{config_path}: {changed} entries updated; restart the server or run /itemalchemy reloademc")
    return 0


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
        values = {}
        with zipfile.ZipFile(args.projecte_jar) as archive:
            for name in ("defaults", "metals"):
                document = json.loads(archive.read(f"data/projecte/pe_custom_conversions/{name}.json"))
                for key, value in document.get("values", {}).get("before", {}).items():
                    if key.startswith("#"):
                        values[key[1:]] = value
        (BASELINES / "projecte-tag-values.json").write_text(dumps(dict(sorted(values.items()))))
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
