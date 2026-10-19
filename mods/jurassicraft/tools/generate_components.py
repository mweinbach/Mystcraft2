#!/usr/bin/env python3
"""Copy laboratory component textures and produce their modern recipes."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/jurassicraft"
UPSTREAM = ROOT / "upstream/src/main/resources/assets/jurassicraft/textures/items"
DATA = RES / "data/jurassicraft/recipes"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


items = {
    "basic_circuit": ("basic_circuit", "Basic Circuit"),
    "advanced_circuit": ("advanced_circuit", "Advanced Circuit"),
    "computer_screen": ("computer_screen", "Computer Screen"),
    "keyboard": ("keyboard", "Keyboard"),
    "laser": ("laser", "Laser"),
    "dna_analyzer": ("dna_analyzer", "DNA Analyzer"),
    "disc_reader": ("disc_reader", "Disc Reader"),
    "empty_test_tube": ("dna/empty_test_tube", "Empty Test Tube"),
    "dna_nucleotides": ("dna_base_material", "DNA Base Material"),
    "petri_dish": ("petri_dish", "Petri Dish"),
    "petri_dish_agar": ("petri_dish_agar", "Petri Dish Agar"),
    "liquid_agar": ("liquid_agar", "Liquid Agar"),
    "plant_cells": ("plant_cells", "Plant Cells"),
    "plant_cells_petri_dish": ("petri_dish_plant_cells", "Plant Cells Petri Dish"),
    "empty_syringe": ("syringe/empty_syringe", "Empty Syringe"),
}
for name, (texture, _) in items.items():
    destination = ASSETS / f"textures/items/{texture}.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UPSTREAM / f"{texture}.png", destination)
    write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated",
          "textures": {"layer0": f"jurassicraft:items/{texture}"}})
write(ROOT / "tools/lang/components.json", {f"item.jurassicraft.{name}": label for name, (_, label) in items.items()})


def ingredient(value):
    return {"tag" if value.startswith("#") else "item": value.removeprefix("#")}


def shaped(name, pattern, key, count=1):
    write(DATA / f"{name}.json", {"type": "minecraft:crafting_shaped", "pattern": pattern,
          "key": {k: ingredient(v) for k, v in key.items()},
          "result": {"item": f"jurassicraft:{name}", "count": count}})


def shapeless(name, ingredients, count=1):
    write(DATA / f"{name}.json", {"type": "minecraft:crafting_shapeless",
          "ingredients": [ingredient(value) for value in ingredients],
          "result": {"item": f"jurassicraft:{name}", "count": count}})


iron = "#forge:nuggets/iron"
shapeless("basic_circuit", [iron] * 4 + ["minecraft:gold_nugget"] * 4 + ["minecraft:redstone"], 2)
shapeless("advanced_circuit", ["jurassicraft:basic_circuit"] * 2 + [iron, "minecraft:gold_nugget", "minecraft:redstone"])
shaped("computer_screen", ["ABA", "CDE", "FGA"], {"A": "minecraft:iron_ingot", "B": "minecraft:green_dye", "C": "minecraft:red_dye", "D": "minecraft:glass_pane", "E": "minecraft:lapis_lazuli", "F": "jurassicraft:basic_circuit", "G": "minecraft:quartz"})
shaped("keyboard", ["AAA", "AAA", "BCD"], {"A": "minecraft:stone_button", "B": "minecraft:stone_pressure_plate", "C": "minecraft:stone_slab", "D": "jurassicraft:basic_circuit"})
shaped("laser", ["ABA", "ACA", "ADA"], {"A": iron, "B": "minecraft:diamond", "C": "jurassicraft:basic_circuit", "D": "minecraft:redstone"})
shaped("dna_analyzer", ["ABA", "CDA", "AAA"], {"A": iron, "B": "minecraft:glass", "C": "jurassicraft:advanced_circuit", "D": "jurassicraft:laser"})
shaped("disc_reader", ["ABC", "D  ", "DDE"], {"A": "jurassicraft:advanced_circuit", "B": "jurassicraft:laser", "C": "minecraft:iron_ingot", "D": iron, "E": "minecraft:stone_button"})
shaped("empty_test_tube", ["A", "A"], {"A": "minecraft:glass"}, 8)
shaped("empty_syringe", [" A ", " B ", "AAA"], {"A": iron, "B": "jurassicraft:empty_test_tube"})
shaped("petri_dish", ["A A", "AAA"], {"A": "minecraft:glass_pane"}, 4)
shapeless("petri_dish_agar", ["jurassicraft:petri_dish"] * 4 + ["jurassicraft:liquid_agar"], 4)
shapeless("plant_cells_petri_dish", ["jurassicraft:plant_cells", "jurassicraft:petri_dish_agar"])

# Forge's partial NBT ingredient restricts this to water potions; a plain potion
# ingredient would silently consume every potion type on Minecraft 1.19.2.
write(DATA / "dna_nucleotides_from_water.json", {"type": "minecraft:smelting",
      "ingredient": {"type": "forge:partial_nbt", "item": "minecraft:potion", "nbt": {"Potion": "minecraft:water"}},
      "result": "jurassicraft:dna_nucleotides", "experience": 1.0, "cookingtime": 200})
write(DATA / "liquid_agar_from_gracilaria.json", {"type": "minecraft:smelting",
      "ingredient": {"item": "jurassicraft:gracilaria"}, "result": "jurassicraft:liquid_agar",
      "experience": 0.5, "cookingtime": 200})
