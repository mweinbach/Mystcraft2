"""Catch a vanilla block model whose parent is not a block model.

Minecraft loads item and block JSON as BlockModel. BlockModel.getMaterials throws
IllegalStateException when a parent resolves to something else, and that aborts
the client resource reload. Fabric's machine OBJ provider is such a parent.
"""

import json
import re
import unittest
from pathlib import Path

from layout import MOD, OVERLAYS, SHARED


LOADER = MOD / "src/fabric/java/ports/jurassicraft/fabric/client/MachineModels.java"
NON_BLOCK_PARENTS = {"builtin/entity", "builtin/generated"}


def model_index(loader):
    found = {}
    for root in (SHARED, OVERLAYS[loader]):
        assets = root / "assets"
        if not assets.is_dir():
            continue
        for path in assets.rglob("models/**/*.json"):
            relative = path.relative_to(assets).as_posix()
            namespace, _, rest = relative.partition("/")
            model_path = rest.removeprefix("models/").removesuffix(".json")
            found[f"{namespace}:{model_path}"] = json.loads(path.read_text(encoding="utf-8"))
    return found


def machine_names():
    source = LOADER.read_text(encoding="utf-8")
    declared = re.search(r"Set\.of\((.*?)\);", source, re.S)
    if declared is None or "loadItem(" not in source:
        raise AssertionError("Fabric machine loader no longer substitutes item parents")
    return set(re.findall(r'"([a-z0-9_]+)"', declared.group(1)))


def parent_id(model_id, parent):
    if ":" in parent:
        return parent
    return f"{model_id.split(':', 1)[0]}:{parent}"


def custom_obj(model_id, models, names):
    model = models.get(model_id)
    path = model_id.split(":", 1)[1]
    return (isinstance(model, dict) and "jurassicraft_obj" in model and path.startswith("block/")
            and path.removeprefix("block/") in names)


def substituted(model_id, models, names):
    """Models the Fabric provider replaces, so vanilla never loads them as BlockModel."""
    if custom_obj(model_id, models, names):
        return True
    model = models.get(model_id)
    path = model_id.split(":", 1)[1]
    if not path.startswith("item/") or not isinstance(model, dict) or not isinstance(model.get("parent"), str):
        return False
    return custom_obj(parent_id(model_id, model["parent"]), models, names)


class BlockModelParentTests(unittest.TestCase):
    def test_fabric_block_models_do_not_parent_custom_or_item_models(self):
        names = machine_names()
        models = model_index("fabric")
        obj = {model_id for model_id in models if custom_obj(model_id, models, names)}
        self.assertEqual(obj, {f"jurassicraft:block/{name}" for name in names})
        errors = []
        for model_id, model in sorted(models.items()):
            if substituted(model_id, models, names) or not isinstance(model, dict):
                continue
            parent = model.get("parent")
            if not isinstance(parent, str):
                continue
            seen = set()
            current = parent_id(model_id, parent)
            owner = model_id
            while current and current not in seen:
                seen.add(current)
                path = current.split(":", 1)[1]
                if path in NON_BLOCK_PARENTS or path.startswith("item/"):
                    if owner.split(":", 1)[1].startswith("block/"):
                        errors.append(f"{owner} parents non-block model {current}")
                if custom_obj(current, models, names):
                    errors.append(f"{owner} parents Fabric OBJ model {current}")
                    break
                parent_model = models.get(current)
                if not isinstance(parent_model, dict) or not isinstance(parent_model.get("parent"), str):
                    break
                current = parent_id(current, parent_model["parent"])
        self.assertEqual(errors, [])

    def test_shared_block_models_do_not_parent_item_or_builtin_models(self):
        errors = []
        for loader in OVERLAYS:
            models = model_index(loader)
            for model_id, model in models.items():
                if not model_id.split(":", 1)[1].startswith("block/") or not isinstance(model, dict):
                    continue
                parent = model.get("parent")
                if not isinstance(parent, str):
                    continue
                path = parent_id(model_id, parent).split(":", 1)[1]
                if path in NON_BLOCK_PARENTS or path.startswith("item/"):
                    errors.append(f"{loader}: {model_id} parents {parent}")
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
