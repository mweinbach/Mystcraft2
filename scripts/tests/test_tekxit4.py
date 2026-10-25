"""Rules of scripts/tekxit4.py, which installs the Fabric ports into a disposable Tekxit 4 server."""

import importlib.util
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("tekxit4", ROOT / "scripts" / "tekxit4.py")
tekxit4 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(tekxit4)


class Tekxit4Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(self.tmp, ignore_errors=True))

    def test_only_production_jars_are_installed(self):
        for name in ("mystcraft-fabric-1.19.2-0.4.0.jar", "mystcraft-fabric-1.19.2-0.4.0-dev.jar",
                     "mystcraft-fabric-1.19.2-0.4.0-sources.jar", "icbm-fabric-port-0.2.0-tekxit-tests.jar",
                     "mekanism-fabric-1.19.2-10.3.9-named.jar", "mystcraft-smoke-fabric-1.19.2-0.4.0.jar"):
            (self.tmp / name).write_text("")
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp, "0.4.0")],
                         ["mystcraft-fabric-1.19.2-0.4.0.jar"])

    def test_a_version_ending_in_dev_is_the_production_jar(self):
        for name in ("mystcraft-fabric-1.19.2-0.4.0-dev.jar", "mystcraft-fabric-1.19.2-0.4.0-dev-sources.jar"):
            (self.tmp / name).write_text("")
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp, "0.4.0-dev")],
                         ["mystcraft-fabric-1.19.2-0.4.0-dev.jar"])

    def test_jars_of_an_earlier_version_are_left_out(self):
        for name in ("mekanism-fabric-1.19.2-10.3.8-port.1.jar", "mekanismtools-fabric-1.19.2-10.3.8-port.1.jar",
                     "mekanism-fabric-1.19.2-10.3.9-port.1.jar", "mekanismtools-fabric-1.19.2-10.3.9-port.1.jar",
                     "mystcraft-fabric-1.19.2-0.4.0-dev.jar", "mystcraft-fabric-1.19.2-0.4.0.jar",
                     "mystcraft-fabric-1.19.2-10.4.0.jar"):
            (self.tmp / name).write_text("")
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp, "10.3.9-port.1")],
                         ["mekanism-fabric-1.19.2-10.3.9-port.1.jar", "mekanismtools-fabric-1.19.2-10.3.9-port.1.jar"])
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp, "0.4.0")],
                         ["mystcraft-fabric-1.19.2-0.4.0.jar"])
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp, "0.5.0")], [])

    def test_project_versions_come_from_the_properties_the_builds_read(self):
        (self.tmp / "mods/mystcraft/fabric/build/libs").mkdir(parents=True)
        (self.tmp / "mods/mystcraft/port.properties").write_text("#mod_version=0.3.0\nmod_version=0.5.0\n")
        (self.tmp / "mods/mystcraft/fabric/build/libs/mystcraft-fabric-1.19.2-0.4.0.jar").write_text("")
        (self.tmp / "mods/mystcraft/fabric/build/libs/mystcraft-fabric-1.19.2-0.5.0.jar").write_text("")
        (self.tmp / "mods/icbm_port").mkdir(parents=True)
        (self.tmp / "mods/icbm_port/gradle.properties").write_text("mod_version=0.2.0-alpha.5\n")
        (self.tmp / "tools/tekxit4-probe").mkdir(parents=True)
        (self.tmp / "tools/tekxit4-probe/gradle.properties").write_text("probe_version=1.1.0\n")
        with mock.patch.object(tekxit4, "ROOT", self.tmp):
            self.assertEqual(tekxit4.project_version("mystcraft"), "0.5.0")
            self.assertEqual(tekxit4.project_version("icbm_port"), "0.2.0-alpha.5")
            self.assertEqual(tekxit4.project_version("probe"), "1.1.0")
            self.assertEqual([jar.name for jar in tekxit4.project_jars("mystcraft")],
                             ["mystcraft-fabric-1.19.2-0.5.0.jar"])

    def test_every_real_port_has_a_current_version(self):
        for project in [*tekxit4.all_ports(), "metalrender", "probe"]:
            self.assertTrue(tekxit4.project_version(project), project)

    def test_every_dual_port_and_icbm_are_known(self):
        self.assertIn("mystcraft", tekxit4.all_ports())
        self.assertIn("icbm_port", tekxit4.all_ports())
        self.assertEqual(tekxit4.port_libs("icbm_port"), ROOT / "mods/icbm_port/build/libs")

    def test_server_properties_are_replaced_and_added(self):
        path = self.tmp / "server.properties"
        path.write_text("#online-mode=true\nonline-mode=true\nlevel-type=bclib\\:normal\n")
        tekxit4.write_properties(path, {"online-mode": "false", "server-port": "25567"})
        self.assertEqual(path.read_text(),
                         "#online-mode=true\nonline-mode=false\nlevel-type=bclib\\:normal\nserver-port=25567\n")

    def test_archive_entries_outside_the_pack_root_are_rejected(self):
        archive = self.tmp / "pack.zip"
        with zipfile.ZipFile(archive, "w") as pack:
            pack.writestr(tekxit4.PACK["root"] + "../escape.txt", "x")
        with self.assertRaises(SystemExit):
            tekxit4.unpack(archive, self.tmp / "out")
        self.assertFalse((self.tmp / "escape.txt").exists())

    def test_eula_needs_the_operators_setting(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertFalse(tekxit4.eula_accepted())
        with mock.patch.dict(os.environ, {"MINECRAFT_EULA": "true"}):
            self.assertTrue(tekxit4.eula_accepted())

    def test_prepare_never_deletes_a_directory_it_did_not_make(self):
        base = self.tmp / "cache/server-20.0.0"
        base.mkdir(parents=True)
        (base / ".source-sha256").write_text(tekxit4.PACK["sha256"])
        mine = self.tmp / "server"
        mine.mkdir()
        (mine / "world").mkdir()
        args = SimpleNamespace(dir=str(mine), ports="mystcraft", no_probe=True, no_item_alchemy=True)
        with mock.patch.object(tekxit4, "CACHE", self.tmp / "cache"), self.assertRaises(SystemExit):
            tekxit4.command_prepare(args)
        self.assertTrue((mine / "world").is_dir())

    def test_report_fails_on_a_failed_or_missing_gametest(self):
        out = self.tmp / "tekxit-probe"
        out.mkdir()
        (out / "server-report-class_3176.json").write_text(json.dumps({"failures": [], "warnings": []}))
        with mock.patch("builtins.print"):
            self.assertEqual(tekxit4.report(self.tmp, 0), 1)
            (out / "gametests.json").write_text(json.dumps(
                {"expected": 1, "passed": 1, "failed": 0, "end": "all tests reported", "results": []}))
            self.assertEqual(tekxit4.report(self.tmp, 143), 0)
            (out / "gametests.json").write_text(json.dumps(
                {"expected": 0, "passed": 0, "failed": 0, "end": "no tests found", "results": []}))
            self.assertEqual(tekxit4.report(self.tmp, 0), 1)

    def jar(self, name, meta, nested=()):
        """A jar holding `meta` as fabric.mod.json, with (file, bytes) pairs nested in it."""
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            meta = dict(meta, jars=[{"file": file} for file, _ in nested])
            archive.writestr("fabric.mod.json", json.dumps(meta))
            for file, data in nested:
                archive.writestr(file, data)
        if name:
            (self.tmp / name).write_bytes(buffer.getvalue())
            return self.tmp / name
        return buffer.getvalue()

    def test_version_ranges_follow_fabric_loader(self):
        self.assertTrue(tekxit4.satisfies("2.3.0", ">=2.3.0"))
        self.assertFalse(tekxit4.satisfies("2.0.0-beta1", ">=2.3.0"))
        self.assertFalse(tekxit4.satisfies("2.3.0-beta1", ">=2.3.0"))
        self.assertTrue(tekxit4.satisfies("0.77.0+1.19.2", ">=0.77.0"))
        self.assertTrue(tekxit4.satisfies("17", ">=17"))
        self.assertTrue(tekxit4.satisfies("1.19.2", "1.19.2"))
        self.assertFalse(tekxit4.satisfies("1.19.3", "1.19.2"))
        self.assertTrue(tekxit4.satisfies("10.3.9-port.1", "10.3.9-port.1"))
        self.assertTrue(tekxit4.satisfies("0.2-SNAPSHOT", "*"))
        self.assertTrue(tekxit4.satisfies("1.4.0", "^1.2.0"))
        self.assertFalse(tekxit4.satisfies("2.0.0", "^1.2.0"))
        self.assertFalse(tekxit4.satisfies("1.3.0", "~1.2.0"))
        self.assertTrue(tekxit4.satisfies("1.0.20", [">=2", ">=1.0.20"]))
        self.assertTrue(tekxit4.satisfies("1.5", ">=1.2 <2"))

    def test_dependencies_resolve_against_the_pack_and_nested_jars(self):
        nested = self.jar(None, {"id": "fluidphysics", "version": "0.1.0", "depends": {"fabric-api": "*"}})
        port = self.jar("port.jar", {"id": "port", "version": "1.0", "depends": {
            "minecraft": "1.19.2", "fabric-api": ">=0.77.0", "fluidphysics": ">=0.1.0", "team_reborn_energy": ">=2.3.0",
            "resourcefulconfig": ">=1.0.20"}}, [("META-INF/jars/fluidphysics.jar", nested)])
        pack = {"fabric-api": {"0.77.0+1.19.2"}, "team_reborn_energy": {"2.0.0-beta1", "2.3.0"}}
        unmet, conflicts, from_pack = tekxit4.check_against_pack([port], pack)
        self.assertEqual(unmet, ["port.jar: port needs resourcefulconfig >=1.0.20 (found: none)"])
        self.assertEqual(conflicts, [])
        self.assertEqual(sorted(from_pack), ["fabric-api", "team_reborn_energy"])
        self.assertEqual(from_pack["team_reborn_energy"][0], "2.3.0")

    def test_breaks_against_the_pack_is_a_conflict(self):
        metal = self.jar("metal.jar", {"id": "metalrender", "version": "0.2.0", "breaks": {"sodium": "*"}})
        _, conflicts, _ = tekxit4.check_against_pack([metal], {"sodium": {"0.4.4+build.18"}})
        self.assertEqual(conflicts, ["metal.jar: metalrender breaks sodium * (pack has 0.4.4+build.18)"])
        self.assertEqual(tekxit4.check_against_pack([metal], {})[1], [])

    def test_pack_mods_reads_a_pack_zip_with_nested_jars(self):
        nested = self.jar(None, {"id": "energy", "version": "2.3.0", "provides": ["team_reborn_energy"]})
        archive = self.tmp / "pack.zip"
        with zipfile.ZipFile(archive, "w") as pack:
            pack.writestr("mods/lib.jar", self.jar(None, {"id": "lib", "version": "1"}, [("e.jar", nested)]))
            pack.writestr("TKXAddons/itemalchemy.jar", self.jar(None, {"id": "itemalchemy", "version": "1"}))
        self.assertEqual(tekxit4.pack_mods(archive),
                         {"lib": {"1"}, "energy": {"2.3.0"}, "team_reborn_energy": {"2.3.0"}})

    def test_release_zip_holds_the_jars_a_readme_and_their_checksums(self):
        jar = self.jar("port-1.0.jar", {"id": "port", "version": "1.0"})
        target = self.tmp / "release.zip"
        tekxit4.write_release_zip(target, [jar], "readme\n")
        with zipfile.ZipFile(target) as archive:
            self.assertEqual(archive.namelist(), ["port-1.0.jar", "README.txt", "SHA256SUMS"])
            self.assertEqual(archive.read("SHA256SUMS").decode(), f"{tekxit4.sha256(jar)}  port-1.0.jar\n")

    def test_two_jars_with_one_top_level_mod_id_are_rejected(self):
        nested = self.jar(None, {"id": "fluidphysics", "version": "0.1.0"})
        old = self.jar("port-1.0.jar", {"id": "port", "version": "1.0"})
        new = self.jar("port-1.1.jar", {"id": "port", "version": "1.1"}, [("fp.jar", nested)])
        other = self.jar("other-1.0.jar", {"id": "other", "version": "1.0"}, [("fp.jar", nested)])
        alias = self.jar("alias-1.0.jar", {"id": "alias", "version": "1.0", "provides": ["other"]})
        # The same library nested in several jars is fine: Fabric Loader keeps one copy.
        tekxit4.reject_duplicate_mods([new, other])
        with self.assertRaises(SystemExit) as raised:
            tekxit4.reject_duplicate_mods([old, new, other])
        self.assertIn("port: port-1.0.jar, port-1.1.jar", str(raised.exception))
        with self.assertRaises(SystemExit) as raised:
            tekxit4.reject_duplicate_mods([other, alias])
        self.assertIn("other: other-1.0.jar, alias-1.0.jar", str(raised.exception))

    def test_release_stops_before_writing_when_metalrender_repeats_a_mod_id(self):
        port = self.jar("port-1.0.jar", {"id": "port", "version": "1.0"})
        metal = self.jar("metalrender-0.2.0.jar", {"id": "metalrender", "version": "0.2.0", "provides": ["port"]})
        out = self.tmp / "out"
        args = SimpleNamespace(skip_build=True, pack=None, label="test", out=str(out))
        jars = lambda project: [metal] if project == "metalrender" else [port] if project == "mystcraft" else []
        with mock.patch.object(tekxit4, "all_ports", return_value=["mystcraft"]), \
                mock.patch.object(tekxit4, "project_jars", side_effect=jars), \
                self.assertRaises(SystemExit) as raised:
            tekxit4.command_release(args)
        self.assertIn("port: port-1.0.jar, metalrender-0.2.0.jar", str(raised.exception))
        self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()
