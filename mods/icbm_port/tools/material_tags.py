"""Generate additive material tags and recipe-book predicates from audited data."""
import json
from pathlib import Path


def write_json(root, path, data):
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2) + "\n")


def write_material_tags(root):
    mapping = json.loads((Path(__file__).parent / "ore_dictionary_mapping.json").read_text())
    for material in mapping["materials"].values():
        # Each public spelling publishes real members, never mutual aliases.
        # This keeps the tag graph acyclic while consumers accept either spelling.
        for tag in material["exports"]:
            namespace, path = tag.split(":", 1)
            write_json(root, f"data/{namespace}/tags/items/{path}.json",
                       {"replace": False, "values": material["members"]})
        namespace, path = material["tag"].split(":", 1)
        write_json(root, f"data/{namespace}/tags/items/{path}.json", {
            "replace": False,
            "values": [{"id": "#" + tag, "required": False} for tag in material["imports"]],
        })


def write_recipe(root, identifier, recipe):
    write_json(root, f"data/icbm_port/recipes/{identifier}.json", recipe)
    ingredients = list(recipe.get("key", {}).values()) if "key" in recipe else recipe.get(
        "ingredients", [recipe["ingredient"]] if "ingredient" in recipe else [])
    if not ingredients:
        return
    first = ingredients[0] if isinstance(ingredients[0], list) else [ingredients[0]]
    criteria = {}
    for index, ingredient in enumerate(first):
        predicate = {"tag": ingredient["tag"]} if "tag" in ingredient else {
            "items": [ingredient["item"]]}
        criteria[f"has_ingredient_{index}"] = {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [predicate]},
        }
    recipe_id = "icbm_port:" + identifier
    criteria["has_the_recipe"] = {
        "trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe_id}}
    advancement = {
        "parent": "minecraft:recipes/root", "criteria": criteria,
        "requirements": [list(criteria)], "rewards": {"recipes": [recipe_id]},
    }
    if "fabric:load_conditions" in recipe:
        advancement["fabric:load_conditions"] = recipe["fabric:load_conditions"]
    write_json(root, f"data/icbm_port/advancements/recipes/{identifier}.json", advancement)
