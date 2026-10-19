#!/usr/bin/env python3
"""Generate the standalone 1.19.2 nest/trackway assets and feature data.

Translations and block-tag values are written as mergeable fragments under
tools/ so this generator never replaces resources owned by other subsystems.
"""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
DATA = RES / "data/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main/resources/assets/jurassicraft"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def copy_texture(name):
    destination = ASSETS / f"textures/blocks/{name}.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UPSTREAM / f"textures/blocks/{name}.png", destination)


def resource_for(name, model, loot):
    write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"jurassicraft:block/{model}"}}})
    write(ASSETS / f"models/item/{name}.json", {"parent": f"jurassicraft:block/{model}"})
    write(DATA / f"loot_tables/blocks/{name}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"jurassicraft:{name}"}], "conditions": loot}],
    })


copy_texture("nest_fossil_side")
copy_texture("fossilized_trackway")
names = {}
blocks = []
nest_blocks = []
silk = {"condition": "minecraft:match_tool", "predicate": {"enchantments": [
    {"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}
survives = {"condition": "minecraft:survives_explosion"}

write(ASSETS / "models/block/fossil_nest_encased.json", {
    "parent": "minecraft:block/cube_all", "textures": {"all": "jurassicraft:blocks/encased_fossil"},
})
for index in range(1, 4):
    raw = "nest_fossil" if index == 1 else f"nest_fossil_{index}"
    encased = "encased_nest_fossil" if index == 1 else f"encased_nest_fossil_{index}"
    model = f"fossil_nest_{index}"
    texture = f"nest_fossil_top_{index}"
    copy_texture(texture)
    write(ASSETS / f"models/block/{model}.json", {
        "parent": "minecraft:block/cube_top",
        "textures": {"top": f"jurassicraft:blocks/{texture}", "side": "jurassicraft:blocks/nest_fossil_side"},
    })
    resource_for(raw, model, [silk])
    resource_for(encased, "fossil_nest_encased", [survives])
    names[f"block.jurassicraft.{raw}"] = "Fossil Nest"
    names[f"block.jurassicraft.{encased}"] = "Encased Fossil Nest"
    blocks.extend((raw, encased))
    nest_blocks.extend((raw, encased))

for variant in ("biped_medium", "biped_small", "raptor"):
    name = f"fossilized_trackway_{variant}"
    copy_texture(name)
    write(ASSETS / f"models/block/{name}.json", {
        "parent": "minecraft:block/cube_top",
        "textures": {"top": f"jurassicraft:blocks/{name}", "side": "jurassicraft:blocks/fossilized_trackway"},
    })
    states = {}
    for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        entry = {"model": f"jurassicraft:block/{name}"}
        if rotation:
            entry["y"] = rotation
        states[f"facing={facing}"] = entry
    write(ASSETS / f"blockstates/{name}.json", {"variants": states})
    write(ASSETS / f"models/item/{name}.json", {"parent": f"jurassicraft:block/{name}"})
    write(DATA / f"loot_tables/blocks/{name}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"jurassicraft:{name}"}], "conditions": [survives]}],
    })
    names[f"block.jurassicraft.{name}"] = "Fossilized Trackway"
    blocks.append(name)

write(ROOT / "tools/lang/fossil_nests.json", names)
write(ROOT / "tools/tags/fossil_nests_pickaxe.json", {"replace": False, "values": [f"jurassicraft:{name}" for name in blocks]})
write(ROOT / "tools/tags/fossil_nests_needs_stone_tool.json", {"replace": False, "values": [f"jurassicraft:{name}" for name in nest_blocks]})

write(DATA / "worldgen/configured_feature/fossil_nest.json", {"type": "jurassicraft:fossil_nest", "config": {}})
write(DATA / "worldgen/placed_feature/fossil_nest.json", {
    "feature": "jurassicraft:fossil_nest",
    "placement": [
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
            "min_inclusive": {"absolute": 30}, "max_inclusive": {"absolute": 49}}},
        {"type": "minecraft:biome"},
    ],
})
write(DATA / "forge/biome_modifier/fossil_nests.json", {
    "type": "forge:add_features", "biomes": "#minecraft:is_overworld",
    "features": ["jurassicraft:fossil_nest"], "step": "underground_ores",
})

# Restrict destructive generation to natural ground and the new trackways.
# Modpacks can add their own natural terrain blocks to this tag.
write(DATA / "tags/blocks/fossil_nest_replaceable.json", {"replace": False, "values": [
    "#minecraft:base_stone_overworld", "#minecraft:dirt", "#minecraft:sand", "#minecraft:terracotta", "#forge:ores",
    "minecraft:gravel", "minecraft:clay", "minecraft:sandstone", "minecraft:red_sandstone",
    "jurassicraft:fossilized_trackway_biped_medium", "jurassicraft:fossilized_trackway_biped_small",
    "jurassicraft:fossilized_trackway_raptor",
]})
