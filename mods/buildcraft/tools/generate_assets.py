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
         item_texture: str | None = None) -> dict:
    texture = texture or name
    return {"name": name, "faces": faces or {d: texture for d in DIRECTIONS}, "special": special,
            "item": item_texture or texture}


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
    ("slime_balls", "slimeballs"): ["minecraft:slime_ball"],
    ("wooden_rods", "rods/wooden"): ["minecraft:stick"],
}


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
            material, flow = definition["name"].rsplit("_", 1)
            self.lang[f"block.{MOD}.{name}"] = f"{MATERIAL_NAMES[material]} {FLOW_NAMES[flow]} Pipe"

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

    def markers(self) -> None:
        for kind in ["volume", "path"]:
            self.copy(f"buildcraftcore/textures/blocks/marker_{kind}.png", f"block/marker_{kind}.png")
            self.copy(f"buildcraftcore/textures/items/marker_{kind}.png", f"item/marker_{kind}.png")
        self.copy("buildcraftcore/textures/items/marker_connector.png", "item/marker_connector.png")
        for laser in ["marker_volume_connected", "marker_volume_possible", "marker_volume_signal", "marker_path_connected",
                      "marker_path_possible", "marker_default_possible"]:
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
        self.lang.update({f"block.{MOD}.marker_volume": "Land Mark", f"block.{MOD}.marker_path": "Path Mark",
                          f"item.{MOD}.marker_connector": "Marker Connector"})

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

        self.shapeless("waterproof", "waterproof", [SEALANT_SOURCES])
        self.shapeless("waterproof_from_residue", "waterproof", [{"item": rl("oil_residue_bucket")}], count=8)

        names = {d["name"] for d in PIPES}
        for material, (left, right) in PIPE_MATERIALS.items():
            if f"{material}_item" in names:
                self.shaped(f"pipe_{material}_item", f"pipe_{material}_item", ["lgr"], {"l": left, "g": GLASS, "r": right}, count=8)
        for definition in PIPES:
            material, flow = definition["name"].rsplit("_", 1)
            if flow == "item":
                continue
            source = f"pipe_{material}_item"
            target = "pipe_" + definition["name"]
            extra = {"item": rl("waterproof")} if flow == "fluid" else REDSTONE
            self.shapeless(target, target, [{"item": rl(source)}, extra])
            self.shapeless(f"{target}_undo", source, [{"item": rl(target)}])

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

        machines = [e for e, _, _ in ENGINES] + ["pump", "mining_well", "distiller", "heat_exchange", "quarry", "frame"] + ["pipe_" + d["name"] for d in PIPES]
        self.tag("blocks", "minecraft", "mineable/pickaxe", [rl(b) for b in machines])

    def loot_tables(self) -> None:
        drops_self = [e for e, _, _ in ENGINES] + ["tank", "pump", "mining_well", "distiller", "heat_exchange", "quarry", "marker_volume", "marker_path"] + ["pipe_" + d["name"] for d in PIPES]
        for block in drops_self:
            write_json(DATA / MOD / "loot_tables" / "blocks" / f"{block}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": rl(block)}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            })

    def run(self) -> None:
        for path in [ASSETS, DATA / MOD, DATA / "c", DATA / "forge", DATA / "minecraft"]:
            if path.exists():
                shutil.rmtree(path)
        self.textures()
        self.engines()
        self.pipes()
        self.factory()
        self.refinery()
        self.builders()
        self.markers()
        self.fluids()
        self.oil_wells()
        self.oil_biomes()
        self.items()
        self.write_lang()
        self.recipes()
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
