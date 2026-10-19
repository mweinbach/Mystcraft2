"""Check bundled sound and tag references without starting Minecraft."""

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources"
ASSETS = ROOT / "assets/jurassicraft"


class SoundAndTagTests(unittest.TestCase):
    def test_all_jurassicraft_sounds_resolve(self):
        manifest = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
        errors = []
        for event, specification in manifest.items():
            for entry in specification.get("sounds", []):
                name = entry if isinstance(entry, str) else entry.get("name")
                kind = "file" if isinstance(entry, str) else entry.get("type", "file")
                if not isinstance(name, str) or not name.startswith("jurassicraft:"):
                    continue
                path = name.split(":", 1)[1]
                if kind == "event":
                    if path not in manifest:
                        errors.append(f"{event}: missing sound event {name}")
                else:
                    sound = ASSETS / "sounds" / f"{path}.ogg"
                    if not sound.is_file():
                        errors.append(f"{event}: missing sound file {name}")
                    elif sound.read_bytes()[:4] != b"OggS":
                        errors.append(f"{event}: invalid Ogg file {name}")
        self.assertFalse(errors, "\n".join(errors))

    def test_jurassicraft_block_and_item_tag_entries_have_resources(self):
        errors = []
        for path in (ROOT / "data").rglob("*.json"):
            parts = path.relative_to(ROOT / "data").parts
            if len(parts) < 4 or parts[1] != "tags" or parts[2] not in ("blocks", "items"):
                continue
            kind = parts[2]
            data = json.loads(path.read_text(encoding="utf-8"))
            for entry in data.get("values", []):
                if isinstance(entry, dict):
                    if entry.get("required") is False:
                        continue
                    entry = entry.get("id")
                if not isinstance(entry, str):
                    errors.append(f"{path.relative_to(ROOT)}: invalid tag entry {entry!r}")
                    continue
                is_tag = entry.startswith("#")
                name = entry[1:] if is_tag else entry
                if not name.startswith("jurassicraft:"):
                    continue
                local = name.split(":", 1)[1]
                if is_tag:
                    target = ROOT / "data/jurassicraft/tags" / kind / f"{local}.json"
                else:
                    client_dir = "blockstates" if kind == "blocks" else "models/item"
                    target = ASSETS / client_dir / f"{local}.json"
                if not target.is_file():
                    errors.append(f"{path.relative_to(ROOT)}: missing resource for {entry}")
        self.assertFalse(errors, "\n".join(errors))


if __name__ == "__main__":
    unittest.main()
