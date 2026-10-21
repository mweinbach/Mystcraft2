#!/usr/bin/env python3
"""Generates BuildCraft's 1.19.2 resources from a checkout of the pinned upstream revision.

Textures are copied from upstream ``buildcraft_resources`` (recoloured where upstream recolours
them at runtime); models, blockstates, lang, recipes, tags and loot tables are written from the
upstream definitions listed in ``docs/PARITY.md``. Output is shared by the Forge and Fabric builds.

    python3 mods/buildcraft/tools/generate_assets.py --upstream /path/to/BuildCraft
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image

MOD = "buildcraft"
ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MOD
DATA = RESOURCES / "data"
# Minecraft 1.19.2's own desert and ocean, from its data generator's --reports output.
VANILLA_BIOMES = Path(__file__).resolve().parent / "vanilla" / "worldgen" / "biome"

# Forge reads the chunk layer from models; Fabric registers the same blocks in BCClient.cutoutBlocks().
CUTOUT = {"render_type": "minecraft:cutout"}

DIRECTIONS = ["down", "up", "north", "south", "west", "east"]
OPPOSITE = {"down": "up", "up": "down", "north": "south", "south": "north", "west": "east", "east": "west"}
HORIZONTAL_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
# Rotations for upstream models built facing west.
WEST_Y = {"west": 0, "north": 90, "east": 180, "south": 270}
STAGES = ["blue", "green", "yellow", "red", "overheat", "black"]

# (family, light colour, dark colour, upstream name) from upstream BCEnergyFluids; mirrors BCEnergy.
FLUID_FAMILIES = [
    ("oil", 0x505050, 0x050505, "Oil"),
    ("oil_residue", 0x100F10, 0x421042, "Residue"),
    ("oil_heavy", 0xA08F1F, 0x423520, "Heavy Oil"),
    ("oil_dense", 0x876E77, 0x422424, "Dense Oil"),
    ("oil_distilled", 0xE4AF78, 0xB47F00, "Distilled Oil"),
    ("fuel_dense", 0xFFAF3F, 0xE07F00, "Dense Fuel"),
    ("fuel_mixed_heavy", 0xF2A700, 0xC48700, "Mixed Heavy Fuels"),
    ("fuel_light", 0xFFFF30, 0xE4CF00, "Light Fuel"),
    ("fuel_mixed_light", 0xF6D700, 0xC4B700, "Mixed Light Fuels"),
    ("fuel_gaseous", 0xFAF630, 0xE0D900, "Gaseous Fuel"),
]
# Upstream lang buildcraft.fluid.heat_N, which every BuildCraft fluid name carries.
HEAT_NAMES = ["§bCool§r", "§6Hot§r", "§cSearing§r"]
# (fluid name, heat, light colour, dark colour, display name) for each registered fluid.
FLUIDS = [(family if heat == 0 else f"{family}_heat_{heat}", heat, light, dark, title)
          for family, light, dark, title in FLUID_FAMILIES for heat in range(len(HEAT_NAMES))]
# Upstream ships bucket textures for these two; the others are painted from their fluid.
UPSTREAM_BUCKETS = {"oil": "bucketOil", "fuel_light": "bucketFuel"}

ENGINES = [("redstone_engine", "wood", "Redstone Engine"), ("stirling_engine", "stone", "Stirling Engine"),
           ("combustion_engine", "iron", "Combustion Engine")]
GEARS = [("wood", "Wood Gear"), ("stone", "Stone Gear"), ("iron", "Iron Gear"), ("gold", "Gold Gear"), ("diamond", "Diamond Gear")]

MATERIAL_NAMES = {"wood": "Wooden", "cobblestone": "Cobblestone", "stone": "Stone", "quartz": "Quartz", "gold": "Golden",
                  "sandstone": "Sandstone", "iron": "Iron", "diamond": "Diamond", "clay": "Clay", "void": "Void"}
FLOW_NAMES = {"item": "Transport", "fluid": "Fluid", "power": "Kinesis"}


def pipe(name: str, texture: str | None = None, special: str | None = None, faces: dict[str, str] | None = None,
         item_texture: str | None = None, title: str | None = None) -> dict:
    texture = texture or name
    if title is None:
        material, flow = name.rsplit("_", 1)
        title = f"{MATERIAL_NAMES[material]} {FLOW_NAMES[flow]} Pipe"
    return {"name": name, "faces": faces or {d: texture for d in DIRECTIONS}, "special": special,
            "item": item_texture or texture, "title": title}


# Mirrors ports.buildcraft.transport.BCTransport.
PIPES = [
    pipe("wood_item", "wood_item_clear", "wood_item_filled"),
    pipe("cobblestone_item"), pipe("stone_item"), pipe("quartz_item"), pipe("gold_item"), pipe("sandstone_item"),
    pipe("iron_item", "iron_item_filled", "iron_item_clear"),
    pipe("diamond_item", faces={d: "diamond_item_" + d for d in DIRECTIONS}, item_texture="diamond_item_itemstack"),
    pipe("clay_item"), pipe("void_item"),
    pipe("wood_fluid", "wood_fluid_clear", "wood_fluid_filled"),
    pipe("cobblestone_fluid"), pipe("stone_fluid"), pipe("quartz_fluid"), pipe("gold_fluid"), pipe("sandstone_fluid"),
    pipe("iron_fluid", "iron_fluid_filled", "iron_fluid_clear"),
    pipe("clay_fluid"), pipe("void_fluid"),
    pipe("wood_power", "wood_power_clear", "wood_power_filled"),
    pipe("cobblestone_power"), pipe("stone_power"), pipe("quartz_power"), pipe("gold_power"), pipe("sandstone_power"),
    pipe("structure", title="Cobblestone Structure Pipe"),
]


def tags(common: str, forge: str) -> list[dict]:
    """A recipe ingredient accepting both the Fabric conventional and the Forge tag."""
    return [{"tag": "c:" + common}, {"tag": "forge:" + forge}]


def gear(material: str) -> list[dict]:
    return tags(material + "_gears", "gears/" + material)


IRON = tags("iron_ingots", "ingots/iron")
GOLD = tags("gold_ingots", "ingots/gold")
DIAMOND = tags("diamonds", "gems/diamond")
REDSTONE = tags("redstone_dusts", "dusts/redstone")
GLASS = tags("glass_blocks", "glass/colorless")
COBBLESTONE = tags("cobblestone", "cobblestone")
PLANKS = {"tag": "minecraft:planks"}
STICKS = tags("wooden_rods", "rods/wooden")
SEALANT_SOURCES = tags("green_dyes", "dyes/green") + tags("slime_balls", "slimeballs")

# Upstream BCTransportRecipes.addPipeRecipe materials; "lgr" with colourless glass in the middle.
PIPE_MATERIALS = {
    "wood": (PLANKS, PLANKS),
    "cobblestone": (COBBLESTONE, COBBLESTONE),
    "stone": (tags("stone", "stone"), tags("stone", "stone")),
    "quartz": (tags("quartz_blocks", "storage_blocks/quartz"), tags("quartz_blocks", "storage_blocks/quartz")),
    "iron": (IRON, IRON),
    "gold": (GOLD, GOLD),
    "clay": ({"item": "minecraft:clay"}, {"item": "minecraft:clay"}),
    "sandstone": (tags("sandstone", "sandstone"), tags("sandstone", "sandstone")),
    "void": (tags("black_dyes", "dyes/black"), REDSTONE),
    "diamond": (DIAMOND, DIAMOND),
}

# Vanilla members of every tag the recipes use, defined under both namespaces so that recipes
# resolve on either loader even without other mods.
MATERIAL_TAGS = {
    ("iron_ingots", "ingots/iron"): ["minecraft:iron_ingot"],
    ("gold_ingots", "ingots/gold"): ["minecraft:gold_ingot"],
    ("diamonds", "gems/diamond"): ["minecraft:diamond"],
    ("redstone_dusts", "dusts/redstone"): ["minecraft:redstone"],
    ("glass_blocks", "glass/colorless"): ["minecraft:glass"],
    ("cobblestone", "cobblestone"): ["minecraft:cobblestone"],
    ("stone", "stone"): ["minecraft:stone"],
    ("quartz_blocks", "storage_blocks/quartz"): ["minecraft:quartz_block"],
    ("sandstone", "sandstone"): ["minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:cut_sandstone"],
    ("black_dyes", "dyes/black"): ["minecraft:black_dye"],
    ("green_dyes", "dyes/green"): ["minecraft:green_dye"],
    ("yellow_dyes", "dyes/yellow"): ["minecraft:yellow_dye"],
    ("lapis", "gems/lapis"): ["minecraft:lapis_lazuli"],
    ("slime_balls", "slimeballs"): ["minecraft:slime_ball"],
    ("wooden_rods", "rods/wooden"): ["minecraft:stick"],
    ("wooden_chests", "chests/wooden"): ["minecraft:chest", "minecraft:trapped_chest"],
    ("quartz", "gems/quartz"): ["minecraft:quartz"],
}

# DyeColor order: (1.19 name, upstream EnumDyeColor name, title).
WIRE_COLOURS = [
    ("white", "white", "White"), ("orange", "orange", "Orange"), ("magenta", "magenta", "Magenta"),
    ("light_blue", "light_blue", "Light Blue"), ("yellow", "yellow", "Yellow"), ("lime", "lime", "Lime"),
    ("pink", "pink", "Pink"), ("gray", "gray", "Gray"), ("light_gray", "silver", "Light Gray"), ("cyan", "cyan", "Cyan"),
    ("purple", "purple", "Purple"), ("blue", "blue", "Blue"), ("brown", "brown", "Brown"), ("green", "green", "Green"),
    ("red", "red", "Red"), ("black", "black", "Black"),
]
MATERIAL_TAGS.update({(f"{colour}_dyes", f"dyes/{colour}"): [f"minecraft:{colour}_dye"] for colour, _, _ in WIRE_COLOURS})
MATERIAL_TAGS.update({(f"{colour}_glass_blocks", f"glass/{colour}"): [f"minecraft:{colour}_stained_glass"] for colour, _, _ in WIRE_COLOURS})

# Upstream EnumRedstoneChipset and its assembly_recipes_examples: (id, texture, name, MJ, extra ingredient).
CHIPSETS = [
    ("redstone_chipset", "red", "Redstone Chipset", 10000, None),
    ("iron_chipset", "iron", "Iron Chipset", 20000, IRON),
    ("gold_chipset", "gold", "Golden Chipset", 40000, GOLD),
    ("quartz_chipset", "quartz", "Quartz Chipset", 60000, tags("quartz", "gems/quartz")),
    ("diamond_chipset", "diamond", "Diamond Chipset", 80000, DIAMOND),
]
# Mirrors ports.buildcraft.silicon.BCSilicon: (block id, upstream model, texture parts, name).
LASER_TABLES = [
    ("assembly_table", "assembly", ["bottom", "side", "top"], "Assembly Table"),
    ("advanced_crafting_table", "advanced_crafting", ["bottom", "middle", "side", "top"], "Advanced Crafting Table"),
    ("integration_table", "integration", ["bottom", "center", "middle", "side", "top"], "Integration Table"),
]

# Mirrors ports.buildcraft.silicon.gate: material -> (texture, modifiable), modifier -> texture.
GATE_MATERIALS = {"clay_brick": ("minecraft:block/bricks", False), "iron": ("minecraft:block/iron_block", True),
                  "nether_brick": ("minecraft:block/nether_bricks", True), "gold": ("minecraft:block/gold_block", True)}
GATE_MODIFIERS = {"no_modifier": None, "lapis": "minecraft:block/lapis_block", "quartz": "minecraft:block/quartz_block_top",
                  "diamond": "minecraft:block/diamond_block"}


def gate_name(logic: str, material: str, modifier: str) -> str:
    """GateVariant.variantName, which names the gate's item."""
    return f"gate_{material}_{logic}_{modifier}" if GATE_MATERIALS[material][1] else f"gate_{material}"


GATE_VARIANTS = [("and", "clay_brick", "no_modifier")] + [
    (logic, material, modifier) for material, (_, modifiable) in GATE_MATERIALS.items() if modifiable
    for logic in ["and", "or"] for modifier in GATE_MODIFIERS]


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def rl(path: str) -> str:
    return f"{MOD}:{path}"


class Generator:
    def __init__(self, upstream: Path):
        self.upstream = upstream
        self.src = upstream / "buildcraft_resources" / "assets"
        self.lang: dict[str, str] = {}

    # ---- textures -------------------------------------------------------------------------

    def copy(self, source: str, target: str) -> None:
        src = self.src / source
        if not src.exists():
            raise FileNotFoundError(src)
        dest = ASSETS / "textures" / target
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest)
        meta = src.with_name(src.name + ".mcmeta")
        if meta.exists():
            shutil.copyfile(meta, dest.with_name(dest.name + ".mcmeta"))

    def textures(self) -> None:
        for stage in ["blue", "green", "yellow", "red", "overheat", "black"]:
            self.copy(f"buildcraftlib/textures/blocks/engine/trunk_{stage}.png", f"block/engine/trunk_{stage}.png")
        self.copy("buildcraftlib/textures/blocks/engine/chamber_base.png", "block/engine/chamber.png")
        for part in ["back", "side"]:
            self.copy(f"buildcraftcore/textures/blocks/engine/wood/{part}.png", f"block/engine/wood/{part}.png")
            for material in ["stone", "iron"]:
                self.copy(f"buildcraftenergy/textures/blocks/engine/{material}/{part}.png", f"block/engine/{material}/{part}.png")

        pipe_textures = {"power_flow"}
        for definition in PIPES:
            pipe_textures.update(definition["faces"].values())
            pipe_textures.add(definition["item"])
            if definition["special"]:
                pipe_textures.add(definition["special"])
        for texture in sorted(pipe_textures):
            self.copy(f"buildcrafttransport/textures/pipes/{texture}.png", f"block/pipe/{texture}.png")

        for part in ["end", "side", "side_joined_below"]:
            self.copy(f"buildcraftfactory/textures/blocks/tank/{part}.png", f"block/tank/{part}.png")
        for part in ["bottom", "side", "top", "tube"]:
            self.copy(f"buildcraftfactory/textures/blocks/pump/{part}.png", f"block/pump/{part}.png")
        for part in ["back", "bottom", "front", "side", "top", "tube"]:
            self.copy(f"buildcraftfactory/textures/blocks/mining_well/{part}.png", f"block/mining_well/{part}.png")
        for state in ["normal", "connected"]:
            for part in ["back", "bottom", "front", "side", "top"]:
                self.copy(f"buildcraftbuilders/textures/blocks/quarry/{state}/{part}.png", f"block/quarry/{state}/{part}.png")
        for part in ["power_sprite_a", "power_sprite_b", "power_sprite_c", "power_sprite_d", "tank_sprite_a", "tank_sprite_b"]:
            self.copy(f"buildcraftfactory/textures/blocks/distiller/{part}.png", f"block/distiller/{part}.png")
        for part in "abcde":
            self.copy(f"buildcraftfactory/textures/blocks/heat_exchange/sprite_{part}.png", f"block/heat_exchange/sprite_{part}.png")
        self.copy("buildcraftfactory/textures/gui/distiller.png", "gui/distiller.png")
        self.copy("buildcraftbuilders/textures/blocks/quarry/drill.png", "block/quarry/drill.png")
        self.copy("buildcraftbuilders/textures/blocks/frame/default.png", "block/frame/default.png")

        for material, _ in GEARS:
            self.copy(f"buildcraftcore/textures/items/gear_{material}.png", f"item/gear_{material}.png")
        self.copy("buildcraftcore/textures/items/wrench.png", "item/wrench.png")
        for layer in ["base", "fluid"]:
            self.copy(f"buildcraftcore/textures/items/fragile_fluid_shard_{layer}.png", f"item/fragile_fluid_shard_{layer}.png")
            # Forge's bucket model turned the whole shard upside down for gases ("flipGas").
            image = Image.open(ASSETS / "textures" / "item" / f"fragile_fluid_shard_{layer}.png")
            image.rotate(180).save(ASSETS / "textures" / "item" / f"fragile_fluid_shard_{layer}_gaseous.png")
        self.copy("buildcrafttransport/textures/items/pipewaterproof.png", "item/waterproof.png")
        self.copy("buildcraftenergy/textures/gui/steam_engine_gui.png", "gui/stirling_engine.png")
        self.copy("buildcraftenergy/textures/gui/combustion_engine_gui.png", "gui/combustion_engine.png")
        self.copy("buildcrafttransport/textures/gui/filter.png", "gui/diamond_pipe.png")

        for name, heat, light, dark, _ in FLUIDS:
            for kind in ["still", "flow"]:
                self.recolour_fluid(kind, name, heat, light, dark)
            if name in UPSTREAM_BUCKETS:
                self.copy(f"buildcraftenergy/textures/items/{UPSTREAM_BUCKETS[name]}.png", f"item/{name}_bucket.png")
            else:
                self.paint_bucket(name)

        icon = Image.open(self.src / "buildcraftcore/textures/items/gear_iron.png").convert("RGBA")
        icon.resize((128, 128), Image.NEAREST).save(ASSETS / "icon.png")
        shutil.copyfile(self.src / "buildcraft/logo.png", ASSETS / "logo.png")

    def recolour_fluid(self, kind: str, name: str, heat: int, light: int, dark: int) -> None:
        """Upstream AtlasSpriteFluid: each channel blends the dark and light colour by the grey value; alpha is opaque."""
        source = self.src / f"buildcraftenergy/textures/blocks/fluids/heat_{heat}_{kind}.png"
        image = Image.open(source).convert("RGBA")
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                channels = pixels[x, y][:3]
                out = []
                for i, value in enumerate(channels):
                    shift = 16 - 8 * i
                    d = (dark >> shift) & 0xFF
                    l = (light >> shift) & 0xFF
                    out.append((d * (256 - value) + l * value) // 256)
                pixels[x, y] = (out[0], out[1], out[2], 255)
        dest = ASSETS / "textures" / "block" / "fluids" / f"{name}_{kind}.png"
        dest.parent.mkdir(parents=True, exist_ok=True)
        image.save(dest)
        shutil.copyfile(source.with_name(source.name + ".mcmeta"), dest.with_name(dest.name + ".mcmeta"))

    def paint_bucket(self, name: str) -> None:
        """
        Upstream 1.12 drew other buckets from the fluid sprite at runtime: paint the pixels where the oil
        and fuel buckets differ (the fluid) with the first frame of this fluid's recoloured still texture.
        """
        items = self.src / "buildcraftenergy/textures/items"
        oil = Image.open(items / "bucketOil.png").convert("RGBA")
        fuel = Image.open(items / "bucketFuel.png").convert("RGBA")
        still = Image.open(ASSETS / "textures" / "block" / "fluids" / f"{name}_still.png").convert("RGBA")
        out = oil.copy()
        for y in range(oil.height):
            for x in range(oil.width):
                a, b = oil.getpixel((x, y)), fuel.getpixel((x, y))
                if a[3] > 0 and a != b:
                    out.putpixel((x, y), still.getpixel((x, y)))
        dest = ASSETS / "textures" / "item" / f"{name}_bucket.png"
        dest.parent.mkdir(parents=True, exist_ok=True)
        out.save(dest)

    # ---- models ---------------------------------------------------------------------------

    def model(self, path: str, value: dict) -> None:
        write_json(ASSETS / "models" / f"{path}.json", value)

    def blockstate(self, name: str, value: dict) -> None:
        write_json(ASSETS / "blockstates" / f"{name}.json", value)

    def item_model(self, name: str, parent: str) -> None:
        self.model(f"item/{name}", {"parent": parent})

    def generated_item(self, name: str, texture: str | None = None, parent: str = "minecraft:item/generated") -> None:
        self.model(f"item/{name}", {"parent": parent, "textures": {"layer0": rl(f"item/{texture or name}")}})

    def engines(self) -> None:
        side_uv = [0, 0, 16, 4]
        plate = {d: ({"uv": [0, 0, 16, 16], "texture": "#back"} if d in ("down", "up") else {"uv": side_uv, "texture": "#side"})
                 for d in DIRECTIONS}
        trunk = {d: ({"uv": [0, 0, 8, 8], "texture": "#trunk"} if d in ("down", "up") else {"uv": [8, 0, 16, 12], "texture": "#trunk"})
                 for d in DIRECTIONS}
        base = {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {d: dict(f, **({"cullface": "down"} if d == "down" else {}))
                                                                 for d, f in plate.items()}}
        trunk_element = {"from": [4, 4, 4], "to": [12, 16, 12], "faces": trunk}
        # EngineRenderer draws the trunk, whose texture and light follow the power stage, and the moving parts.
        self.model("block/engine_base", {"textures": {"particle": "#back"}, "elements": [base]})

        # Upstream item model: progress 0.2 gives progress_size = 0.2 * 15.99.
        size = round(0.2 * (8 * 2 - 0.01), 4)
        moving = {"from": [0, 4 + size, 0], "to": [16, 8 + size, 16], "faces": plate}
        chamber_uv = [3, size, 13, 0]
        chamber = {"from": [3, 4, 3], "to": [13, 4 + size, 13],
                   "faces": {d: {"uv": chamber_uv, "texture": "#chamber"} for d in ["north", "south", "west", "east"]}}
        self.model("item/engine_base", {
            "parent": "minecraft:block/block",
            "textures": {"particle": "#back", "trunk": rl("block/engine/trunk_blue"), "chamber": rl("block/engine/chamber")},
            "elements": [base, moving, trunk_element, chamber],
        })

        rotations = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
                     "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
        for block, material, title in ENGINES:
            textures = {"back": rl(f"block/engine/{material}/back"), "side": rl(f"block/engine/{material}/side")}
            self.model(f"block/engine/{material}", {"parent": rl("block/engine_base"), "textures": textures})
            variants = {f"facing={facing}": dict({"model": rl(f"block/engine/{material}")}, **rotation)
                        for facing, rotation in rotations.items()}
            self.blockstate(block, {"variants": variants})
            self.model(f"item/{block}", {"parent": rl("item/engine_base"), "textures": textures})
            self.lang[f"block.{MOD}.{block}"] = title

    @staticmethod
    def arm_bounds(direction: str) -> tuple[list[int], list[int]]:
        lo, hi = [4, 4, 4], [12, 12, 12]
        axis = {"west": 0, "east": 0, "down": 1, "up": 1, "north": 2, "south": 2}[direction]
        if direction in ("west", "down", "north"):
            lo[axis], hi[axis] = 0, 4
        else:
            lo[axis], hi[axis] = 12, 16
        return lo, hi

    def pipe_templates(self) -> None:
        """
        Upstream PipeBaseModelGenStandard: a centre face per unconnected side and a four-sided arm per
        connection, each also drawn inside out (an element with from > to) so the pipe's interior shows.
        """
        for direction in DIRECTIONS:
            face = {"texture": "#pipe"}
            self.model(f"block/pipe_center_{direction}", {
                **CUTOUT,
                "textures": {"particle": "#pipe"},
                "elements": [
                    {"from": [4, 4, 4], "to": [12, 12, 12], "faces": {direction: face}},
                    {"from": [12, 12, 12], "to": [4, 4, 4], "faces": {OPPOSITE[direction]: face}},
                ],
            })
            lo, hi = self.arm_bounds(direction)
            sides = {d: face for d in DIRECTIONS if d not in (direction, OPPOSITE[direction])}
            self.model(f"block/pipe_arm_{direction}", {
                **CUTOUT,
                "textures": {"particle": "#pipe"},
                "elements": [{"from": lo, "to": hi, "faces": sides}, {"from": hi, "to": lo, "faces": sides}],
            })
        ends = {"uv": [4, 4, 12, 12], "texture": "#pipe"}
        side = {"uv": [4, 0, 12, 16], "texture": "#pipe"}
        self.model("item/pipe_base", {
            "parent": "minecraft:block/block",
            **CUTOUT,
            "textures": {"particle": "#pipe"},
            "elements": [{"from": [4, 0, 4], "to": [12, 16, 12],
                          "faces": {d: ends if d in ("down", "up") else side for d in DIRECTIONS}}],
        })

    def pipe_part(self, texture: str, kind: str, direction: str, made: set[str]) -> str:
        path = f"block/pipe/{texture}_{kind}_{direction}"
        if path not in made:
            self.model(path, {"parent": rl(f"block/pipe_{kind}_{direction}"), "textures": {"pipe": rl(f"block/pipe/{texture}")}})
            made.add(path)
        return rl(path)

    def pipes(self) -> None:
        self.pipe_templates()
        made: set[str] = set()
        for definition in PIPES:
            name = "pipe_" + definition["name"]
            multipart = []
            for direction in DIRECTIONS:
                texture = definition["faces"][direction]
                special = definition["special"] or texture
                multipart.append({"when": {direction: "none"}, "apply": {"model": self.pipe_part(texture, "center", direction, made)}})
                multipart.append({"when": {direction: "connected"}, "apply": {"model": self.pipe_part(texture, "arm", direction, made)}})
                multipart.append({"when": {direction: "special"}, "apply": {"model": self.pipe_part(special, "arm", direction, made)}})
            self.blockstate(name, {"multipart": multipart})
            self.model(f"item/{name}", {"parent": rl("item/pipe_base"), "textures": {"pipe": rl("block/pipe/" + definition["item"])}})
            self.lang[f"block.{MOD}.{name}"] = definition["title"]
        self.lang[f"tip.block.{MOD}.pipe_structure"] = "Support pipe"

    # Upstream ModelItemSimple.TRANSFORM_PLUG_AS_ITEM, the display of ModelPluggableItem.
    PLUG_ITEM_DISPLAY = {
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, -4], "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, -4], "scale": [0.4, 0.4, 0.4]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "gui": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    }

    def plug_item(self, item: str, upstream: str, textures: dict[str, str]) -> None:
        """An item model of an upstream plug model's elements, which upstream's ModelPluggableItem drew from the same quads."""
        model = json.loads((self.src / upstream).read_text(encoding="utf-8"))
        self.model(f"item/{item}", {"gui_light": "front", "textures": dict(textures, particle="#all"), "elements": model["elements"],
                                    "display": self.PLUG_ITEM_DISPLAY})

    def plugs(self) -> None:
        self.copy("buildcrafttransport/textures/pipes/plug.png", "block/pipe/plug.png")
        self.copy("buildcrafttransport/textures/pipes/power_adapter.png", "block/pipe/power_adapter.png")
        self.plug_item("plug_blocker", "buildcrafttransport/models/plugs/blocker.json", {"all": rl("block/pipe/plug")})
        self.plug_item("plug_power_adaptor", "buildcrafttransport/models/plugs/power_adapter.json", {"all": rl("block/pipe/power_adapter")})
        self.lang[f"item.{MOD}.plug_blocker"] = "Pipe Plug"
        self.lang[f"item.{MOD}.plug_power_adaptor"] = "Power Adapter"

    def factory(self) -> None:
        self.model("block/tank", {
            **CUTOUT,
            "textures": {"particle": rl("block/tank/side"), "up": rl("block/tank/end"), "down": rl("block/tank/end"),
                         "side": rl("block/tank/side")},
            "elements": [{"from": [2, 0, 2], "to": [14, 16, 14], "faces": {
                "down": {"texture": "#down", "cullface": "down"}, "up": {"texture": "#up", "cullface": "up"},
                "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}}],
        })
        self.model("block/tank_joined_below", {"parent": rl("block/tank"), "textures": {
            "particle": rl("block/tank/side_joined_below"), "side": rl("block/tank/side_joined_below")}})
        self.blockstate("tank", {"variants": {"joined_below=false": {"model": rl("block/tank")},
                                              "joined_below=true": {"model": rl("block/tank_joined_below")}}})
        self.item_model("tank", rl("block/tank"))

        self.model("block/pump", {"parent": "minecraft:block/cube", "textures": {
            "particle": rl("block/pump/side"), "down": rl("block/pump/bottom"), "up": rl("block/pump/top"),
            "north": rl("block/pump/side"), "east": rl("block/pump/side"), "south": rl("block/pump/side"), "west": rl("block/pump/side")}})
        self.blockstate("pump", {"variants": {"": {"model": rl("block/pump")}}})
        self.item_model("pump", rl("block/pump"))

        self.model("block/mining_well", {"parent": "minecraft:block/cube", "textures": {
            "particle": rl("block/mining_well/side"), "down": rl("block/mining_well/bottom"), "up": rl("block/mining_well/top"),
            "north": rl("block/mining_well/front"), "east": rl("block/mining_well/side"), "south": rl("block/mining_well/back"),
            "west": rl("block/mining_well/side")}})
        self.blockstate("mining_well", {"variants": {f"facing={f}": dict({"model": rl("block/mining_well")}, **({"y": y} if y else {}))
                                                     for f, y in HORIZONTAL_Y.items()}})
        self.item_model("mining_well", rl("block/mining_well"))

        self.model("block/tube", {"textures": {"particle": rl("block/pump/tube")}})
        self.blockstate("tube", {"variants": {"": {"model": rl("block/tube")}}})

        self.lang.update({f"block.{MOD}.tank": "Tank", f"block.{MOD}.pump": "Pump", f"block.{MOD}.mining_well": "Mining Well",
                          f"block.{MOD}.tube": "Tube"})

    def upstream_model(self, path: str) -> dict:
        return json.loads((self.src / f"buildcraftfactory/models/{path}.json").read_text(encoding="utf-8"))

    @staticmethod
    def both_sides(element: dict) -> list[dict]:
        """
        Upstream "both_sides": the element and an inside-out copy (from > to), whose faces named after the
        opposite side lie on the original faces' planes and face inward.
        """
        inverted = {"from": element["to"], "to": element["from"],
                    "faces": {OPPOSITE[d]: face for d, face in element["faces"].items()}}
        return [element, inverted]

    def refinery(self) -> None:
        # Upstream block/distiller.json is a vanilla model; the power blocks and fluids are DistillerRenderer's.
        distiller_textures = {"particle": rl("block/distiller/tank_sprite_a"), "sprite_a": rl("block/distiller/tank_sprite_a"),
                              "sprite_b": rl("block/distiller/tank_sprite_b")}
        self.model("block/distiller", {**CUTOUT, "textures": distiller_textures,
                                       "elements": self.upstream_model("block/distiller")["elements"]})
        self.blockstate("distiller", {"variants": {f"facing={f}": dict({"model": rl("block/distiller")}, **({"y": y} if y else {}))
                                                   for f, y in WEST_Y.items()}})
        item = self.upstream_model("item/distiller")
        self.model("item/distiller", {"parent": "minecraft:block/block", **CUTOUT, "elements": item["elements"],
                                      "textures": dict(distiller_textures, power=rl("block/distiller/power_sprite_a"))})

        # Upstream models/tiles/heat_exchange_static.json, facing west, split by each element's "visible"
        # condition into multipart models. Its face rotations count quarter turns.
        static = self.upstream_model("tiles/heat_exchange_static")
        textures = {"particle": rl("block/heat_exchange/sprite_a")}
        textures.update({key.lstrip("#"): value.replace("buildcraftfactory:blocks/", rl("block/"))
                         for key, value in static["textures"].items()})
        parts: dict[str, list[dict]] = {}
        names = {"Inner Pipe": "inner", "Outer Pipe": "outer", "Middle pipe cap (if its not connected on the left side)": "cap_left",
                 "Middle pipe cap (if its not connected on the right side)": "cap_right",
                 "Start outer pipe - vertical connector (down)": "start", "End outer pipe - vertical connector (up)": "end"}
        for element in static["elements"]:
            faces = {}
            for direction, face in element["faces"].items():
                converted = {"uv": face["uv"], "texture": face["texture"]}
                if face.get("rotation"):
                    converted["rotation"] = face["rotation"] * 90
                faces[direction] = converted
            converted = {"from": element["from"], "to": element["to"], "faces": faces}
            parts[names[element["comment"]]] = self.both_sides(converted) if element.get("both_sides") == "true" else [converted]
        for name, elements in parts.items():
            self.model(f"block/heat_exchange/{name}", {**CUTOUT, "textures": textures, "elements": elements})
        conditions = {"inner": {}, "outer": {"part": "middle"}, "cap_left": {"part": "middle", "connected_left": "false"},
                      "cap_right": {"part": "middle", "connected_right": "false"}, "start": {"part": "start"}, "end": {"part": "end"}}
        multipart = []
        for facing, y in WEST_Y.items():
            for name, when in conditions.items():
                apply = {"model": rl(f"block/heat_exchange/{name}")}
                if y:
                    apply["y"] = y
                multipart.append({"when": dict({"facing": facing}, **when), "apply": apply})
        self.blockstate("heat_exchange", {"multipart": multipart})
        self.model("item/heat_exchange", {"parent": "minecraft:block/block", **CUTOUT, "textures": textures,
                                          "elements": [e for n in ["inner", "outer", "cap_left", "cap_right"] for e in parts[n]]})
        self.lang.update({f"block.{MOD}.distiller": "Distiller", f"block.{MOD}.heat_exchange": "Heat Exchanger"})

    def builders(self) -> None:
        textures = {"down": "bottom", "up": "top", "north": "front", "south": "back", "west": "side", "east": "side"}
        multipart = []
        for local, texture in textures.items():
            for state in ["normal", "connected"]:
                path = f"block/quarry/{local}_{state}"
                self.model(path, {"textures": {"particle": rl(f"block/quarry/normal/side"), "face": rl(f"block/quarry/{state}/{texture}")},
                                  "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                                                "faces": {local: {"texture": "#face", "cullface": local}}}]})
                for facing, y in HORIZONTAL_Y.items():
                    apply = {"model": rl(path)}
                    if y:
                        apply["y"] = y
                    multipart.append({"when": {"facing": facing, f"connected_{local}": str(state == "connected").lower()}, "apply": apply})
        self.blockstate("quarry", {"multipart": multipart})
        self.model("item/quarry", {"parent": "minecraft:block/cube", "textures": dict(
            {"particle": rl("block/quarry/normal/side")}, **{d: rl(f"block/quarry/normal/{t}") for d, t in textures.items()})})

        frame = rl("block/frame/default")
        self.model("block/frame_base", {"parent": "minecraft:block/block", **CUTOUT, "textures": {"particle": frame, "all": frame}, "elements": [
            {"from": [4, 4, 4], "to": [12, 12, 12], "faces": {d: {"texture": "#all"} for d in DIRECTIONS}},
            {"from": [12, 12, 12], "to": [4, 4, 4], "faces": {d: {"texture": "#all"} for d in DIRECTIONS}}]})
        sides = {d: {"texture": "#all"} for d in ["down", "up", "west", "east"]}
        self.model("block/frame_connection", {**CUTOUT, "textures": {"particle": frame, "all": frame}, "elements": [
            {"from": [4, 4, 0], "to": [12, 12, 4], "faces": sides}, {"from": [12, 12, 4], "to": [4, 4, 0], "faces": sides}]})
        rotations = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
        multipart = [{"apply": {"model": rl("block/frame_base")}}]
        for direction, rotation in rotations.items():
            multipart.append({"when": {f"connected_{direction}": "true"}, "apply": dict({"model": rl("block/frame_connection")}, **rotation)})
        self.blockstate("frame", {"multipart": multipart})
        self.item_model("frame", rl("block/frame_base"))
        self.lang.update({f"block.{MOD}.quarry": "Quarry", f"block.{MOD}.frame": "Frame"})

    def filler(self) -> None:
        for part in ["bottom", "top", "front", "side"]:
            self.copy(f"buildcraftbuilders/textures/blocks/filler/{part}.png", f"block/filler/{part}.png")
        upstream = self.src / "buildcraftbuilders/textures/filler"
        for kind in ["patterns", "parameters"]:
            for texture in sorted((upstream / kind).glob("*.png")):
                self.copy(f"buildcraftbuilders/textures/filler/{kind}/{texture.name}", f"filler/{kind}/{texture.name}")
        self.copy("buildcraftbuilders/textures/gui/filler.png", "gui/filler.png")
        self.copy("buildcraftbuilders/textures/robot.png", "robot.png")
        self.copy("buildcraftlib/textures/gui/misc_slots.png", "gui/misc_slots.png")
        self.copy("buildcraftlib/textures/icons/ledger_right.png", "gui/ledger_right.png")
        self.copy("buildcraftlib/textures/icons/lock.png", "gui/lock.png")
        for power in ["low", "med", "high", "full"]:
            self.copy(f"buildcraftcore/textures/lasers/power_{power}.png", f"lasers/power_{power}.png")

        side = rl("block/filler/side")
        self.model("block/filler", {"parent": "minecraft:block/cube", "textures": {
            "particle": side, "down": rl("block/filler/bottom"), "up": rl("block/filler/top"), "north": rl("block/filler/front"),
            "east": side, "south": side, "west": side}})
        self.blockstate("filler", {"variants": {f"facing={facing}": dict({"model": rl("block/filler")}, **({"y": y} if y else {}))
                                                for facing, y in HORIZONTAL_Y.items()}})
        self.item_model("filler", rl("block/filler"))

        patterns = {"none": "None", "clear": "Clear", "fill": "Fill", "box": "Box", "frame": "Frame", "pyramid": "Pyramid",
                    "stairs": "Stairs", "sphere": "Sphere", "sphere_half": "Hemisphere", "sphere_quarter": "Quarter-sphere",
                    "sphere_eighth": "Eighth-Sphere", "2d_arc": "Arc", "2d_circle": "Circle", "2d_hexagon": "Hexagon",
                    "2d_octagon": "Octagon", "2d_pentagon": "Pentagon", "2d_semi_circle": "Semi-Circle", "2d_square": "Square",
                    "2d_triangle": "Triangle"}
        for tag, title in patterns.items():
            self.lang[f"fillerpattern.{tag}"] = title
        self.lang.update({"fillerpattern.parameter.hollow": "Hollow", "fillerpattern.parameter.filled": "Filled",
                          "fillerpattern.parameter.filled_outer": "Surrounded"})
        centres = ["North-West", "North", "North-East", "West", "Center", "East", "South-West", "South", "South-East"]
        for index, title in enumerate(centres):
            self.lang[f"direction.center.{index}"] = title
        self.lang.update({"direction.up": "Top", "direction.down": "Bottom", "direction.north": "North", "direction.south": "South",
                          "direction.west": "West", "direction.east": "East"})
        for axis in "xyz":
            self.lang[f"buildcraft.param.axis.{axis}"] = f"{axis.upper()} Axis"
        for count, title in enumerate(["No rotation", "90 degree rotation", "180 degree rotation", "270 degree rotation"]):
            self.lang[f"buildcraft.param.rotation.{count}"] = title
        # Upstream's lang files have no names for the facing parameter.
        for face in DIRECTIONS:
            self.lang[f"buildcraft.param.facing.{face}"] = face.capitalize()
        self.lang.update({
            f"block.{MOD}.filler": "Filler",
            f"block.{MOD}.filler.tip": "Not usable in survival.",
            f"gui.{MOD}.locked": "Locked",
            "gui.filling.resources": "Filling Resources",
            "gui.progress": "Progress",
            "gui.leftToBreak": "To break",
            "gui.leftToPlace": "To place",
            "tip.filler.excavate.on": "Excavate",
            "tip.filler.excavate.off": "Do Not Excavate",
            "tip.filler.invert.on": "Invert",
            "tip.filler.invert.off": "Do Not Invert",
        })

    def architect(self) -> None:
        for part in ["back", "bottom", "front_off", "front_on", "left", "right", "top"]:
            self.copy(f"buildcraftbuilders/textures/blocks/architect/{part}.png", f"block/architect/{part}.png")
        self.copy("buildcraftbuilders/textures/blocks/scan.png", "block/scan.png")
        self.copy("buildcraftbuilders/textures/gui/architect.png", "gui/architect.png")
        for state in ["off", "on"]:
            model = json.loads((self.src / f"buildcraftbuilders/models/block/architect_{state}.json").read_text(encoding="utf-8"))
            model["parent"] = "minecraft:block/orientable"
            model["textures"] = {face: texture.replace("buildcraftbuilders:blocks/", rl("block/"))
                                 for face, texture in model["textures"].items()}
            self.model(f"block/architect_{state}", model)
        self.blockstate("architect_table", {"variants": {
            f"facing={facing},valid={valid}": dict({"model": rl(f"block/architect_{state}")}, **({"y": y} if y else {}))
            for valid, state in [("true", "on"), ("false", "off")] for facing, y in HORIZONTAL_Y.items()}})
        self.item_model("architect_table", rl("block/architect_off"))
        for kind in ["template", "blueprint"]:
            for state in ["clean", "used"]:
                self.copy(f"buildcraftbuilders/textures/items/{kind}/{state}.png", f"item/{kind}/{state}.png")
                self.generated_item(f"{kind}_{state}", f"{kind}/{state}")
        self.lang.update({
            f"block.{MOD}.architect_table": "Architect Table",
            f"block.{MOD}.architect_table.tip": "Not usable in survival",
            f"item.{MOD}.template_clean": "Template",
            f"item.{MOD}.template_used": "Template",
            f"item.{MOD}.blueprint_clean": "Blueprint",
            f"item.{MOD}.blueprint_used": "Blueprint",
            f"item.{MOD}.snapshot.author": "by",
            f"item.{MOD}.snapshot.blank": "Blank",
        })

    def builder(self) -> None:
        for part in ["back", "bottom", "front", "side", "top", "slot_empty", "slot_template", "slot_blueprint"]:
            self.copy(f"buildcraftbuilders/textures/blocks/builder/{part}.png", f"block/builder/{part}.png")
        self.copy("buildcraftbuilders/textures/gui/builder.png", "gui/builder.png")
        self.copy("buildcraftbuilders/textures/gui/builder_blueprint.png", "gui/builder_blueprint.png")
        self.copy("buildcraftcore/textures/lasers/stripes_write_direction.png", "lasers/stripes_write_direction.png")
        upstream = self.src / "buildcraftbuilders/models/block/builder"
        for part in ["main", "slot_empty", "slot_template", "slot_blueprint"]:
            model = json.loads((upstream / f"{part}.json").read_text(encoding="utf-8"))
            if "parent" in model:
                model["parent"] = "minecraft:" + model["parent"]
            model["textures"] = {key: texture.replace("buildcraftbuilders:blocks/", rl("block/"))
                                 for key, texture in model["textures"].items()}
            self.model(f"block/builder/{part}", model)
        # Upstream's forge_marker blockstate applies the slot submodel with the facing's rotation.
        multipart = []
        for facing, y in HORIZONTAL_Y.items():
            rotation = {"y": y} if y else {}
            multipart.append({"when": {"facing": facing}, "apply": dict({"model": rl("block/builder/main")}, **rotation)})
            for snapshot_type, slot in [("none", "slot_empty"), ("template", "slot_template"), ("blueprint", "slot_blueprint")]:
                multipart.append({"when": {"facing": facing, "snapshot_type": snapshot_type},
                                  "apply": dict({"model": rl(f"block/builder/{slot}")}, **rotation)})
        self.blockstate("builder", {"multipart": multipart})
        self.item_model("builder", rl("block/builder/main"))
        self.lang.update({f"block.{MOD}.builder": "Builder", f"block.{MOD}.builder.tip": "Not usable in survival"})

    def library(self) -> None:
        for block, parts in [("library", ["back", "bottom", "front", "left", "right", "top"]),
                             ("replacer", ["bottom", "front", "side", "top"])]:
            for part in parts:
                self.copy(f"buildcraftbuilders/textures/blocks/{block}/{part}.png", f"block/{block}/{part}.png")
        self.copy("buildcraftbuilders/textures/gui/electronic_library.png", "gui/electronic_library.png")
        self.copy("buildcraftbuilders/textures/gui/replacer.png", "gui/replacer.png")
        self.copy("buildcraftlib/textures/gui/buttons.png", "gui/buttons.png")
        for upstream, name in [("library", "electronic_library"), ("replacer", "replacer")]:
            model = json.loads((self.src / f"buildcraftbuilders/models/block/{upstream}.json").read_text(encoding="utf-8"))
            model["parent"] = "minecraft:" + model["parent"]
            model["textures"] = {face: texture.replace("buildcraftbuilders:blocks/", rl("block/"))
                                 for face, texture in model["textures"].items()}
            self.model(f"block/{name}", model)
            self.blockstate(name, {"variants": {f"facing={facing}": dict({"model": rl(f"block/{name}")}, **({"y": y} if y else {}))
                                                for facing, y in HORIZONTAL_Y.items()}})
            self.item_model(name, rl(f"block/{name}"))
        for state in ["clean", "used"]:
            self.copy(f"buildcraftbuilders/textures/items/schematic_single/{state}.png", f"item/schematic_single/{state}.png")
            self.generated_item(f"schematic_single_{state}", f"schematic_single/{state}")
        # Upstream's lang named no replacer.
        self.lang.update({
            f"block.{MOD}.electronic_library": "Electronic Library",
            f"block.{MOD}.electronic_library.tip": "Not usable in survival",
            f"block.{MOD}.replacer": "Replacer",
            f"item.{MOD}.schematic_single_clean": "Single Block Schematic",
            f"item.{MOD}.schematic_single_used": "Single Block Schematic",
            f"gui.{MOD}.del": "Del",
        })

    def markers(self) -> None:
        for kind in ["volume", "path"]:
            self.copy(f"buildcraftcore/textures/blocks/marker_{kind}.png", f"block/marker_{kind}.png")
            self.copy(f"buildcraftcore/textures/items/marker_{kind}.png", f"item/marker_{kind}.png")
        self.copy("buildcraftcore/textures/items/marker_connector.png", "item/marker_connector.png")
        for laser in ["marker_volume_connected", "marker_volume_possible", "marker_volume_signal", "marker_path_connected",
                      "marker_path_possible", "marker_default_possible", "stripes_read", "stripes_write"]:
            self.copy(f"buildcraftcore/textures/lasers/{laser}.png", f"lasers/{laser}.png")

        torch = json.loads((self.src / "buildcraftcore/models/block/torch_center_lit.json").read_text(encoding="utf-8"))
        for element in torch["elements"]:
            element.pop("comment", None)
        self.model("block/torch_center_lit", {**CUTOUT, **torch})
        # Upstream's "active" state changed nothing in the model, so only the facing is kept.
        facings = {"up": {}, "down": {"x": 180}, "east": {"y": 90, "x": 90}, "south": {"y": 180, "x": 90},
                   "west": {"y": 270, "x": 90}, "north": {"x": 90}}
        for kind in ["volume", "path"]:
            name = f"marker_{kind}"
            self.model(f"block/{name}", {"parent": rl("block/torch_center_lit"), "textures": {"all": rl(f"block/{name}")}})
            self.blockstate(name, {"variants": {f"facing={facing}": dict({"model": rl(f"block/{name}")}, **rotation)
                                                for facing, rotation in facings.items()}})
            self.generated_item(name)
        self.generated_item("marker_connector", parent="minecraft:item/handheld")
        volume_box = json.loads((self.src / "buildcraftcore/models/item/volume_box.json").read_text(encoding="utf-8"))
        volume_box["parent"] = "minecraft:block/block"
        volume_box["textures"] = {"main": rl("lasers/marker_volume_connected"), "particle": rl("lasers/marker_volume_connected")}
        self.model("item/volume_box", volume_box)
        # Upstream's lang files have no name for the volume box.
        self.lang.update({f"block.{MOD}.marker_volume": "Land Mark", f"block.{MOD}.marker_path": "Path Mark",
                          f"item.{MOD}.marker_connector": "Marker Connector", f"item.{MOD}.volume_box": "Volume Box"})

    def fluids(self) -> None:
        for heat, suffix in enumerate(HEAT_NAMES):
            self.lang[f"buildcraft.fluid.heat_{heat}"] = f"%s ({suffix})"
        for family, _, _, title in FLUID_FAMILIES:
            self.lang[f"fluid_family.{MOD}.{family}"] = title
        for name, heat, _, _, title in FLUIDS:
            self.model(f"block/{name}", {"textures": {"particle": rl(f"block/fluids/{name}_still")}})
            self.blockstate(name, {"variants": {"": {"model": rl(f"block/{name}")}}})
            self.generated_item(f"{name}_bucket")
            self.lang[f"block.{MOD}.{name}"] = f"{title} ({HEAT_NAMES[heat]})"
            self.lang[f"fluid.{MOD}.{name}"] = f"{title} ({HEAT_NAMES[heat]})"
            self.lang[f"item.{MOD}.{name}_bucket"] = f"{title} Bucket ({HEAT_NAMES[heat]})"

    def oil_wells(self) -> None:
        self.blockstate("oil_spring", {"variants": {"": {"model": "minecraft:block/bedrock"}}})
        self.item_model("oil_spring", "minecraft:block/bedrock")
        self.lang[f"block.{MOD}.oil_spring"] = "Oil Spring"
        # Upstream spring.json draws both spring types as bedrock.
        self.blockstate("water_spring", {"variants": {"": {"model": "minecraft:block/bedrock"}}})
        self.item_model("water_spring", "minecraft:block/bedrock")
        self.lang[f"block.{MOD}.water_spring"] = "Water Spring"
        # The Forge biome modifier and Fabric's BiomeModifications add this to overworld biomes.
        write_json(DATA / MOD / "worldgen" / "configured_feature" / "oil_well.json", {"type": rl("oil_well"), "config": {}})
        write_json(DATA / MOD / "worldgen" / "placed_feature" / "oil_well.json", {"feature": rl("oil_well"), "placement": []})

    def oil_biomes(self) -> None:
        # Upstream BiomeOilDesert and BiomeOilOcean copy BiomeDesert's and BiomeOcean's properties.
        biomes = DATA / MOD / "worldgen" / "biome"
        biomes.mkdir(parents=True, exist_ok=True)
        for oil, parent, title in [("oil_desert", "desert", "Desert Oil Field"), ("oil_ocean", "ocean", "Ocean Oil Field")]:
            shutil.copyfile(VANILLA_BIOMES / f"{parent}.json", biomes / f"{oil}.json")
            self.lang[f"biome.{MOD}.{oil}"] = title
        desert, ocean = rl("oil_desert"), rl("oil_ocean")

        def optional(tag: str) -> dict:
            return {"id": tag, "required": False}

        def biome_tag(namespace: str, path: str, values: list[str | dict]) -> None:
            self.tag("worldgen/biome", namespace, path, values)

        # Upstream's layers replace biomes of the HOT, DRY and SANDY types, and of the OCEAN type.
        biome_tag(MOD, "oil_desert_replaceable", ["minecraft:desert", optional("#forge:is_desert"), optional("#c:desert")])
        biome_tag(MOD, "oil_ocean_replaceable", ["#minecraft:is_ocean"])
        biome_tag(MOD, "excessive_oil", [desert, ocean])
        # Forge's biome dictionary typed the oil biomes as their parents. 1.12 kept villages, desert temples
        # and ocean monuments out of them but placed mineshafts and, above sea level, strongholds anywhere.
        for path in ["is_overworld", "has_structure/mineshaft"]:
            biome_tag("minecraft", path, [desert, ocean])
        for path in ["only_allows_snow_and_gold_rabbits", "spawns_warm_variant_frogs", "stronghold_biased_to",
                     "has_structure/ruined_portal_desert"]:
            biome_tag("minecraft", path, [desert])
        for path in ["is_ocean", "has_structure/ocean_ruin_cold", "has_structure/ruined_portal_ocean",
                     "plays_underwater_music", "water_on_map_outlines"]:
            biome_tag("minecraft", path, [ocean])
        for path in ["is_desert", "is_sandy", "is_hot/overworld", "is_dry/overworld"]:
            biome_tag("forge", path, [desert])
        biome_tag("c", "in_overworld", [desert, ocean])
        biome_tag("c", "desert", [desert])
        biome_tag("c", "shallow_ocean", [ocean])

    def items(self) -> None:
        for material, title in GEARS:
            self.generated_item(f"gear_{material}")
            self.lang[f"item.{MOD}.gear_{material}"] = title
        self.generated_item("wrench", parent="minecraft:item/handheld")
        self.generated_item("waterproof")
        self.lang[f"item.{MOD}.wrench"] = "Wrench"
        self.lang[f"item.{MOD}.waterproof"] = "Pipe Sealant"

        # Upstream's forge:forgebucket model: the base and a fluid mask, which BCClient tints with the fluid.
        shard = "fragile_fluid_shard"
        self.model(f"item/{shard}_gaseous", {"parent": "minecraft:item/generated", "textures": {
            "layer0": rl(f"item/{shard}_base_gaseous"), "layer1": rl(f"item/{shard}_fluid_gaseous")}})
        self.model(f"item/{shard}", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": rl(f"item/{shard}_base"), "layer1": rl(f"item/{shard}_fluid")},
            "overrides": [{"predicate": {rl("gaseous"): 1}, "model": rl(f"item/{shard}_gaseous")}],
        })
        self.lang[f"item.{MOD}.{shard}"] = "Fragile %s Shard"

    def silicon_model(self, upstream: str, name: str) -> None:
        model = json.loads((self.src / f"buildcraftsilicon/models/block/{upstream}.json").read_text(encoding="utf-8"))
        model["parent"] = "minecraft:" + model["parent"]
        model["textures"] = {key: texture.replace("buildcraftsilicon:blocks/", rl("block/"))
                             for key, texture in model["textures"].items()}
        self.model(f"block/{name}", dict(model, **CUTOUT))

    def silicon(self) -> None:
        for part in ["bottom", "side", "top"]:
            self.copy(f"buildcraftsilicon/textures/blocks/laser/{part}.png", f"block/laser/{part}.png")
        self.silicon_model("laser", "laser")
        # Upstream's forge_marker blockstate: the model faces up and turns to each facing.
        rotations = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90},
                     "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
        self.blockstate("laser", {"variants": {f"facing={facing}": dict({"model": rl("block/laser")}, **rotation)
                                               for facing, rotation in rotations.items()}})
        self.item_model("laser", rl("block/laser"))
        for block, upstream, parts, title in LASER_TABLES:
            for part in parts:
                self.copy(f"buildcraftsilicon/textures/blocks/table/{upstream}/{part}.png", f"block/table/{upstream}/{part}.png")
            self.silicon_model(f"table/{upstream}", f"table/{upstream}")
            self.blockstate(block, {"variants": {"": {"model": rl(f"block/table/{upstream}")}}})
            self.item_model(block, rl(f"block/table/{upstream}"))
            self.copy(f"buildcraftsilicon/textures/gui/{block}.png", f"gui/{block}.png")
            self.lang[f"block.{MOD}.{block}"] = title
        for name, texture, title, _, _ in CHIPSETS:
            self.copy(f"buildcraftsilicon/textures/items/redstone_chipset/{texture}.png", f"item/chipset/{texture}.png")
            self.generated_item(name, f"chipset/{texture}")
            self.lang[f"item.{MOD}.{name}"] = title
        for icon in ["engine_active", "engine_inactive"]:
            self.copy(f"buildcraftlib/textures/icons/{icon}.png", f"gui/{icon}.png")
        self.lang.update({
            f"block.{MOD}.laser": "Laser",
            f"block.{MOD}.integration_table.tip": "No recipes use this!",
            "gui.power": "Power",
            "gui.stored": "Stored",
            "gui.assemblyRate": "Rate",
            "gui.assemblyCurrentRequired": "Required",
            "buildcraft.mj.static.short": "%s MJ",
            "buildcraft.mj.flow.seconds.short": "%s MJ/s",
        })

    def gate_item(self, logic: str, material: str, modifier: str) -> None:
        """Upstream ModelGateItem: the static plug elements of the variant and the dynamic part switched off, made bigger."""
        static = json.loads((self.src / "buildcraftsilicon/models/plugs/gate.json").read_text(encoding="utf-8"))["elements"]
        dynamic = json.loads((self.src / "buildcraftsilicon/models/plugs/gate_dynamic.json").read_text(encoding="utf-8"))["elements"]
        visible = {"material != 'clay_brick'": material != "clay_brick", "modifier != 'no_modifier'": modifier != "no_modifier"}
        elements = []
        for element in static + dynamic:
            if not visible.get(element.get("visible"), True):
                continue
            texture = element["faces"]["west"]["texture"].split("'")[1].rstrip("_")
            faces = {face: {"uv": value["uv"], "texture": texture} for face, value in element["faces"].items()}
            elements.append({"from": element["from"], "to": element["to"], "faces": faces})
        textures = {"material": GATE_MATERIALS[material][0], "logic": rl(f"block/gate/{logic}"),
                    "gate": rl("block/gate/off"), "particle": rl(f"block/gate/{logic}")}
        if GATE_MODIFIERS[modifier]:
            textures["modifier"] = GATE_MODIFIERS[modifier]
        display = {key: dict(value, scale=[round(s * 1.8, 4) for s in value["scale"]]) for key, value in self.PLUG_ITEM_DISPLAY.items()}
        self.model(f"item/{gate_name(logic, material, modifier)}", {"gui_light": "front", "textures": textures, "elements": elements,
                                                                     "display": display})

    def gates(self) -> None:
        for name in ["and", "or", "on", "off"]:
            self.copy(f"buildcraftsilicon/textures/gates/gate_{name}.png", f"block/gate/{name}.png")
        self.copy("buildcraftsilicon/textures/gui/gate_interface.png", "gui/gate.png")
        for variant in GATE_VARIANTS:
            self.gate_item(*variant)
        for state in ["empty", "full"]:
            self.copy(f"buildcraftsilicon/textures/items/gatecopier/{state}.png", f"item/gate_copier/{state}.png")
        self.generated_item("gate_copier_full", "gate_copier/full", parent="minecraft:item/handheld")
        self.model("item/gate_copier", {"parent": "minecraft:item/handheld", "textures": {"layer0": rl("item/gate_copier/empty")},
                                        "overrides": [{"predicate": {rl("full"): 1}, "model": rl("item/gate_copier_full")}]})

        core = "buildcraftcore/textures/triggers"
        triggers = ["trigger_true", "redstone_gate_side_only", "action_redstoneoutput", "trigger_machine_active", "trigger_machine_inactive",
                    "trigger_redstoneinput_active", "trigger_redstoneinput_inactive"]
        for kind in ["inventory", "liquidcontainer"]:
            triggers += [f"trigger_{kind}_{state}" for state in ["empty", "contains", "space", "full", "below25", "below50", "below75"]]
        triggers += [f"trigger_engineheat_{stage}" for stage in STAGES if stage != "black"]
        triggers += [f"trigger_dir_{direction}" for direction in DIRECTIONS]
        for sprite in triggers:
            self.copy(f"{core}/{sprite}.png", f"triggers/{sprite}.png")
        for contents in ["containsitems", "containsfluids", "requestsenergy"]:
            self.copy(f"buildcrafttransport/textures/triggers/trigger_pipecontents_{contents}.png", f"triggers/trigger_pipecontents_{contents}.png")

        # Upstream en_US.lang, whose %d placeholders become %s.
        self.lang.update({
            "gate.logic.and": "AND", "gate.logic.or": "OR",
            "gate.material.iron": "Iron", "gate.material.nether_brick": "Nether Brick", "gate.material.gold": "Gold",
            "gate.name": "%s %s Gate", "gate.name.basic": "Basic Gate",
            "gate.slots": "%s Slots", "gate.params": "%s Slot Parameters",
            "gate.params.trigger": "%s Trigger Parameters", "gate.params.action": "%s Action Parameters",
            "gate.side": "§7on the %s§7 side",
            "gate.parameter.redstone.gateSideOnly": "Gate Side Only",
            "gate.trigger.true": "Always On",
            "gate.trigger.engine.blue": "Engine Blue", "gate.trigger.engine.green": "Engine Green",
            "gate.trigger.engine.yellow": "Engine Yellow", "gate.trigger.engine.red": "Engine Red",
            "gate.trigger.engine.overheat": "Engine Overheat",
            "gate.trigger.fluid.empty": "Tank Empty", "gate.trigger.fluid.contains": "Fluid in Tank",
            "gate.trigger.fluid.space": "Space for Fluid", "gate.trigger.fluid.full": "Tank Full",
            "gate.trigger.fluidlevel.below": "Contains < %s%%",
            "gate.trigger.inventory.empty": "Inventory Empty", "gate.trigger.inventory.contains": "Items in Inventory",
            "gate.trigger.inventory.space": "Space in Inventory", "gate.trigger.inventory.full": "Inventory Full",
            "gate.trigger.inventorylevel.below": "Contains < %s%%",
            "gate.trigger.machine.done": "Work Done", "gate.trigger.machine.scheduled": "Has Work",
            "gate.trigger.redstone.input.active": "Redstone Signal On", "gate.trigger.redstone.input.inactive": "Redstone Signal Off",
            "gate.trigger.pipe.containsItems": "Items Traversing", "gate.trigger.pipe.containsFluids": "Fluid Traversing",
            "gate.trigger.pipe.requestsEnergy": "Power Requested",
            "gate.action.redstone.signal": "Redstone Signal",
            "gate.action.pipe.direction": "Face the %s side",
            "chat.gateCopier.gateCopied": "Copied gate information to copier.",
            "chat.gateCopier.gatePasted": "Pasted copied data to gate.",
            "chat.gateCopier.noInformation": "No gate information stored!",
            "buildcraft.item.nonclean.usage": "Shift-right click to clean",
            "gui.inventory": "Inventory",
            f"item.{MOD}.gate_copier": "Gate Copier",
        })
        material_names = {"iron": "Iron", "nether_brick": "Nether Brick", "gold": "Gold"}
        for logic, material, modifier in GATE_VARIANTS:
            title = f"{material_names[material]} {logic.upper()} Gate" if material in material_names else "Basic Gate"
            self.lang[f"item.{MOD}.{gate_name(logic, material, modifier)}"] = title

    def wires(self) -> None:
        for colour, upstream, title in WIRE_COLOURS:
            self.copy(f"buildcrafttransport/textures/wires/{upstream}.png", f"block/wire/{upstream}.png")
            self.copy(f"buildcrafttransport/textures/items/wire/{upstream}.png", f"item/wire/{upstream}.png")
            self.generated_item(f"wire_{colour}", f"wire/{upstream}")
            for state in ["active", "inactive"]:
                sprite = f"trigger_pipesignal_{upstream}_{state}"
                self.copy(f"buildcrafttransport/textures/triggers/{sprite}.png", f"triggers/{sprite}.png")
            self.lang[f"item.{MOD}.wire_{colour}"] = f"{title} Pipe Wire"
        self.lang.update({
            f"item.{MOD}.wire": "%s Pipe Wire",
            "gate.action.pipe.wire": "%s Pipe Signal",
            "gate.trigger.pipe.wire.active": "%s Pipe Signal On",
            "gate.trigger.pipe.wire.inactive": "%s Pipe Signal Off",
        })

    # Vanilla FaceInfo corner order, as (x, y, z) picks of an element's from (0) or to (1).
    VANILLA_CORNERS = {
        "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)], "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
        "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)], "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
        "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)], "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
    }

    @classmethod
    def vanilla_face(cls, face: str, uv: list[float], rotation: int) -> dict:
        """
        Upstream's variable models place a face's UVs with ModelUtil.createFace, which mirrors some faces against
        vanilla, then turn them with rotateTextureUp: find the vanilla UV rectangle and rotation that put the same
        texture point on every corner.
        """
        axis = {"down": 1, "up": 1, "north": 2, "south": 2, "west": 0, "east": 0}[face]
        positive = face in ("up", "south", "east")
        points = []
        for u, v in [(False, False), (False, True), (True, True), (True, False)]:
            point = [1 if u else 0, 0 if v else 1, (0 if v else 1) if axis == 1 else (1 if u else 0)]
            point[axis] = 1 if positive else 0
            points.append(tuple(point))
        base = [(uv[0], uv[1]), (uv[0], uv[3]), (uv[2], uv[3]), (uv[2], uv[1])]
        vertices = list(zip(points, base)) if (not positive) != (axis == 2) else list(zip(points, base))[::-1]
        upstream = {vertices[i][0]: vertices[(i + rotation) % 4][1] for i in range(4)}
        for turn in range(4):
            for u1, u2 in [(uv[0], uv[2]), (uv[2], uv[0])]:
                for v1, v2 in [(uv[1], uv[3]), (uv[3], uv[1])]:
                    corners = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                    if all(upstream[corner] == corners[(i + turn) % 4] for i, corner in enumerate(cls.VANILLA_CORNERS[face])):
                        result = {"uv": [u1, v1, u2, v2]}
                        if turn:
                            result["rotation"] = turn * 90
                        return result
        raise ValueError(f"no vanilla face matches {face} {uv} {rotation}")

    def lens_item(self, colour: str | None, upstream: str | None, is_filter: bool) -> None:
        """Upstream ModelLensItem: the lens or filter plug model facing west, its pane tinted by LensRenderer's item colour."""
        lens = json.loads((self.src / "buildcraftsilicon/models/plugs/lens.json").read_text(encoding="utf-8"))
        cutout = lens["cutout"]
        if is_filter:
            cutout = cutout + json.loads((self.src / "buildcraftsilicon/models/plugs/filter.json").read_text(encoding="utf-8"))["cutout"]
        elements = []
        for element in cutout:
            faces = {face: dict(self.vanilla_face(face, value["uv"], value.get("rotation", 0)), texture="#main")
                     for face, value in element["faces"].items()}
            elements.append({"from": element["from"], "to": element["to"], "faces": faces})
        if colour is not None or not is_filter:
            for element in lens["translucent"]:
                faces = {face: dict(self.vanilla_face(face, value["uv"], value.get("rotation", 0)), texture="#pane", tintindex=0)
                         for face, value in element["faces"].items()}
                elements.append({"from": element["from"], "to": element["to"], "faces": faces})
        kind = "filter" if is_filter else "lens"
        textures = {"main": rl(f"block/lens/{kind}"), "pane": rl("block/lens/overlay") if colour else "minecraft:block/water_flow",
                    "particle": "#main"}
        self.model(f"item/{kind}_{colour or 'clear'}", {"gui_light": "front", "textures": textures, "elements": elements,
                                                         "display": self.PLUG_ITEM_DISPLAY})

    def pulsars(self) -> None:
        """Upstream pulsar and timer plugs. The pulsar item is the switched-off model from setModelVariablesForItem."""
        for name in ["pulsar_static", "pulsar_dynamic_on", "pulsar_dynamic_off", "timer"]:
            self.copy(f"buildcraftsilicon/textures/plugs/{name}.png", f"block/pipe/{name}.png")
        for name in ["trigger_timer_short", "trigger_timer_medium", "trigger_timer_long", "action_pulsar_on", "action_pulsar_single"]:
            self.copy(f"buildcraftsilicon/textures/triggers/{name}.png", f"triggers/{name}.png")
        self.plug_item("plug_timer", "buildcraftsilicon/models/plugs/timer.json", {"all": rl("block/pipe/timer")})
        static = json.loads((self.src / "buildcraftsilicon/models/plugs/pulsar_static.json").read_text(encoding="utf-8"))
        piston = {"from": [1.999, 6, 6], "to": [3.999, 10, 10], "shade": True, "faces": {
            face: {"uv": [5.8, 6, 6, 10] if face in ("down", "up", "north", "south") else [6, 6, 10, 10], "texture": "#piston"}
            for face in ("down", "up", "north", "south", "west", "east")}}
        leds = [
            ([2.5, 6.5, 4.9], [3.5, 7.5, 5]), ([2.5, 8.5, 4.9], [3.5, 9.5, 5]),
            ([2.5, 8.5, 11], [3.5, 9.5, 11.1]), ([2.5, 6.5, 11], [3.5, 7.5, 11.1]),
            ([2.5, 4.9, 8.5], [3.5, 5, 9.5]), ([2.5, 4.9, 6.5], [3.5, 5, 7.5]),
            ([2.5, 11, 6.5], [3.5, 11.1, 7.5]), ([2.5, 11, 8.5], [3.5, 11.1, 9.5]),
        ]
        led_elements = [{"from": start, "to": end, "shade": True, "faces": {
            face: {"uv": [0, 0, 16, 16], "texture": "#led", "tintindex": 0} for face in ("down", "up", "north", "south", "west", "east")}}
            for start, end in leds]
        self.model("item/plug_pulsar", {
            "gui_light": "front",
            "textures": {"all": rl("block/pipe/pulsar_static"), "piston": rl("block/pipe/pulsar_dynamic_off"),
                         "led": "minecraft:block/white_concrete", "particle": "#all"},
            "elements": static["elements"] + [piston] + led_elements,
            "display": self.PLUG_ITEM_DISPLAY})
        self.lang[f"item.{MOD}.plug_pulsar"] = "Pipe Pulsar"
        self.lang[f"item.{MOD}.plug_timer"] = "Timer"
        self.lang["gate.action.pulsar.constant"] = "Power Pulsar"
        self.lang["gate.action.pulsar.single"] = "Single Power Pulse"
        self.lang["gate.trigger.timer"] = "%s Sec Timer"

    def pulsar_timer_recipes(self) -> None:
        """Upstream BCSiliconRecipes: a pulsar from a redstone engine and two iron ingots at 1000 MJ, a timer from a clock at 500 MJ."""
        self.recipe("assembly/plug_pulsar", {
            "type": rl("assembly"), "result": {"item": rl("plug_pulsar")}, "MJ": 1000,
            "components": [{"ingredient": {"item": rl("redstone_engine")}}, {"ingredient": IRON, "amount": 2}]})
        self.assembly("assembly/plug_timer", "plug_timer", 500, [{"item": "minecraft:clock"}])

    def lenses(self) -> None:
        for name, target in [("lens", "lens"), ("filter", "filter"), ("overlay_lens", "overlay")]:
            self.copy(f"buildcraftsilicon/textures/plugs/{name}.png", f"block/lens/{target}.png")
        self.copy("buildcrafttransport/textures/pipes/colour_item_box.png", "block/pipe/colour_item_box.png")
        for is_filter in [False, True]:
            for colour, upstream, _ in WIRE_COLOURS:
                self.lens_item(colour, upstream, is_filter)
            self.lens_item(None, None, is_filter)
        self.lang.update({f"item.{MOD}.lens": "%s Lens", f"item.{MOD}.filter": "%s Filter", "color.clear": "Clear"})
        for colour, _, title in WIRE_COLOURS + [("clear", None, "Clear")]:
            self.lang[f"item.{MOD}.lens_{colour}"] = f"{title} Lens"
            self.lang[f"item.{MOD}.filter_{colour}"] = f"{title} Filter"

    def write_lang(self) -> None:
        self.lang.update({
            f"itemGroup.{MOD}.main": "BuildCraft",
            f"itemGroup.{MOD}.pipes": "BuildCraft Pipes",
            f"gui.{MOD}.empty": "Empty",
            f"gui.{MOD}.fluid_amount": "%s / %s mB",
            f"tip.{MOD}.fluid_flow": "%s mB/t",
            f"tip.{MOD}.power_flow": "%s MJ/t (%s units/t)",
        })
        write_json(ASSETS / "lang" / "en_us.json", dict(sorted(self.lang.items())))

    # ---- data -----------------------------------------------------------------------------

    def recipe(self, name: str, value: dict) -> None:
        write_json(DATA / MOD / "recipes" / f"{name}.json", value)

    def shaped(self, name: str, result: str, pattern: list[str], key: dict, count: int = 1) -> None:
        output = {"item": rl(result)}
        if count != 1:
            output["count"] = count
        self.recipe(name, {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key, "result": output})

    def shapeless(self, name: str, result: str, ingredients: list, count: int = 1) -> None:
        output = {"item": rl(result)}
        if count != 1:
            output["count"] = count
        self.recipe(name, {"type": "minecraft:crafting_shapeless", "ingredients": ingredients, "result": output})

    def recipes(self) -> None:
        self.shaped("gear_wood", "gear_wood", [" o ", "o o", " o "], {"o": STICKS})
        previous = {"stone": ("wood", COBBLESTONE), "iron": ("stone", IRON), "gold": ("iron", GOLD), "diamond": ("gold", DIAMOND)}
        for material, (inner, outer) in previous.items():
            self.shaped(f"gear_{material}", f"gear_{material}", [" o ", "oio", " o "], {"o": outer, "i": gear(inner)})
        self.shaped("wrench", "wrench", ["I I", " G ", " I "], {"I": IRON, "G": gear("stone")})

        engine_bodies = {"redstone_engine": (PLANKS, "wood"), "stirling_engine": (COBBLESTONE, "stone"),
                         "combustion_engine": (IRON, "iron")}
        for engine, (body, gear_material) in engine_bodies.items():
            self.shaped(engine, engine, ["www", " g ", "GpG"],
                        {"w": body, "g": GLASS, "G": gear(gear_material), "p": {"item": "minecraft:piston"}})

        self.shaped("tank", "tank", ["ggg", "g g", "ggg"], {"g": GLASS})
        self.shaped("pump", "pump", ["iri", "igi", "tbt"],
                    {"i": IRON, "r": REDSTONE, "g": gear("iron"), "t": {"item": rl("tank")}, "b": {"item": "minecraft:bucket"}})
        self.shaped("mining_well", "mining_well", ["iri", "igi", "ipi"],
                    {"i": IRON, "r": REDSTONE, "g": gear("iron"), "p": {"item": "minecraft:iron_pickaxe"}})
        self.shaped("distiller", "distiller", ["rtr", "tgt"],
                    {"r": {"item": "minecraft:redstone_torch"}, "t": {"item": rl("tank")}, "g": gear("diamond")})
        self.shaped("heat_exchange", "heat_exchange", ["igi", "###", "igi"], {"i": IRON, "g": gear("iron"), "#": GLASS})
        self.shaped("quarry", "quarry", ["iri", "gig", "dpd"],
                    {"i": gear("iron"), "r": REDSTONE, "g": gear("gold"), "d": gear("diamond"), "p": {"item": "minecraft:diamond_pickaxe"}})

        self.shaped("marker_volume", "marker_volume", ["l", "t"], {"l": {"item": "minecraft:lapis_lazuli"}, "t": {"item": "minecraft:redstone_torch"}})
        self.shaped("marker_path", "marker_path", ["g", "t"], {"g": {"item": "minecraft:green_dye"}, "t": {"item": "minecraft:redstone_torch"}})
        self.shaped("marker_connector", "marker_connector", ["r", "g", "w"],
                    {"r": {"item": "minecraft:redstone_torch"}, "g": gear("wood"), "w": {"item": rl("wrench")}})
        paper = {"item": "minecraft:paper"}
        self.shaped("template", "template_clean", ["ppp", "pbp", "ppp"], {"p": paper, "b": tags("black_dyes", "dyes/black")})
        self.shaped("blueprint", "blueprint_clean", ["ppp", "pbp", "ppp"], {"p": paper, "b": tags("lapis", "gems/lapis")})
        self.shaped("architect_table", "architect_table", ["bmb", "ycy", "dsd"], {
            "b": tags("black_dyes", "dyes/black"), "m": {"item": rl("marker_volume")}, "y": tags("yellow_dyes", "dyes/yellow"),
            "c": {"item": "minecraft:crafting_table"}, "d": gear("diamond"), "s": {"item": rl("blueprint_clean")}})

        structure = {"item": rl("pipe_structure")}
        self.shaped("plug_blocker", "plug_blocker", ["s"], {"s": structure}, count=4)
        self.shaped("plug_power_adaptor", "plug_power_adaptor", ["sis", "sgs", "srs"],
                    {"s": structure, "i": {"item": "minecraft:gold_ingot"}, "g": gear("stone"), "r": {"item": "minecraft:redstone"}}, count=4)

        self.shapeless("waterproof", "waterproof", [SEALANT_SOURCES])
        self.shapeless("waterproof_from_residue", "waterproof", [{"item": rl("oil_residue_bucket")}], count=8)

        names = {d["name"] for d in PIPES}
        for material, (left, right) in PIPE_MATERIALS.items():
            if f"{material}_item" in names:
                self.shaped(f"pipe_{material}_item", f"pipe_{material}_item", ["lgr"], {"l": left, "g": GLASS, "r": right}, count=8)
        self.shaped("pipe_structure", "pipe_structure", ["cgc"], {"c": COBBLESTONE, "g": {"item": "minecraft:gravel"}}, count=8)
        for definition in PIPES:
            if definition["name"] == "structure":
                continue
            material, flow = definition["name"].rsplit("_", 1)
            if flow == "item":
                continue
            source = f"pipe_{material}_item"
            target = "pipe_" + definition["name"]
            extra = {"item": rl("waterproof")} if flow == "fluid" else REDSTONE
            self.shapeless(target, target, [{"item": rl(source)}, extra])
            self.shapeless(f"{target}_undo", source, [{"item": rl(target)}])

    def silicon_recipes(self) -> None:
        obsidian = {"item": "minecraft:obsidian"}
        self.shaped("laser", "laser", ["rro", "rdd", "rro"], {"r": REDSTONE, "d": DIAMOND, "o": obsidian})
        self.shaped("assembly_table", "assembly_table", ["OdO", "OrO", "OgO"],
                    {"O": obsidian, "d": DIAMOND, "r": REDSTONE, "g": gear("diamond")})
        self.shaped("advanced_crafting_table", "advanced_crafting_table", ["OtO", "OcO", "OrO"],
                    {"O": obsidian, "t": {"item": "minecraft:crafting_table"}, "c": tags("wooden_chests", "chests/wooden"),
                     "r": {"item": rl("redstone_chipset")}})
        self.shaped("integration_table", "integration_table", ["OiO", "OrO", "OgO"],
                    {"O": obsidian, "i": GOLD, "r": {"item": rl("iron_chipset")}, "g": gear("diamond")})
        for name, _, _, mj, extra in CHIPSETS:
            components = [{"ingredient": REDSTONE}] + ([{"ingredient": extra}] if extra else [])
            self.recipe(name, {"type": rl("assembly"), "result": {"item": rl(name)}, "MJ": mj, "components": components})

    def assembly(self, name: str, result: str, mj: int, ingredients: list[dict], count: int = 1) -> None:
        output = {"item": rl(result)}
        if count != 1:
            output["count"] = count
        self.recipe(name, {"type": rl("assembly"), "result": output, "MJ": mj,
                           "components": [{"ingredient": ingredient} for ingredient in ingredients]})

    def lens_recipes(self) -> None:
        """Upstream BCSiliconRecipes: a lens from glass of its colour, a filter from that and iron bars, each at 500 MJ.

        Clear ones take upstream's blockGlass, which stained glass was also registered under.
        """
        bars = {"item": "minecraft:iron_bars"}
        stained = {colour: tags(f"{colour}_glass_blocks", f"glass/{colour}") for colour, _, _ in WIRE_COLOURS}
        stained["clear"] = GLASS + [tag for colour_glass in stained.values() for tag in colour_glass]
        for colour, glass in stained.items():
            self.assembly(f"assembly/lens_{colour}", f"lens_{colour}", 500, [glass])
            self.assembly(f"assembly/filter_{colour}", f"filter_{colour}", 500, [glass, bars])

    def wire_recipes(self) -> None:
        """Upstream BCTransportRecipes: eight wires of a colour from redstone and that dye."""
        for colour, _, _ in WIRE_COLOURS:
            self.assembly(f"assembly/wire_{colour}", f"wire_{colour}", 10_000, [REDSTONE, tags(f"{colour}_dyes", f"dyes/{colour}")], count=8)

    def gate_recipes(self) -> None:
        """Upstream BCSiliconRecipes: crafted basic, iron and nether brick gates, iron gate modifiers, AND/OR swaps and assembly."""
        base = {"r": REDSTONE, "b": {"item": rl("plug_blocker")}}
        for material, m in [("clay_brick", {"item": "minecraft:brick"}), ("iron", IRON), ("nether_brick", {"item": "minecraft:nether_brick"})]:
            gate = gate_name("and", material, "no_modifier")
            self.shaped(gate, gate, [" m ", "mrm", " b "], dict(base, m=m))
        lapis = tags("lapis", "gems/lapis")
        quartz = tags("quartz", "gems/quartz")
        iron_gate = {"item": rl(gate_name("and", "iron", "no_modifier"))}
        for modifier, m in [("lapis", lapis), ("quartz", quartz)]:
            gate = gate_name("and", "iron", modifier)
            self.shaped(gate, gate, [" m ", "mgm", " m "], {"m": m, "g": iron_gate})
        for logic, material, modifier in GATE_VARIANTS:
            if material == "clay_brick":
                continue
            other = "or" if logic == "and" else "and"
            gate = gate_name(logic, material, modifier)
            self.shaped(f"{gate}_from_{other}", gate, ["i"], {"i": {"item": rl(gate_name(other, material, modifier))}})

        chipsets = {"iron": {"item": rl("iron_chipset")}, "gold": {"item": rl("gold_chipset")},
                    "quartz": {"item": rl("quartz_chipset")}, "diamond": {"item": rl("diamond_chipset")}}
        bases = {"iron": (20_000, [chipsets["iron"]]),
                 "nether_brick": (40_000, [chipsets["iron"], {"item": "minecraft:nether_bricks"}]),
                 "gold": (80_000, [chipsets["gold"]])}
        modifiers = {"iron": (40_000, 60_000, 80_000), "nether_brick": (80_000, 100_000, 120_000), "gold": (100_000, 140_000, 180_000)}
        for material, (mj, ingredients) in bases.items():
            for logic in ["and", "or"]:
                gate = gate_name(logic, material, "no_modifier")
                self.assembly(f"assembly/{gate}", gate, mj, ingredients)
                for modifier, extra, modifier_mj in zip(["lapis", "quartz", "diamond"], [lapis, chipsets["quartz"], chipsets["diamond"]],
                                                       modifiers[material]):
                    upgraded = gate_name(logic, material, modifier)
                    self.assembly(f"assembly/{upgraded}", upgraded, modifier_mj, [{"item": rl(gate)}, extra])
        self.assembly("gate_copier", "gate_copier", 500, [{"item": rl("wrench")}, chipsets["iron"]])

    def tag(self, registry: str, namespace: str, path: str, values: list[str | dict]) -> None:
        write_json(DATA / namespace / "tags" / registry / f"{path}.json", {"replace": False, "values": values})

    def data_tags(self) -> None:
        for material, _ in GEARS:
            self.tag("items", "c", f"{material}_gears", [rl(f"gear_{material}")])
            self.tag("items", "forge", f"gears/{material}", [rl(f"gear_{material}")])
        self.tag("items", "forge", "gears", [f"#forge:gears/{m}" for m, _ in GEARS])
        self.tag("items", "c", "wrenches", [rl("wrench")])
        self.tag("items", "forge", "tools/wrench", [rl("wrench")])
        self.tag("items", "forge", "tools", ["#forge:tools/wrench"])
        for (common, forge), values in MATERIAL_TAGS.items():
            self.tag("items", "c", common, values)
            self.tag("items", "forge", forge, values)
        self.tag("fluids", "c", "oil", [rl("oil"), rl("flowing_oil")])
        self.tag("fluids", "forge", "oil", [rl("oil"), rl("flowing_oil")])

        machines = [e for e, _, _ in ENGINES] + ["pump", "mining_well", "distiller", "heat_exchange", "quarry", "frame", "filler", "architect_table", "builder", "electronic_library", "replacer",
                                                    "laser"] + [t for t, _, _, _ in LASER_TABLES] + ["pipe_" + d["name"] for d in PIPES]
        self.tag("blocks", "minecraft", "mineable/pickaxe", [rl(b) for b in machines])

    def loot_tables(self) -> None:
        drops_self = [e for e, _, _ in ENGINES] + ["tank", "pump", "mining_well", "distiller", "heat_exchange", "quarry", "filler", "architect_table", "builder", "electronic_library", "replacer", "marker_volume", "marker_path", "laser"] + [t for t, _, _, _ in LASER_TABLES] + ["pipe_" + d["name"] for d in PIPES]
        for block in drops_self:
            write_json(DATA / MOD / "loot_tables" / "blocks" / f"{block}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": rl(block)}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            })

    def run(self) -> None:
        for path in [*(child for child in ASSETS.glob("*") if child.name != "compat"), DATA / MOD, DATA / "c", DATA / "forge", DATA / "minecraft"]:
            if path.is_dir():
                shutil.rmtree(path)
            elif path.exists():
                path.unlink()
        self.textures()
        self.engines()
        self.pipes()
        self.plugs()
        self.factory()
        self.refinery()
        self.builders()
        self.filler()
        self.architect()
        self.builder()
        self.library()
        self.markers()
        self.silicon()
        self.gates()
        self.wires()
        self.lenses()
        self.pulsars()
        self.fluids()
        self.oil_wells()
        self.oil_biomes()
        self.items()
        self.write_lang()
        self.recipes()
        self.silicon_recipes()
        self.gate_recipes()
        self.wire_recipes()
        self.lens_recipes()
        self.pulsar_timer_recipes()
        self.data_tags()
        self.loot_tables()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--upstream", type=Path, required=True, help="BuildCraft checkout at port.properties' upstream_revision")
    args = parser.parse_args()
    expected = dict(line.split("=", 1) for line in (ROOT / "port.properties").read_text().splitlines() if "=" in line)["upstream_revision"]
    actual = subprocess.run(["git", "-C", str(args.upstream), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    if actual != expected:
        print(f"upstream checkout is at {actual}, expected {expected}", file=sys.stderr)
        return 1
    Generator(args.upstream).run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
