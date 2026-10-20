#!/usr/bin/env python3
"""Writes the 1.19.2 builder rules from upstream BuildCraft's 1.12 vanilla rule files.

Upstream ships `assets/buildcraftbuilders/compat/buildcraft/builders/vanilla/*.json`, written for 1.12 block
and item ids with metadata. This script restates each of those rules for the flattened 1.19.2 ids, extends
the block families upstream listed with their 1.19.2 members (every slab, door, sign, bed, banner, sapling and
so on), and adds `port_additions.json` for blocks whose cost upstream did not cover. Rule files for
BuildCraft's own blocks are not written: blueprints never record BuildCraft blocks.

Families are read from Minecraft's block state files, so pass the folder extracted from the 1.19.2 client:

    unzip -q <gradle cache>/forge_gradle/minecraft_repo/versions/1.19.2/client-extra.jar \\
        'assets/minecraft/blockstates/*' -d /tmp/mc1192
    python3 mods/buildcraft/tools/generate_builder_rules.py /tmp/mc1192/assets/minecraft/blockstates
"""

import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/buildcraft/compat/buildcraft/builders"

COLOURS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
           "purple", "blue", "brown", "green", "red", "black"]
HORIZONTAL = {"north": [0, 0, 1], "south": [0, 0, -1], "west": [1, 0, 0], "east": [-1, 0, 0]}
FLOWERS = ["dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
           "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose"]
DOUBLE_PLANTS = ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"]
RAILS = ["rail", "activator_rail", "detector_rail", "powered_rail"]
UPSTREAM_ORDER = ["double_slabs", "flower_pot", "blocks_simple_inventories", "ignored", "snow_layer", "farmland",
                  "special_block_items", "crops", "redstone_wire", "multiple_variants", "observer", "doors", "hopper",
                  "daylight_detector", "dispenser", "block_below_required", "opens", "piston", "rails", "two_parts",
                  "facings", "painting", "item_frame", "armor_stand", "ender_crystal", "boats", "minecarts", "cauldron"]


class Blocks:
    """Blocks drawn by block entity renderers list no properties in their state files; the GameTests check those."""

    def __init__(self, folder):
        self.states = {}
        for file in sorted(Path(folder).glob("*.json")):
            data = json.loads(file.read_text())
            values = {}
            for key in data.get("variants", {}):
                for pair in filter(None, key.split(",")):
                    name, value = pair.split("=")
                    values.setdefault(name, set()).add(value)
            for part in data.get("multipart", []):
                for clause in part.get("when", {}).get("OR", [part.get("when", {})]):
                    for name, value in clause.items():
                        values.setdefault(name, set()).update(str(value).lower().split("|"))
            self.states[file.stem] = values
        if "oak_slab" not in self.states:
            sys.exit(f"{folder} does not look like Minecraft's blockstates folder")

    def ending(self, suffix, exclude=()):
        return [name for name in self.states if name.endswith(suffix) and name not in exclude]

    def selector(self, name, **properties):
        if name not in self.states:
            sys.exit(f"No block minecraft:{name}")
        known = self.states[name]
        for prop, value in properties.items():
            if known and str(value) not in known.get(prop, set()):
                sys.exit(f"minecraft:{name} has no {prop}={value}")
        if not properties:
            return f"minecraft:{name}"
        return f"minecraft:{name}[" + ", ".join(f"{prop}={value}" for prop, value in properties.items()) + "]"

    def selectors(self, names, **properties):
        return [self.selector(name, **properties) for name in names]


def items(*entries):
    return {"type": "constant", "items": [entry if isinstance(entry, dict) else {"item": f"minecraft:{entry}"} for entry in entries]}


def item(name, amount=1, tag=None):
    entry = {"item": f"minecraft:{name}", "amount": amount}
    if tag is not None:
        entry["tagCompound"] = tag
    return entry


def generate(blocks):
    b = blocks
    slabs = b.ending("_slab")
    doors = b.ending("_door")
    wooden_doors = [door for door in doors if door != "iron_door"]
    trapdoors = b.ending("_trapdoor")
    signs = b.ending("_sign", exclude=b.ending("_wall_sign"))
    wall_signs = b.ending("_wall_sign")
    beds = [f"{colour}_bed" for colour in COLOURS]
    banners = [f"{colour}_banner" for colour in COLOURS]
    wall_banners = [f"{colour}_wall_banner" for colour in COLOURS]
    shulker_boxes = ["shulker_box"] + [f"{colour}_shulker_box" for colour in COLOURS]
    saplings = b.ending("_sapling", exclude=["bamboo_sapling"])
    potted = [name for name in b.states if name.startswith("potted_")]
    cauldrons = ["cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron"]
    crops = ["wheat", "pumpkin_stem", "melon_stem", "beetroots", "carrots", "potatoes", "sugar_cane"]
    candles = ["candle"] + [f"{colour}_candle" for colour in COLOURS]

    files = {}
    files["double_slabs"] = [
        {"selectors": [b.selector(slab, type="double")], "requiredExtractors": [items(item(slab, 2))]} for slab in slabs]

    def potted_plant(pot):
        return {"azalea_bush": "azalea", "flowering_azalea_bush": "flowering_azalea"}.get(pot[len("potted_"):], pot[len("potted_"):])

    files["flower_pot"] = [{"selectors": b.selectors(["flower_pot"] + potted), "requiredExtractors": [items("flower_pot")]}] + [
        {"selectors": [b.selector(pot)], "requiredExtractors": [items(potted_plant(pot))]} for pot in potted]
    files["blocks_simple_inventories"] = [{
        "selectors": b.selectors(["chest", "trapped_chest", "brewing_stand", "dispenser", "dropper", "furnace", "hopper"]
                                 + shulker_boxes + ["barrel", "smoker", "blast_furnace"]),
        "requiredExtractors": [{"type": "item_from_block"}, {"type": "items_list", "path": ["Items"]}]}]
    files["ignored"] = [{"selectors": b.selectors(["fire", "soul_fire", "ender_chest"]), "ignore": True}]
    files["snow_layer"] = [
        {"selectors": [b.selector("snow", layers=layers)], "requiredExtractors": [items(item("snow", layers))]} for layers in range(1, 9)]
    files["farmland"] = [{"selectors": [b.selector("farmland")], "requiredExtractors": [items("dirt")], "ignoredProperties": ["moisture"]}]
    files["special_block_items"] = [
        {"selectors": [b.selector("cake")], "requiredExtractors": [items("cake")]},
        {"selectors": [b.selector("wheat")], "requiredExtractors": [items("wheat_seeds")]},
        {"selectors": b.selectors(["pumpkin_stem", "attached_pumpkin_stem"]), "requiredExtractors": [items("pumpkin_seeds")]},
        {"selectors": b.selectors(["melon_stem", "attached_melon_stem"]), "requiredExtractors": [items("melon_seeds")]},
        {"selectors": [b.selector("beetroots")], "requiredExtractors": [items("beetroot_seeds")]},
        {"selectors": [b.selector("carrots")], "requiredExtractors": [items("carrot")]},
        {"selectors": [b.selector("potatoes")], "requiredExtractors": [items("potato")]},
        {"selectors": [b.selector("sugar_cane")], "requiredExtractors": [items("sugar_cane")]},
    ] + [
        {"selectors": b.selectors([sign, sign.replace("_sign", "_wall_sign")]), "requiredExtractors": [items(sign)]} for sign in signs
    ] + [
        {"selectors": [b.selector("tall_grass", half="lower")], "requiredExtractors": [items("tall_grass")]},
        {"selectors": [b.selector("grass")], "requiredExtractors": [items("grass")]},
        {"selectors": [b.selector("fern")], "requiredExtractors": [items("fern")]},
        {"selectors": b.selectors(cauldrons), "requiredExtractors": [items("cauldron")]},
    ]
    files["crops"] = [{"selectors": b.selectors(crops), "ignoredProperties": ["age"]}]
    files["redstone_wire"] = [{"selectors": [b.selector("redstone_wire")], "requiredExtractors": [items("redstone")],
                               "ignoredProperties": ["power", "east", "north", "south", "west"]}]
    files["multiple_variants"] = [
        {"selectors": [b.selector("repeater")], "requiredExtractors": [items("repeater")], "ignoredProperties": ["powered"]},
        {"selectors": [b.selector("comparator")], "requiredExtractors": [items("comparator")], "ignoredProperties": ["powered"]},
        {"selectors": b.selectors(["redstone_ore", "deepslate_redstone_ore"]), "ignoredProperties": ["lit"]},
        {"selectors": b.selectors(["redstone_torch", "redstone_wall_torch"]), "requiredExtractors": [items("redstone_torch")],
         "ignoredProperties": ["lit"]},
        {"selectors": [b.selector("redstone_lamp")], "requiredExtractors": [items("redstone_lamp")], "ignoredProperties": ["lit"]},
        {"selectors": b.selectors(["furnace", "smoker", "blast_furnace"]), "ignoredProperties": ["lit"]},
    ]
    files["observer"] = [{"selectors": [b.selector("observer")], "ignoredProperties": ["powered"]}]
    files["doors"] = [{"selectors": b.selectors(doors), "ignoredProperties": ["powered", "open", "hinge"]}]
    files["hopper"] = [{"selectors": [b.selector("hopper")], "ignoredProperties": ["enabled"]}]
    files["daylight_detector"] = [{"selectors": [b.selector("daylight_detector")], "requiredExtractors": [items("daylight_detector")],
                                   "ignoredProperties": ["power"]}]
    files["dispenser"] = [{"selectors": [b.selector("dispenser")], "ignoredProperties": ["triggered"]}]
    files["block_below_required"] = [{
        "selectors": b.selectors(["cactus", "redstone_wire", "tripwire", "wheat", "carrots", "potatoes", "sugar_cane", "melon_stem",
                                  "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem", "dead_bush", "lily_pad", "red_mushroom",
                                  "brown_mushroom", "nether_wart"] + saplings + ["grass", "fern"] + DOUBLE_PLANTS + FLOWERS
                                 + ["beetroots"] + doors + RAILS + beds + signs + banners)
                     + [b.selector("mangrove_propagule", hanging="false")],
        "requiredBlockOffsets": [[0, -1, 0]]}]
    files["opens"] = [{"selectors": b.selectors(["ladder", "tripwire_hook"] + trapdoors), "ignoredProperties": ["open"]}]
    files["piston"] = [
        {"selectors": [b.selector("piston")], "canBeReplacedWithBlocks": ["minecraft:piston", "minecraft:moving_piston"],
         "ignoredProperties": ["extended"]},
        {"selectors": [b.selector("piston_head")], "placeBlock": "minecraft:air",
         "canBeReplacedWithBlocks": ["minecraft:piston_head", "minecraft:moving_piston", "minecraft:air"],
         "ignoredProperties": ["facing", "short", "type"], "requiredExtractors": []},
    ]
    files["rails"] = [{"selectors": b.selectors(RAILS), "ignoredProperties": ["shape", "powered"]}]
    files["two_parts"] = [
        {"selectors": [b.selector(bed, part="foot")], "requiredExtractors": [items(bed)]} for bed in beds
    ] + [
        {"selectors": [b.selector(door, half="lower")], "requiredExtractors": [items(door)]} for door in doors
    ] + [
        {"selectors": [b.selector(bed, part="head") for bed in beds] + [b.selector(door, half="upper") for door in doors]
                      + [b.selector(plant, half="upper") for plant in DOUBLE_PLANTS], "requiredExtractors": []},
        {"selectors": [b.selector(bed, part="foot") for bed in beds] + [b.selector(door, half="lower") for door in doors]
                      + [b.selector(plant, half="lower") for plant in DOUBLE_PLANTS], "updateBlockOffsets": []},
    ]
    wall_attached = ["wall_torch", "redstone_wall_torch", "soul_wall_torch"] + wall_signs + wall_banners
    files["facings"] = [
        {"selectors": b.selectors(["torch", "redstone_torch", "soul_torch"]), "requiredBlockOffsets": [[0, -1, 0]]},
    ] + [
        {"selectors": [b.selector(block, facing=facing) for block in wall_attached], "requiredBlockOffsets": [offset]}
        for facing, offset in HORIZONTAL.items()
    ] + [
        {"selectors": [b.selector("lever", face="ceiling")], "requiredBlockOffsets": [[0, 1, 0]]},
        {"selectors": [b.selector("lever", face="floor")], "requiredBlockOffsets": [[0, -1, 0]]},
    ] + [
        {"selectors": [b.selector("lever", face="wall", facing=facing)], "requiredBlockOffsets": [offset]}
        for facing, offset in HORIZONTAL.items()
    ]
    files["painting"] = [{"selectors": ["minecraft:painting"], "capture": True, "requiredExtractors": [items("painting")]}]
    files["item_frame"] = [
        {"selectors": [f"minecraft:{frame}"], "capture": True, "requiredExtractors": [items(frame), {"type": "item", "path": ["Item"]}]}
        for frame in ["item_frame", "glow_item_frame"]]
    files["armor_stand"] = [{"selectors": ["minecraft:armor_stand"], "capture": True, "requiredExtractors": [
        items("armor_stand"), {"type": "items_list", "path": ["HandItems"]}, {"type": "items_list", "path": ["ArmorItems"]}]}]
    files["ender_crystal"] = [{"selectors": ["minecraft:end_crystal"], "capture": True, "requiredExtractors": [items("end_crystal")]}]
    boat_types = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove"]
    files["boats"] = [
        {"selectors": [{"base": "minecraft:boat", "nbt": [{"key": ["Type"], "operation": "=", "value": wood}]}], "capture": True,
         "requiredExtractors": [items(f"{wood}_boat")]} for wood in boat_types
    ] + [
        {"selectors": [{"base": "minecraft:chest_boat", "nbt": [{"key": ["Type"], "operation": "=", "value": wood}]}], "capture": True,
         "requiredExtractors": [items(f"{wood}_chest_boat"), {"type": "items_list", "path": ["Items"]}]} for wood in boat_types
    ]
    files["minecarts"] = [
        {"selectors": ["minecraft:minecart"], "capture": True, "requiredExtractors": [items("minecart")]},
        {"selectors": ["minecraft:chest_minecart"], "capture": True,
         "requiredExtractors": [items("chest_minecart"), {"type": "items_list", "path": ["Items"]}]},
        {"selectors": ["minecraft:furnace_minecart"], "capture": True, "requiredExtractors": [items("furnace_minecart")]},
        {"selectors": ["minecraft:tnt_minecart"], "capture": True, "requiredExtractors": [items("tnt_minecart")]},
        {"selectors": ["minecraft:hopper_minecart"], "capture": True,
         "requiredExtractors": [items("hopper_minecart"), {"type": "items_list", "path": ["Items"]}]},
    ]
    files["cauldron"] = [
        {"selectors": [b.selector("water_cauldron", level=level)],
         "requiredExtractors": [items(item("potion", level, {"Potion": "minecraft:water"}))]} for level in range(1, 4)
    ] + [
        {"selectors": [b.selector("lava_cauldron")], "requiredExtractors": [items("lava_bucket")]},
        {"selectors": b.selectors(["powder_snow_cauldron"]), "requiredExtractors": [items("powder_snow_bucket")]},
    ]
    files["port_additions"] = [
        {"selectors": [b.selector(candle, candles=count)], "requiredExtractors": [items(item(candle, count))]}
        for candle in candles for count in range(1, 5)
    ] + [
        {"selectors": [b.selector("sea_pickle", pickles=count)], "requiredExtractors": [items(item("sea_pickle", count))]} for count in range(1, 5)
    ] + [
        {"selectors": [b.selector("turtle_egg", eggs=count)], "requiredExtractors": [items(item("turtle_egg", count))]} for count in range(1, 5)
    ] + [
        {"selectors": [b.selector(candle + "_cake")], "requiredExtractors": [items("cake", candle)]} for candle in candles
    ] + [
        {"selectors": b.selectors(["campfire", "soul_campfire"]),
         "requiredExtractors": [{"type": "item_from_block"}, {"type": "items_list", "path": ["Items"]}]},
        {"selectors": [b.selector("lectern")], "requiredExtractors": [{"type": "item_from_block"}, {"type": "item", "path": ["Book"]}]},
        {"selectors": [b.selector("jukebox")], "requiredExtractors": [{"type": "item_from_block"}, {"type": "item", "path": ["RecordItem"]}]},
        {"selectors": b.selectors(["sticky_piston"]), "canBeReplacedWithBlocks": ["minecraft:sticky_piston", "minecraft:moving_piston"],
         "ignoredProperties": ["extended"]},
    ]
    return files


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mc1192/assets/minecraft/blockstates"
    files = generate(Blocks(folder))
    (OUT / "vanilla").mkdir(parents=True, exist_ok=True)
    for stale in (OUT / "vanilla").glob("*.json"):
        stale.unlink()
    order = UPSTREAM_ORDER + ["port_additions"]
    if sorted(order) != sorted(files):
        sys.exit("Rule files and their order disagree")
    for name in order:
        (OUT / "vanilla" / f"{name}.json").write_text(json.dumps(files[name], indent=2) + "\n")
    (OUT / "index.json").write_text(json.dumps([f"vanilla/{name}" for name in order], indent=2) + "\n")
    print(f"Wrote {sum(len(rules) for rules in files.values())} rules in {len(files)} files to {OUT}")


if __name__ == "__main__":
    main()
