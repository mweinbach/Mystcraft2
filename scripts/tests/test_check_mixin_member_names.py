"""Rules of scripts/check_mixin_member_names.py (Tekxit 4: Tree Chop's Entity mixin failed on JurassiCraft's KEY)."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("check_mixin_member_names", ROOT / "scripts" / "check_mixin_member_names.py")
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)


def offenders(source):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "mixin" / "SampleMixin.java"
        path.parent.mkdir()
        path.write_text(source, encoding="utf-8")
        return check.offenders(path)


class MixinMemberNameTests(unittest.TestCase):
    def test_unprefixed_static_field_is_reported(self):
        found = offenders(
            "@Mixin(Entity.class)\n"
            "public abstract class SampleMixin {\n"
            "    @Unique private static final String KEY = \"JurassiCraftData\";\n"
            "}\n")
        self.assertEqual(len(found), 1)
        self.assertIn("'KEY'", found[0])

    def test_two_space_indent_and_multiline_annotations_are_reported(self):
        found = offenders(
            "@Mixin(Entity.class)\n"
            "public class SampleMixin {\n"
            "  @Unique\n"
            "  private int ticks;\n"
            "}\n")
        self.assertEqual(len(found), 1)

    def test_prefixed_shadow_and_nested_fields_are_fine(self):
        found = offenders(
            "@Mixin(Entity.class)\n"
            "public abstract class SampleMixin {\n"
            "    @Unique private static final String mod$KEY = \"{\";\n"
            "    @Shadow private Level level;\n"
            "    @Shadow\n"
            "    public int tickCount;\n"
            "    private void mod$run() {\n"
            "        final int local = 1;\n"
            "    }\n"
            "    private static class Helper {\n"
            "        private static final int PLAIN = 2;\n"
            "    }\n"
            "}\n")
        self.assertEqual(found, [])

    def test_non_mixin_classes_are_ignored(self):
        self.assertEqual(offenders("public class SampleMixin {\n    private static final int KEY = 1;\n}\n"), [])

    def test_repository_mixins_are_prefixed(self):
        self.assertEqual(check.main([str(ROOT / "mods")]), 0)


if __name__ == "__main__":
    unittest.main()
