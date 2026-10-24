import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_payload_visuals import main
from payload_visuals import (
    PROFILES,
    explosive_bottom_pixels,
    paint_explosive_side,
    paint_explosive_top,
    paint_label,
    validate_profiles,
)


class PayloadVisualTests(unittest.TestCase):
    def test_profiles_reject_shared_identity(self):
        validate_profiles()
        self.assertEqual(len({profile.rgb for profile in PROFILES}), 23)
        images = {tuple(paint_label(profile)) for profile in PROFILES}
        self.assertEqual(len(images), 23)
        closest = min(
            sum((a - b) ** 2 for a, b in zip(left.rgb, right.rgb)) ** 0.5
            for index, left in enumerate(PROFILES)
            for right in PROFILES[index + 1 :]
        )
        self.assertGreater(closest, 40)

    def test_explosive_faces_are_flat_and_distinct(self):
        sides = {tuple(paint_explosive_side(profile)) for profile in PROFILES}
        tops = {tuple(paint_explosive_top(profile)) for profile in PROFILES}
        self.assertEqual(len(sides), 23)
        self.assertEqual(len(tops), 23)
        for profile in PROFILES:
            side = paint_explosive_side(profile)
            # TNT-like layout: casing bands top and bottom, a label band across the middle.
            self.assertEqual(side[0], side[15])
            self.assertEqual(side[15 * 16], side[15 * 16 + 15])
            self.assertNotEqual(side[2 * 16], side[8 * 16])
            # Flat faces, no drawn bevel: each casing band is one colour edge to edge, and the
            # charge sticks repeat every four pixels so adjacent blocks tile seamlessly.
            for y in (0, 1, 14, 15):
                self.assertEqual(len(set(side[y * 16 : y * 16 + 16])), 1, profile.id)
            for y in (2, 3, 12, 13):
                row = side[y * 16 : y * 16 + 16]
                self.assertEqual(row, row[4:] + row[:4], profile.id)
        bottom = explosive_bottom_pixels()
        self.assertEqual(len(bottom), 256)
        for y in range(16):
            self.assertEqual(bottom[y * 16], bottom[y * 16 + 15])

    def test_packed_resources_match_the_profile_table(self):
        main()


if __name__ == "__main__":
    unittest.main()
