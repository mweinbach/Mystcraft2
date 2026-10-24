"""Rules of scripts/check_unmapped_overrides.py, which caught Extreme Reactors' Tekxit 4 AbstractMethodError."""

import importlib.util
import unittest
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("check_unmapped_overrides", ROOT / "scripts" / "check_unmapped_overrides.py")
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)

STATE = "()Lnet/minecraft/class_2680;"


def cls(name, super_="java/lang/Object", interfaces=(), methods=None, access=0x0001):
    return SimpleNamespace(name=name, super=super_, interfaces=list(interfaces), methods=dict(methods or {}),
                           access=access, origin="test.jar")


def interface(name, methods):
    return cls(name, None, methods=methods, access=0x0001 | check.ACC_INTERFACE | check.ACC_ABSTRACT)


def classes(*infos):
    return {info.name: info for info in infos}


class UnmappedOverrideTests(unittest.TestCase):
    def test_interface_method_left_to_a_minecraft_superclass_is_reported(self):
        # IMultiblockVariantProvider declared getBlockState() and relied on BlockEntity (class_2586) to implement it.
        found = check.problems(classes(
            interface("ports/Provider", {("getBlockState", STATE): check.ACC_ABSTRACT | 0x0001}),
            cls("ports/Part", "net/minecraft/class_2586", ["ports/Provider"]),
        ))
        self.assertEqual(len(found), 1)
        self.assertIn("ports/Provider.getBlockState", found[0])

    def test_methods_implemented_in_the_jar_or_by_default_are_fine(self):
        provider = interface("ports/Provider", {("getBlockState", STATE): check.ACC_ABSTRACT | 0x0001,
                                                ("variant", "()V"): 0x0001})
        self.assertEqual(check.problems(classes(
            provider, cls("ports/Part", "net/minecraft/class_2586", ["ports/Provider"],
                          {("getBlockState", STATE): 0x0001}))), [])

    def test_names_minecraft_keeps_in_production_are_fine(self):
        for name in ("method_11010", "comp_123", "m_58900_", "toString"):
            with self.subTest(name=name):
                self.assertEqual(check.problems(classes(
                    interface("ports/Provider", {(name, STATE): check.ACC_ABSTRACT | 0x0001}),
                    cls("ports/Part", "net/minecraft/class_2586", ["ports/Provider"]))), [])

    def test_classes_outside_minecraft_hierarchies_are_skipped(self):
        self.assertEqual(check.problems(classes(
            interface("ports/Provider", {("getBlockState", STATE): check.ACC_ABSTRACT | 0x0001}),
            cls("ports/Part", "com/example/LibraryBase", ["ports/Provider"]))), [])

    def test_forge_srg_name_shape(self):
        self.assertTrue(check.keeps_name("m_58900_"))
        self.assertFalse(check.keeps_name("m_custom_"))
        self.assertFalse(check.keeps_name("getBlockState"))


if __name__ == "__main__":
    unittest.main()
