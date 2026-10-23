"""Keep the port CI workflow covering every port and the required check name."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


def matrix_entry(text, port):
    marker = f"- id: {port}\n"
    start = text.index(marker)
    rest = text[start + len(marker):]
    nxt = rest.find("\n          - id:")
    return rest if nxt == -1 else rest[:nxt]


class CiWorkflowTests(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text(encoding="utf-8")

    def test_push_does_not_rebuild_pull_request_branches(self):
        head, _, _ = self.text.partition("\njobs:")
        self.assertRegex(head, r"push:\n\s+branches:\n\s+- main\n")
        self.assertIn("\n  pull_request:\n", head)
        self.assertIn("\n  workflow_dispatch:\n", head)

    def test_required_check_job_is_still_named_build(self):
        self.assertTrue(self.text.startswith("name: Build ports\n"))
        self.assertIn("\n  build:\n", self.text)
        self.assertIn("needs: [tooling, port]\n", self.text)
        for minutes in re.findall(r"timeout-minutes:\s*(\d+)", self.text):
            self.assertLessEqual(int(minutes), 40)

    def test_matrix_covers_every_port_and_preserves_each_check(self):
        ports = sorted(path.parent.name for path in (ROOT / "mods").glob("*/port.properties"))
        ids = re.findall(r"^          - id: ([a-z][a-z0-9_]{1,63})\s*$", self.text, re.M)
        self.assertEqual(sorted(ids), ports)
        for snippet in (
            "python3 scripts/ports.py check",
            "python3 -m unittest discover -s scripts/tests -v",
            "python3 scripts/emc.py check",
            "python3 -m unittest discover -s mods/icbm_port/tools/tests -v",
            "python3 -m unittest discover -s mods/jurassicraft/tools/tests -v",
            "python3 mods/mekanism/scripts/generate_fabric_tags.py --check",
            "./gradlew --no-daemon listPorts",
            ":icbm_port:build",
            "mods/icbm_port/tools/validate_resources.py",
            ":icbm_port:runGametest",
            "-PwithItemAlchemy",
            ":icbm_port:runMaterialGametest",
            "scripts/check_loader_parity.py",
            ":runGameTestServer",
            "runGametest",
            "test_built_jar.py",
            "minecraft-1.19.2-ports",
            "gametest-reports-",
            "cache-read-only:",
        ):
            self.assertIn(snippet, self.text)
        self.assertIn('forge_props: ""', matrix_entry(self.text, "mekanism"))
        self.assertNotIn("withProjectE", matrix_entry(self.text, "mekanism"))
        fluidphysics = matrix_entry(self.text, "fluidphysics")
        self.assertIn("kind: fabric", fluidphysics)
        self.assertNotIn("forge_props", fluidphysics)
        self.assertNotIn("fabric_props", fluidphysics)
        jurassicraft = matrix_entry(self.text, "jurassicraft")
        self.assertIn("forge_props: -PwithProjectE", jurassicraft)
        self.assertIn("fabric_props: -PwithJade -PwithEmi -PwithItemAlchemy", jurassicraft)
        for port in ("advancedgenetics", "mystcraft", "buildcraft", "extremereactors"):
            entry = matrix_entry(self.text, port)
            self.assertIn("forge_props: -PwithProjectE", entry)
            self.assertIn("fabric_props: -PwithItemAlchemy", entry)
            self.assertNotIn("withJade", entry)


if __name__ == "__main__":
    unittest.main()
