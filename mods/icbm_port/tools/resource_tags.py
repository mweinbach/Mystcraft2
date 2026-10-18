"""Resolve item-tag graphs and Fabric recipe conditions for resource validation."""
import json


class TagIndex:
    def __init__(self, files, vanilla):
        self.files = files
        self.vanilla = vanilla
        self.cache = {}

    def definitions(self, identifier):
        namespace, path = identifier.split(":", 1)
        key = f"data/{namespace}/tags/items/{path}.json"
        definitions = []
        if key in self.vanilla.namelist():
            definitions.append(json.loads(self.vanilla.read(key)))
        if key in self.files:
            definitions.append(self.files[key])
        return definitions

    def resolve(self, identifier, visiting=()):
        if identifier in visiting:
            raise ValueError("Cyclic item tags: " + " -> ".join((*visiting, identifier)))
        if identifier in self.cache:
            return self.cache[identifier]
        definitions = self.definitions(identifier)
        if not definitions:
            raise ValueError("Missing item tag " + identifier)
        entries = []
        for data in definitions:
            if data.get("replace", False):
                entries.clear()
            entries.extend(data["values"])
        members = set()
        for entry in entries:
            name = entry["id"] if isinstance(entry, dict) else entry
            required = entry.get("required", True) if isinstance(entry, dict) else True
            if name.startswith("#"):
                target = name[1:]
                if not required and not self.definitions(target):
                    continue
                members.update(self.resolve(target, (*visiting, identifier)))
            else:
                members.add(name)
        self.cache[identifier] = frozenset(members)
        return self.cache[identifier]

    def condition(self, condition):
        kind = condition["condition"]
        if kind == "fabric:tags_populated":
            if condition.get("registry", "minecraft:item") != "minecraft:item":
                raise ValueError("Unsupported non-item condition registry")
            return all(self.definitions(tag) and self.resolve(tag) for tag in condition["values"])
        if kind == "fabric:not":
            return not self.condition(condition["value"])
        if kind == "fabric:or":
            return any(self.condition(child) for child in condition["values"])
        if kind == "fabric:and":
            return all(self.condition(child) for child in condition["values"])
        raise ValueError("Unsupported resource condition " + kind)

    def enabled(self, data):
        return all(self.condition(condition) for condition in data.get("fabric:load_conditions", []))
