"""Rebuild source card ranks without the old nested-constructor extraction bug or a rank-four clamp."""
from pathlib import Path
import json
import re

module = Path(__file__).resolve().parents[1]
catalog = json.loads((module / "src/main/resources/assets/mystcraft/classic_symbols.json").read_text())
assert catalog["reference"]["commit"] == "9bc8ddc061845df0cd5ea47f4fdef773cff786ae"
ranks = {}
for entry in catalog["symbols"]:
    source = entry["source_registration"]
    if entry["source"] == "data/ModSymbols.java":
        # Constructor arguments can themselves contain commas, parentheses and field references.
        match = re.search(r'\),\s*(null|\d+),\s*WordData\.', source)
        if not match:
            raise ValueError(source)
        if match.group(1) != "null":
            ranks[entry["id"]] = int(match.group(1))
    elif entry["legacy_id"].split(":")[1].startswith("ModColor"):
        ranks[entry["id"]] = 0  # SymbolColor constructor setCardRank(0).
    elif "modmat_" in entry["id"]:
        match = re.search(r'create(?:Myst)?\(WordData\.\w+,\s*(\d+)', source)
        if not match:
            raise ValueError(source)
        ranks[entry["id"]] = int(match.group(1))
out = {"source": "ModSymbols.registerSymbol; SymbolColor constructor; ModSymbolsModifiers ordered descriptor card ranks", "ranks": ranks}
(module / "src/main/resources/assets/mystcraft/exploration/card_ranks.json").write_text(json.dumps(out, indent=2) + "\n")
