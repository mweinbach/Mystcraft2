#!/usr/bin/env python3
"""Generate the dinosaur steak and growth-serum resources."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/jurassicraft"
DATA = ROOT / "src/main/resources/data/jurassicraft/recipes"
UP = ROOT / "upstream/src/main/resources/assets/jurassicraft/textures/items"


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


for item, texture in (("growth_serum", "growth_serum"), ("dinosaur_steak", "steak/velociraptor"), ("pregnancy_test", "birthing_wand"),
                      ("breeding_wand", "breeding_wand"), ("birthing_wand", "birthing_wand")):
    destination = ASSETS / f"textures/items/{texture}.png"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(UP / f"{texture}.png", destination)
    write(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jurassicraft:items/{texture}"}})
write(ROOT / "tools/lang/dinosaur_supplies.json", {"item.jurassicraft.growth_serum": "Growth Serum", "item.jurassicraft.dinosaur_steak": "Dinosaur Steak",
    "item.jurassicraft.pregnancy_test": "Pregnancy Test", "item.jurassicraft.breeding_wand": "Breeding Wand",
    "item.jurassicraft.birthing_wand": "Birthing Wand", "message.jurassicraft.breeding_wand.selected": "First dinosaur selected.",
    "message.jurassicraft.breeding_wand.success": "Breeding started.", "message.jurassicraft.breeding_wand.invalid": "These dinosaurs cannot breed.",
    "message.jurassicraft.birthing_wand.success": "Delivery started.", "message.jurassicraft.birthing_wand.invalid": "This dinosaur is not pregnant.",
    "message.jurassicraft.pregnancy_test.male": "This dinosaur is male.",
    "message.jurassicraft.pregnancy_test.pregnant": "This dinosaur is pregnant.", "message.jurassicraft.pregnancy_test.not_pregnant": "This dinosaur is not pregnant."})
write(DATA / "dinosaur_steak_from_smelting.json", {"type": "minecraft:smelting", "ingredient": {"item": "jurassicraft:dinosaur_meat"},
    "result": "jurassicraft:dinosaur_steak", "experience": 5.0, "cookingtime": 200})
write(DATA / "growth_serum.json", {"type": "minecraft:crafting_shapeless", "ingredients": [
    {"item": "minecraft:golden_carrot"}, {"item": "jurassicraft:empty_syringe"}, {"item": "minecraft:water_bucket"},
    [{"item": f"minecraft:{food}"} for food in ("cooked_beef", "cooked_chicken", "cooked_cod", "cooked_salmon", "cooked_mutton", "cooked_porkchop", "cooked_rabbit")]
    + [{"item": f"jurassicraft:{name}_steak"} for name in ("velociraptor", "coelacanth", "microraptor", "brachiosaurus",
       "mussaurus", "dilophosaurus", "gallimimus", "parasaurolophus", "triceratops", "tyrannosaurus")]
    + [{"item": "jurassicraft:dinosaur_steak"}]], "result": {"item": "jurassicraft:growth_serum"}})
write(DATA / "pregnancy_test.json", {"type": "minecraft:crafting_shaped", "pattern": ["P  ", " R ", "  W"],
    "key": {"P": {"item": "minecraft:pink_wool"}, "R": {"tag": "forge:dusts/redstone"}, "W": {"item": "minecraft:white_wool"}},
    "result": {"item": "jurassicraft:pregnancy_test"}})
