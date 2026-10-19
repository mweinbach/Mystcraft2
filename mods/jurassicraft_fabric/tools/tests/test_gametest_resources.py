"""Ensure Fabric GameTests can resolve their explicitly named structures."""
from pathlib import Path
import re
import unittest

MOD = Path(__file__).resolve().parents[2]
JAVA = MOD / "src/gametest/java"
DATA = MOD / "src/gametest/resources/data"
TEMPLATE = re.compile(r'@GameTest\s*\([^)]*?\btemplate\s*=\s*"([^"]+)"', re.DOTALL)


class GameTestStructureResourcesTest(unittest.TestCase):
    def test_named_templates_have_fabric_snbt_resources(self):
        references = {}
        for source in JAVA.rglob("*.java"):
            for name in TEMPLATE.findall(source.read_text()):
                references.setdefault(name, []).append(source.relative_to(MOD))
        self.assertTrue(references, "No explicitly named GameTest templates were found")
        missing = []
        for name, sources in references.items():
            namespace, separator, path = name.partition(":")
            self.assertTrue(separator, f"Template must include namespace: {name}")
            resource = DATA / namespace / "gametest/structures" / f"{path}.snbt"
            if not resource.is_file():
                missing.append(f"{name}: {resource.relative_to(MOD)} (referenced by {sources})")
        self.assertFalse(missing, "Missing Fabric GameTest structures:\n" + "\n".join(missing))


if __name__ == "__main__":
    unittest.main()
