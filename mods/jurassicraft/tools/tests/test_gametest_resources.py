"""Ensure each loader's GameTests resolve their named structures from GameTest-only resources."""
from pathlib import Path
import re
import unittest

MOD = Path(__file__).resolve().parents[2]
TEMPLATE = re.compile(r'@GameTest\s*\([^)]*?\btemplate\s*=\s*"([^"]+)"', re.DOTALL)


def references(source_set):
    found = {}
    for source in (MOD / "src" / source_set / "java").rglob("*.java"):
        for name in TEMPLATE.findall(source.read_text()):
            found.setdefault(name, []).append(source.relative_to(MOD))
    return found


class GameTestStructureResourcesTest(unittest.TestCase):
    def test_fabric_templates_have_snbt_resources(self):
        data = MOD / "src/gametestFabric/resources/data"
        found = references("gametestFabric")
        self.assertTrue(found, "No explicitly named Fabric GameTest templates were found")
        missing = []
        for name, sources in found.items():
            namespace, separator, path = name.partition(":")
            self.assertTrue(separator, f"Fabric template must include its namespace: {name}")
            resource = data / namespace / "gametest/structures" / f"{path}.snbt"
            if not resource.is_file():
                missing.append(f"{name}: {resource.relative_to(MOD)} (referenced by {sources})")
        self.assertFalse(missing, "Missing Fabric GameTest structures:\n" + "\n".join(missing))

    def test_forge_templates_have_nbt_resources(self):
        structures = MOD / "src/gametestForge/resources/data/jurassicraft/structures"
        found = references("gametestForge")
        self.assertTrue(found, "No explicitly named Forge GameTest templates were found")
        missing = [f"{name} (referenced by {sources})" for name, sources in found.items()
                   if not (structures / f"{name}.nbt").is_file()]
        self.assertFalse(missing, "Missing Forge GameTest structures:\n" + "\n".join(missing))

    def test_production_resources_hold_no_test_structures(self):
        for root in ("main", "forge", "fabric"):
            resources = MOD / "src" / root / "resources"
            leaked = [path.relative_to(MOD) for pattern in ("*_test.nbt", "*_test.snbt")
                      for path in resources.rglob(pattern)]
            self.assertFalse(leaked, f"GameTest structures in production resources: {leaked}")


if __name__ == "__main__":
    unittest.main()
