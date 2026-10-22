#!/usr/bin/env python3
"""Generate the 1.19.2 data for the first ported resource blocks and items."""
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
DATA = RES / "data/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main/resources/assets/jurassicraft"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def copy_texture(kind, name):
    destination = ASSETS / f"textures/{kind}/{name}.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UPSTREAM / f"textures/{kind}/{name}.png", destination)


blocks = {
    "amber_ore": "Amber Ore",
    "ice_shard": "Ice Shard Ore",
    "gypsum_stone": "Gypsum Stone",
    "gypsum_cobblestone": "Gypsum Cobblestone",
    "gypsum_bricks": "Gypsum Bricks",
    "reinforced_stone": "Reinforced Stone",
    "reinforced_bricks": "Reinforced Bricks",
    "plant_fossil_block": "Plant Fossil Block",
}
items = {
    "amber": ("amber_mosquito", "Mosquito in Amber"),
    "amber_aphid": ("amber_aphid", "Aphid in Amber"),
    "gypsum_powder": ("gypsum_powder", "Gypsum Powder"),
    "sea_lamprey": ("sea_lamprey", "Sea Lamprey"),
    "plant_fossil": ("plant_fossil", "Plant Fossil"),
    "twig_fossil": ("twig_fossil", "Twig Fossil"),
    "plaster_and_bandage": ("plaster_and_bandage", "Plaster and Bandage"),
}

for name in blocks:
    if name == "plant_fossil_block":
        variants = [{"model": f"jurassicraft:block/{name}_{i}"} for i in range(4)]
        for i in range(4):
            texture = f"plant_fossil_{i}"
            copy_texture("blocks", texture)
            write(ASSETS / f"models/block/{name}_{i}.json", {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"jurassicraft:blocks/{texture}"},
            })
        item_parent = f"jurassicraft:block/{name}_0"
    else:
        copy_texture("blocks", name)
        variants = {"model": f"jurassicraft:block/{name}"}
        write(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"jurassicraft:blocks/{name}"},
        })
        item_parent = f"jurassicraft:block/{name}"
    write(ASSETS / f"blockstates/{name}.json", {"variants": {"": variants}})
    write(ASSETS / f"models/item/{name}.json", {"parent": item_parent})

for name, (texture, _) in items.items():
    copy_texture("items", texture)
    write(ASSETS / f"models/item/{name}.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"jurassicraft:items/{texture}"},
    })

translations = {"itemGroup.jurassicraft": "JurassiCraft"}
translations.update({f"block.jurassicraft.{name}": label for name, label in blocks.items()})
translations.update({f"item.jurassicraft.{name}": label for name, (_, label) in items.items()})
write(ASSETS / "lang/en_us.json", translations)

write(RES / "data/minecraft/tags/blocks/mineable/pickaxe.json", {
    "replace": False, "values": [f"jurassicraft:{name}" for name in blocks],
})
write(RES / "data/minecraft/tags/blocks/needs_iron_tool.json", {
    "replace": False, "values": ["jurassicraft:amber_ore", "jurassicraft:ice_shard"],
})


def entry(name, weight=None, functions=None):
    result = {"type": "minecraft:item", "name": name}
    if weight is not None:
        result["weight"] = weight
    if functions:
        result["functions"] = functions
    return result


silk = {
    "condition": "minecraft:match_tool",
    "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]},
}
survives = {"condition": "minecraft:survives_explosion"}


def loot(name, alternatives):
    write(DATA / f"loot_tables/blocks/{name}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:alternatives", "children": [
                {**entry(f"jurassicraft:{name}"), "conditions": [silk]},
                {"type": "minecraft:group", "children": alternatives},
            ]},
        ], "conditions": [survives]}],
    })


for name, kind in (("amber_ore", "amber"), ("gypsum_stone", "gypsum"),
                   ("ice_shard", "ice"), ("plant_fossil_block", "plant")):
    loot(name, [{"type": "jurassicraft:fossil_drops", "kind": kind}])
for name in blocks:
    if name not in {"amber_ore", "gypsum_stone", "ice_shard", "plant_fossil_block"}:
        write(DATA / f"loot_tables/blocks/{name}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry(f"jurassicraft:{name}")], "conditions": [survives]}],
        })


def shaped(name, pattern, key, result, count=1):
    write(DATA / f"recipes/{name}.json", {
        "type": "minecraft:crafting_shaped", "pattern": pattern,
        "key": {letter: {"item": value} for letter, value in key.items()},
        "result": {"item": f"jurassicraft:{result}", "count": count},
    })


shaped("gypsum_stone", ["AAA", "AAA", "AAA"], {"A": "jurassicraft:gypsum_powder"}, "gypsum_stone")
shaped("gypsum_bricks", ["AA", "AA"], {"A": "jurassicraft:gypsum_stone"}, "gypsum_bricks", 4)
shaped("reinforced_stone", ["AAA", "ABA", "AAA"], {"A": "minecraft:stone", "B": "minecraft:iron_ingot"}, "reinforced_stone", 8)
shaped("reinforced_bricks", ["AAA", "ABA", "AAA"], {"A": "minecraft:stone_bricks", "B": "minecraft:iron_ingot"}, "reinforced_bricks", 8)
shaped("reinforced_bricks_from_reinforced_stone", ["AA", "AA"], {"A": "jurassicraft:reinforced_stone"}, "reinforced_bricks", 4)
write(DATA / "recipes/gypsum_stone_from_smelting.json", {
    "type": "minecraft:smelting", "ingredient": {"item": "jurassicraft:gypsum_cobblestone"},
    "result": "jurassicraft:gypsum_stone", "experience": 1.5, "cookingtime": 200,
})
write(DATA / "recipes/plaster_and_bandage.json", {
    "type": "minecraft:crafting_shaped", "pattern": ["ABA", "BCB", "ABA"],
    "key": {"A": {"item": "minecraft:paper"}, "B": {"item": "jurassicraft:gypsum_powder"},
            "C": {"item": "minecraft:white_wool"}},
    "result": {"item": "jurassicraft:plaster_and_bandage", "count": 9},
})

# Preserve the old height ranges and vein parameters; 1.19.2 has deeper terrain.
features = {
    "amber_ore": (3, 8, 0, 19),
    "ice_shard": (1, 8, 0, 63),
    "gypsum_stone": (10, 32, 0, 127),
}
for name, (size, count, low, high) in features.items():
    feature = f"jurassicraft:{name}"
    write(DATA / f"worldgen/configured_feature/{name}.json", {
        "type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": 0.0,
            "targets": [{"target": {"predicate_type": "minecraft:tag_match", "tag": tag},
                         "state": {"Name": feature}} for tag in (
                             "minecraft:stone_ore_replaceables", "minecraft:deepslate_ore_replaceables")]},
    })
    write(DATA / f"worldgen/placed_feature/{name}.json", {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
            "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}},
        {"type": "minecraft:biome"},
    ]})

# Species definitions live in Java so block registration and generated data use
# the same IDs and bone lists. Fail rather than silently producing incomplete data.
species_file = ROOT / "src/main/java/ports/jurassicraft/content/Species.java"
species_pattern = re.compile(r'^\s+([A-Z]+)\((\d+), "([a-z]+)", (.+)\)[,;]$')
species = []
for line in species_file.read_text(encoding="utf-8").splitlines():
    match = species_pattern.match(line)
    if match:
        species.append((match.group(1).lower(), match.group(3), re.findall(r'"([a-z_]+)"', match.group(4))))
if len(species) != 10 or any(not bones for _, _, bones in species):
    raise ValueError("Could not read all registered species and bones")

# Upstream exposed all dinosaur fossil blocks through the fossil ore dictionary.
for kind in ("blocks", "items"):
    write(RES / f"data/forge/tags/{kind}/fossils.json", {
        "replace": False, "values": [f"jurassicraft:{name}_fossil_block" for name, _, _ in species],
    })

for index in range(4):
    copy_texture("blocks", f"fossil_block_{index}")
    write(ASSETS / f"models/block/fossil_deposit_{index}.json", {
        "parent": "minecraft:block/cube_all", "textures": {"all": f"jurassicraft:blocks/fossil_block_{index}"},
    })
copy_texture("blocks", "encased_fossil")
write(ASSETS / "models/block/encased_fossil.json", {
    "parent": "minecraft:block/cube_all", "textures": {"all": "jurassicraft:blocks/encased_fossil"},
})

mineable = [f"jurassicraft:{name}" for name in blocks]
stone_required = []
for name, period, bones in species:
    display = name.capitalize()
    fossil = f"{name}_fossil_block"
    encased = f"{name}_encased_fossil"
    translations[f"block.jurassicraft.{fossil}"] = f"{display} Fossil"
    translations[f"block.jurassicraft.{encased}"] = f"Encased {display} Fossil"
    write(ASSETS / f"blockstates/{fossil}.json", {"variants": {"": [
        {"model": f"jurassicraft:block/fossil_deposit_{index}"} for index in range(4)
    ]}})
    write(ASSETS / f"models/item/{fossil}.json", {"parent": "jurassicraft:block/fossil_deposit_0"})
    write(ASSETS / f"blockstates/{encased}.json", {"variants": {"": {"model": "jurassicraft:block/encased_fossil"}}})
    write(ASSETS / f"models/item/{encased}.json", {"parent": "jurassicraft:block/encased_fossil"})
    write(DATA / f"loot_tables/blocks/{encased}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry(f"jurassicraft:{encased}")], "conditions": [survives]}],
    })
    mineable.extend((f"jurassicraft:{fossil}", f"jurassicraft:{encased}"))
    stone_required.extend((f"jurassicraft:{fossil}", f"jurassicraft:{encased}"))
    for bone in bones:
        item_name = f"{name}_fossil_{bone}"
        texture = f"{name}_{bone}"
        copy_texture("items/bones", texture)
        write(ASSETS / f"models/item/{item_name}.json", {"parent": "minecraft:item/generated",
              "textures": {"layer0": f"jurassicraft:items/bones/{texture}"}})
        translations[f"item.jurassicraft.{item_name}"] = f"{display} Fossil {bone.replace('_', ' ').title()}"

# Dinosaur fossils share one 32-attempt feature; per-species ore features are not regenerated.

mineable.extend(f"jurassicraft:{name}" for name in (
    "cleaning_station", "fossil_grinder", "feeder", "dna_extractor", "dna_sequencer",
    "dna_combinator_hybridizer", "dna_synthesizer", "embryonic_machine",
    "embryo_calcification_machine", "incubator", "cultivate_bottom", "skeleton_display",
    "calamites_log_petrified", "ginkgo_log_petrified", "psaronius_log_petrified",
    "phoenix_log_petrified", "araucaria_log_petrified"))
tag_values = {"pickaxe": mineable, "needs_stone_tool": stone_required,
              "needs_iron_tool": ["jurassicraft:amber_ore", "jurassicraft:ice_shard",
                  *[f"jurassicraft:{name}_log_petrified" for name in ("calamites", "ginkgo", "psaronius", "phoenix", "araucaria")]]}
for fragment in sorted((ROOT / "tools/tags").glob("*.json")):
    for tag in tag_values:
        if fragment.name.endswith(f"_{tag}.json"):
            tag_values[tag].extend(json.loads(fragment.read_text(encoding="utf-8"))["values"])
            break
for tag, values in tag_values.items():
    path = "mineable/pickaxe" if tag == "pickaxe" else tag
    write(RES / f"data/minecraft/tags/blocks/{path}.json", {"replace": False, "values": list(dict.fromkeys(values))})

# The plant generator owns the wood axe tag; add the assembly table after it runs.
axe_path = RES / "data/minecraft/tags/blocks/mineable/axe.json"
axe = json.loads(axe_path.read_text(encoding="utf-8")) if axe_path.exists() else {"replace": False, "values": []}
axe["values"] = list(dict.fromkeys([*axe["values"], "jurassicraft:skeleton_assembly"]))
write(axe_path, axe)
write(RES / "data/minecraft/tags/blocks/mineable/shovel.json", {"replace": False,
      "values": ["jurassicraft:peat", "jurassicraft:peat_moss"]})

fragments = sorted((ROOT / "tools/lang").glob("*.json")) + sorted((ROOT / "tools").glob("*/*.fragment.json"))
for fragment in fragments:
    for key, value in json.loads(fragment.read_text(encoding="utf-8")).items():
        if key in translations and translations[key] != value:
            raise ValueError(f"Conflicting translation {key} in {fragment}")
        translations[key] = value
write(ASSETS / "lang/en_us.json", translations)
write(DATA / "worldgen/configured_feature/fossil_deposits.json", {"type": "jurassicraft:fossil_deposits", "config": {}})
write(DATA / "worldgen/placed_feature/fossil_deposits.json", {"feature": "jurassicraft:fossil_deposits", "placement": []})
write(DATA / "forge/biome_modifier/ores.json", {"type": "forge:add_features", "biomes": "#minecraft:is_overworld",
    "features": [f"jurassicraft:{name}" for name in features], "step": "underground_ores"})
