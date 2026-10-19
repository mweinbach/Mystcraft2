import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("ports", Path(__file__).resolve().parents[1] / "ports.py")
ports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ports)


class PortManagementTests(unittest.TestCase):
    def setUp(self):
        self.sandbox = tempfile.TemporaryDirectory()
        self.addCleanup(self.sandbox.cleanup)
        self.root = Path(self.sandbox.name)
        (self.root / "mods").mkdir()
        shutil.copytree(ports.ROOT / "templates", self.root / "templates")

    def create(self, mod_id="examplemod", **overrides):
        values = dict(name="Example mod", upstream="https://github.com/example/mod", license_name="MIT")
        values.update(overrides)
        return ports.create_port(self.root, mod_id, **values)

    def test_generated_port_has_source_resources_and_metadata(self):
        result = self.create()
        self.assertEqual(ports.ports(self.root)[0]["mod_id"], "examplemod")
        self.assertIn('package ports.examplemod;', (result / "src/main/java/ports/examplemod/PortMod.java").read_text())
        for path in result.rglob("*"):
            if path.is_file():
                self.assertNotIn("@@", path.read_text())
        self.assertIn('${mod_id}', (result / "src/main/resources/META-INF/mods.toml").read_text())

    def test_second_port_is_independent(self):
        self.create()
        self.create("secondmod")
        self.assertEqual([p["mod_id"] for p in ports.ports(self.root)], ["examplemod", "secondmod"])

    def fabric(self):
        result = self.create("fabricmod")
        with (result / "port.properties").open("a") as metadata:
            metadata.write("\nmod_loader=fabric\n")
        (result / "src/main/resources/META-INF/mods.toml").unlink()
        (result / "src/main/resources/pack.mcmeta").unlink()
        (result / "settings.gradle").write_text("rootProject.name = 'fabricmod'\n")
        (result / "src/main/resources/fabric.mod.json").write_text('{"schemaVersion": 1, "id": "fabricmod"}\n')
        return result

    def test_mixed_loaders_use_their_own_metadata_files(self):
        self.create()
        self.fabric()
        entries = ports.ports(self.root)
        self.assertEqual([(p["mod_id"], p.get("mod_loader", "forge")) for p in entries],
                         [("examplemod", "forge"), ("fabricmod", "fabric")])

    def test_fabric_requires_manifest_and_independent_settings(self):
        result = self.fabric()
        for relative in ("settings.gradle", "src/main/resources/fabric.mod.json"):
            path = result / relative
            original = path.read_text()
            path.unlink()
            with self.subTest(resource=relative), self.assertRaises(ValueError):
                ports.ports(self.root)
            path.write_text(original)

    def test_multi_loader_forge_descriptor_may_live_in_its_overlay(self):
        result = self.create()
        descriptor = result / "src/main/resources/META-INF/mods.toml"
        overlay = result / "src/forge/resources/META-INF/mods.toml"
        overlay.parent.mkdir(parents=True)
        descriptor.rename(overlay)
        self.assertEqual(ports.ports(self.root)[0]["mod_id"], "examplemod")
        overlay.unlink()
        with self.assertRaises(ValueError):
            ports.ports(self.root)
        (result / "src/fabric/resources/META-INF").mkdir(parents=True)
        (result / "src/fabric/resources/META-INF/mods.toml").write_text("")
        with self.assertRaises(ValueError):
            ports.ports(self.root)

    def test_existing_port_is_not_overwritten(self):
        result = self.create()
        sentinel = result / "local-work.txt"
        sentinel.write_text("keep this")
        with self.assertRaises(ValueError):
            self.create()
        self.assertEqual(sentinel.read_text(), "keep this")

    def test_invalid_input_leaves_no_partial_port(self):
        for mod_id, values in [("../escape", {}), ("BadId", {}),
                               ("badurl", {"upstream": "http://example.com"}),
                               ("badname", {"name": 'Broken"metadata'}),
                               ("badline", {"name": "Example\nmod"})]:
            with self.subTest(mod_id=mod_id):
                with self.assertRaises(ValueError):
                    self.create(mod_id, **values)
        self.assertEqual(list((self.root / "mods").iterdir()), [])

    def test_malformed_metadata_is_rejected(self):
        result = self.create()
        metadata = result / "port.properties"
        original = metadata.read_text()
        for changed in [original + "mod_id=duplicate\n",
                        original.replace("mod_id=examplemod", "mod_id=wrong"),
                        original.replace("port_status=scaffold", "port_status=done"),
                        original + "\nmod_loader=unknown\n",
                        original.replace("mod_license=MIT\n", "")]:
            with self.subTest(metadata=changed):
                metadata.write_text(changed)
                with self.assertRaises(ValueError):
                    ports.ports(self.root)


if __name__ == "__main__":
    unittest.main()
