import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_payload_visuals import main
from payload_visuals import PROFILES, paint_label, validate_profiles


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

    def test_packed_resources_match_the_profile_table(self):
        main()


if __name__ == "__main__":
    unittest.main()
