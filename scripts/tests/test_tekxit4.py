"""Rules of scripts/tekxit4.py, which installs the Fabric ports into a disposable Tekxit 4 server."""

import importlib.util
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
                     "mekanism-fabric-1.19.2-10.3.9-named.jar", "mystcraft-smoke-fabric-1.19.2-1.0.0.jar"):
            (self.tmp / name).write_text("")
        self.assertEqual([jar.name for jar in tekxit4.production_jars(self.tmp)],
                         ["mystcraft-fabric-1.19.2-0.4.0.jar"])

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


if __name__ == "__main__":
    unittest.main()
