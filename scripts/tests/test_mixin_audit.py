"""Conflict rules of tools/tekxit4-probe/mixin_audit.py, the Tekxit 4 mixin regression check."""

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("mixin_audit", ROOT / "tools" / "tekxit4-probe" / "mixin_audit.py")
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


def injector(kind, at="INVOKE", target="method_1", method="method_9", mod="a", ordinal=-1, require=None):
    return {
        "mod": mod, "kind": kind, "mixin": "x/Mixin", "handler": "h", "class": "net/minecraft/class_1",
        "method": method, "at": "OVERWRITE" if kind == "Overwrite" else at, "target": target, "ordinal": ordinal,
        "require": require, "exclusive": kind in ("Redirect", "ModifyConstant", "Overwrite"),
    }


class MixinAuditTests(unittest.TestCase):
    def test_two_redirects_of_one_call_collide(self):
        self.assertTrue(audit.collides(injector("Redirect"), injector("Redirect", mod="b")))

    def test_redirects_of_different_calls_or_ordinals_do_not(self):
        self.assertFalse(audit.collides(injector("Redirect"), injector("Redirect", target="method_2", mod="b")))
        self.assertFalse(audit.collides(injector("Redirect", ordinal=0), injector("Redirect", ordinal=1, mod="b")))

    def test_wrap_operation_composes_with_a_redirect(self):
        self.assertFalse(audit.collides(injector("WrapOperation"), injector("Redirect", mod="b")))

    def test_body_injectors_collide_with_an_overwrite(self):
        # Sodium 0.4.4 overwrites ItemRenderer.renderQuadList; Mekanism's wrap of putBulkData crashed the client.
        for kind in ("WrapOperation", "ModifyExpressionValue", "ModifyArg", "Inject"):
            with self.subTest(kind=kind):
                self.assertTrue(audit.collides(injector(kind), injector("Overwrite", mod="sodium")))
                self.assertTrue(audit.collides(injector("Overwrite", mod="sodium"), injector(kind)))

    def test_head_and_return_injectors_survive_an_overwrite(self):
        for at in ("HEAD", "RETURN", "TAIL"):
            with self.subTest(at=at):
                self.assertFalse(audit.collides(injector("Inject", at=at), injector("Overwrite", mod="sodium")))

    def test_other_methods_are_unaffected(self):
        self.assertFalse(audit.collides(injector("WrapOperation"), injector("Overwrite", method="method_8", mod="sodium")))

    def test_empty_port_list_fails(self):
        empty = ROOT / "build" / "mixin-audit-empty"
        empty.mkdir(parents=True, exist_ok=True)
        self.assertEqual(audit.main(["--ports", str(empty)]), 2)


if __name__ == "__main__":
    unittest.main()
