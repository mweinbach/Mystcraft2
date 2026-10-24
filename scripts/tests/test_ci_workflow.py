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
            "python3 mods/projecte/scripts/generate_fabric_tags.py --check",
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
            "tools/tekxit4-probe/mixin_audit.py --targets tools/tekxit4-probe/tekxit4-exclusive-mixins.json",
            "python3 scripts/check_unmapped_overrides.py collected-jars",
            "gametest-reports-",
            "cache-read-only:",
        ):
            self.assertIn(snippet, self.text)
        fluidphysics = matrix_entry(self.text, "fluidphysics")
        self.assertIn("kind: fabric", fluidphysics)
        self.assertNotIn("forge_props", fluidphysics)
        self.assertNotIn("fabric_props", fluidphysics)
        jurassicraft = matrix_entry(self.text, "jurassicraft")
        self.assertIn("forge_props: -PwithProjectE -PwithJade -PwithJei\n", jurassicraft)
        self.assertIn("fabric_props: -PwithJade -PwithEmi -PwithItemAlchemy", jurassicraft)
        for port in ("advancedgenetics", "mystcraft", "buildcraft", "extremereactors", "mekanism"):
            entry = matrix_entry(self.text, port)
            self.assertIn("forge_props: -PwithProjectE\n", entry)
            self.assertIn("fabric_props: -PwithItemAlchemy\n", entry)

    def test_optional_integrations_and_smoke_suite_run(self):
        """Optional integrations get their own GameTest runs, apart from the EMC runs; Mystcraft runs its smoke suite."""
        mekanism = matrix_entry(self.text, "mekanism")
        self.assertIn("forge_compat_props: -PwithJade -PwithTop -PwithComputerCraft -PwithJei -PwithCurios -PwithGender"
                      " -PwithFluxNetworks -PwithRecipeStages\n",
                      mekanism)
        self.assertIn("fabric_compat_props: -PwithJade -PwithWthit -PwithComputerCraft -PwithJei -PwithTrinkets -PwithGender"
                      " -PwithFluxNetworks\n",
                      mekanism)
        self.assertIn("fabric_portinglib_props: -PwithPortingLib -PwithCraftTweaker\n", mekanism)
        self.assertIn("name: Run Fabric GameTests beside Porting Lib", self.text)
        self.assertIn('./gradlew --no-daemon -p "mods/${PORT}/fabric" runGametest "${extra[@]}"', self.text)
        extremereactors = matrix_entry(self.text, "extremereactors")
        self.assertIn("forge_compat_props: -PwithCompat\n", extremereactors)
        self.assertIn("fabric_compat_props: -PwithCompat -PwithMekanism\n", extremereactors)
        self.assertIn("fabric_compat_props: -PwithEmi -PwithJei -PwithRei\n", matrix_entry(self.text, "icbm_port"))
        projecte = matrix_entry(self.text, "projecte")
        self.assertIn("kind: dual", projecte)
        self.assertIn("fabric_compat_props: -PwithMekanism -PwithPortingLib\n", projecte)
        gridwright = matrix_entry(self.text, "gridwright")
        self.assertIn("forge_compat_props: -PwithEmi -PwithJei -PwithRei\n", gridwright)
        self.assertIn("fabric_compat_props: -PwithEmi -PwithJei -PwithRei\n", gridwright)
        self.assertIn("smoke: true\n", matrix_entry(self.text, "mystcraft"))
        self.assertIn('gradle_with_props "$FORGE_COMPAT_PROPS" ":${PORT}:runGameTestServer"', self.text)
        self.assertIn('gradle_with_props "$FABRIC_COMPAT_PROPS" -p "mods/${PORT}/fabric" runGametest', self.text)
        self.assertIn('gradle_with_props "$FABRIC_COMPAT_PROPS" :icbm_port:runGametest', self.text)
        self.assertIn(":mystcraft:runSmokeGameTestServer -PenableSmokeTests", self.text)
        self.assertIn("-p mods/mystcraft/fabric runSmokeGametest -PenableSmokeTests", self.text)
        self.assertEqual(self.text.count("MYSTCRAFT_SMOKE_GAMETEST MYSTCRAFT_SMOKE_COMPLETE"), 2)

    def test_crafttweaker_runs_on_its_own_on_both_loaders(self):
        """Mekanism's CraftTweaker scripts change recipes, so -PwithCraftTweaker gets a separate run on each loader."""
        mekanism = matrix_entry(self.text, "mekanism")
        self.assertIn("crafttweaker_props: -PwithCraftTweaker\n", mekanism)
        self.assertNotRegex(mekanism, r"_compat_props: [^\n]*-PwithCraftTweaker")
        self.assertNotRegex(mekanism, r"(forge|fabric)_props: [^\n]*-PwithCraftTweaker")
        self.assertIn("CRAFTTWEAKER_PROPS: ${{ matrix.crafttweaker_props }}\n", self.text)
        self.assertIn("- name: Run GameTests with CraftTweaker\n        if: ${{ matrix.crafttweaker_props }}\n", self.text)
        self.assertIn('./gradlew --no-daemon ":${PORT}:runGameTestServer" "${extra[@]}"', self.text)
        self.assertIn('./gradlew --no-daemon -p "mods/${PORT}/fabric" runGametest "${extra[@]}"', self.text)

    def test_every_emc_port_compares_live_values_with_both_emc_mods(self):
        """Each port's GameTest EmcAudit compares the shipped values with the live EMC mod when it is loaded."""
        dual = [path.parent.parent.name for path in (ROOT / "mods").glob("*/emc/policy.json")
                if (path.parent.parent / "fabric").is_dir()]
        self.assertTrue(dual)
        for port in dual:
            entry = matrix_entry(self.text, port)
            self.assertIn("kind: dual", entry, port)
            self.assertRegex(entry, r"forge_props: [^\n]*-PwithProjectE", port)
            self.assertRegex(entry, r"fabric_props: [^\n]*-PwithItemAlchemy", port)
        self.assertIn('gradle_with_props "$FORGE_PROPS" ":${PORT}:runGameTestServer"', self.text)
        self.assertIn('gradle_with_props "$FABRIC_PROPS" -p "mods/${PORT}/fabric" runGametest', self.text)
        self.assertIn('gradle_with_props "-PwithItemAlchemy" :icbm_port:runGametest', self.text)


if __name__ == "__main__":
    unittest.main()
