#!/usr/bin/env python3
"""Copy the upstream journal art and generate the field guide's standalone resources."""
from pathlib import Path
import json
import shutil

root = Path(__file__).resolve().parents[1]
upstream = root / "upstream/src/main/resources/assets/jurassicraft/textures"
assets = root / "src/main/resources/assets/jurassicraft"
data = root / "src/main/resources/data/jurassicraft/recipes"

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf8")

for source, target in [("items/field_guide.png", "items/field_guide.png"),
                       ("field_guide/background.png", "field_guide/background.png")]:
    destination = assets / "textures" / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(upstream / source, destination)

write(assets / "models/item/field_guide.json", {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "jurassicraft:items/field_guide"}
})
write(data / "field_guide.json", {
    "type": "minecraft:crafting_shaped",
    "pattern": ["ABA", "CCC", "ABA"],
    "key": {"A": {"item": "minecraft:light_blue_wool"},
            "B": {"item": "minecraft:bone"}, "C": {"item": "minecraft:paper"}},
    "result": {"item": "jurassicraft:field_guide"}
})

prefix = "gui.jurassicraft.field_guide."
language = {
    "item.jurassicraft.field_guide": "Field Guide",
    prefix + "title": "JurassiCraft Field Guide",
    prefix + "previous": "Previous",
    prefix + "next": "Next",
    prefix + "page": "%s / %s",
    prefix + "about_species": "About this species",
    prefix + "health": "Health: %s / %s",
    prefix + "growth": "Growth: %s%%",
    prefix + "stage_sex": "%s / %s",
    prefix + "stage_infant": "Infant", prefix + "stage_juvenile": "Juvenile",
    prefix + "stage_adolescent": "Adolescent", prefix + "stage_adult": "Adult",
    prefix + "days_lived": "Age: %s days",
    prefix + "sex": "Sex: %s",
    prefix + "hunger": "Food: %s",
    prefix + "thirst": "Water: %s",
    prefix + "hunger_detail": "Food: %s%% (%s)",
    prefix + "thirst_detail": "Water: %s%% (%s)",
    prefix + "male": "Male", prefix + "female": "Female",
    prefix + "hungry": "Hungry", prefix + "fed": "Fed",
    prefix + "thirsty": "Thirsty", prefix + "hydrated": "Hydrated",
    prefix + "field_notes": "Field notes",
    prefix + "diet": "Diet: %s", prefix + "diet_fish": "Fish",
    prefix + "diet_meat": "Meat", prefix + "diet_plants": "Plants",
    prefix + "diet_insects": "Insects", prefix + "diet_krill": "Krill",
    prefix + "diet_plankton": "Plankton", prefix + "diet_none": "None known",
    prefix + "warning_low_health": "Low health", prefix + "warning_poisoned": "Poisoned",
    prefix + "warning_low_air": "Low air",
    prefix + "state": "State: %s", prefix + "state_awake": "Awake",
    prefix + "state_sleeping": "Sleeping", prefix + "state_tranquilized": "Tranquilized",
    prefix + "state_carcass": "Carcass", prefix + "imprinted": "Imprinted",
    prefix + "wild": "Unimprinted", prefix + "order": "Order: %s",
    prefix + "order_wander": "Wander", prefix + "order_follow": "Follow", prefix + "order_sit": "Stay",
    prefix + "lore_hint": "Turn the page for field notes on this species.",
}

pages = {
    "introduction": (
        ("Welcome", "This guide follows a fossil from the field to a living animal or ancient plant. Use it on a dinosaur to inspect its health, growth, sex, food and water."),
        ("The route", "Collect and clean fossils, grind them for tissue, then use laboratory machines to sequence, combine and synthesize DNA. Finally, grow an embryo or plant callus.")),
    "fossils": (
        ("Collect fossils", "Use Plaster and Bandage on a dinosaur fossil block, then mine the encased fossil. Mine plant fossil blocks directly. Amber can also contain samples."),
        ("Clean and grind", "Put an encased fossil or plant fossil block in a Cleaning Station with water. Grind the resulting fossil bones, plant fossils or twigs for a chance of tissue.")),
    "nests": (
        ("Ancient nests", "Fossil nests can also be wrapped with Plaster and Bandage. Mine the wrapped nest and clean it with water to uncover fossilized eggs."),
        ("Fossilized eggs", "Put a fossilized egg in the Fossil Grinder. It may yield soft tissue from a prehistoric animal; other attempts produce flint or bone meal.")),
    "laboratory": (
        ("Sequence tissue", "Put tissue and a blank Storage Disc in the DNA Sequencer to make a species-specific DNA disc. The sample's DNA quality is shown on the disc."),
        ("Extract samples", "The DNA Extractor also accepts mosquito amber, aphid amber, sea lamprey or suitable dinosaur meat, together with a blank Storage Disc. Each can produce a DNA disc.")),
    "synthesis": (
        ("Combine DNA", "Use the DNA Combinator / Hybridizer to combine two discs of the same species and type. Their quality adds up to a maximum of 100%. Keep combining until you have a complete disc."),
        ("Synthesize", "Put a 100% disc, an Empty Test Tube and DNA Base Material in the DNA Synthesizer. It produces a DNA tube and keeps the disc. Smelt a water bottle to make the base material.")),
    "hatching": (
        ("Make an embryo", "For an animal, put its DNA tube, a Petri Dish and an Empty Syringe in the Embryonic Machine. Put the resulting embryo syringe and a chicken egg in the Embryo Calcification Machine."),
        ("Incubate eggs", "Place an egg and Peat Moss in an Incubator. Above 50 gives a male; 50 or below gives a female. Use the hatched egg on a block to release the animal.")),
    "coelacanth": (
        ("Grow a coelacanth", "Coelacanths grow in a Cultivator rather than a calcified egg. Insert the coelacanth embryo syringe and two water buckets. The machine returns the empty buckets."),
        ("Water and food", "Add food until each of the four nutrient bars is at least half full. Set temperature above 50 for a male or 50 and below for a female. Use the finished coelacanth in water to release it.")),
    "plants": (
        ("Plant cells", "Use an Empty Syringe on a flower or other small plant to collect cells. Smelt gracilaria for Liquid Agar, then craft an agar Petri Dish and add plant cells."),
        ("Grow a plant", "Put a plant DNA tube, Plant Cells Petri Dish and Empty Syringe in an Embryonic Machine to make callus. Use it on farmland; aquatic callus needs source water above suitable sediment.")),
    "care": (
        ("Feed your animals", "Feed hungry dinosaurs by hand. Meat-eaters eat meat; plant-eaters eat plants, leaves, saplings and seeds. Feeders can supply land animals."),
        ("More foods", "Young carnivores and Gallimimus eat insects; Microraptors eat them at any age. Coelacanths eat fish, krill and plankton. Provide water and contain predators.")),
    "orders": (
        ("Imprinting", "Some species imprint on the player who releases them from a hatched egg. Sneak while releasing one to keep it unimprinted."),
        ("Give an order", "Use an empty hand on your imprinted dinosaur to choose wander, follow or stay. Sneak-use an empty hand on a mature owned animal to open its inventory.")),
    "breeding": (
        ("Healthy pairs", "A healthy, well-fed and watered mature male and female of the same species can breed. Keep them together and give them space and time."),
        ("The next generation", "Land animals lay eggs that hatch after some time; use an egg to pick it up. Coelacanths give birth in water. Young inherit DNA from their parents.")),
    "park": (
        ("Fences", "Join fence bases and place a pole above one. Power that base with redstone to electrify connected wires within eight blocks. Contact can shock animals and players."),
        ("Dart gun", "Use a dart gun once to load up to twelve darts from your inventory, then use it again to fire. Tranquilizer darts put dinosaurs to sleep temporarily.")),
    "tours": (
        ("Vehicles", "Use a vehicle item on solid ground, then use the vehicle to ride. The driver uses movement keys. In a helicopter, jump rises and sprint descends."),
        ("Tour rails", "Lay connected tour rails on solid blocks. An Explorer placed on the route can follow them; the colored rails have different speeds.")),
    "collecting": (
        ("Parks and journals", "Look for raptor paddocks and the visitor centre. Its rooms can contain the personal journals of the park staff, with entries to read and collect."),
        ("Other discoveries", "Use a fine net for small aquatic food, keep insects in a bug crate, and assemble fossil bones into skeleton displays. Place action figures as decorations.")),
}
for name, (left, right) in pages.items():
    for side, (title, body) in (("left", left), ("right", right)):
        language[prefix + name + "." + side + ".title"] = title
        language[prefix + name + "." + side + ".body"] = body

# Preserve the full original field notes for each enabled species. One upstream
# Mussaurus quote is already a replacement character in the source file.
names = ("velociraptor", "coelacanth", "microraptor", "brachiosaurus", "mussaurus",
         "dilophosaurus", "gallimimus", "parasaurolophus", "triceratops", "tyrannosaurus")
upstream_language = {}
for line in (root / "upstream/src/main/resources/assets/jurassicraft/lang/en_US.lang").read_text(encoding="utf8").splitlines():
    key, separator, value = line.partition("=")
    if separator:
        upstream_language[key] = value
for name in names:
    language[prefix + "species." + name] = upstream_language["info.jurassicraft." + name + ".name"].replace("\ufffd", '"')

write(root / "tools/field_guide/en_us.fragment.json", language)
