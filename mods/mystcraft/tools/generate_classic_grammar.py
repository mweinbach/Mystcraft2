"""Rebuild ordered classic productions from the pinned LGPL source; no runtime behavior flags are changed."""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("legacy_checkout", type=Path)
args = parser.parse_args()
expected = "9bc8ddc061845df0cd5ea47f4fdef773cff786ae"
actual = subprocess.check_output(["git", "-C", str(args.legacy_checkout), "rev-parse", "HEAD"], text=True).strip()
if actual != expected:
    parser.error(f"Expected {expected}; got {actual}")
module = Path(__file__).resolve().parents[1]
root = args.legacy_checkout / "src"
data = (root / "api/java/com/xcompwiz/mystcraft/api/grammar/GrammarData.java").read_text()
constants = {key: "mystcraft:" + value for key, value in re.findall(
    r'public static final ResourceLocation (\w+) = asMyst\("(.*?)"\);', data)}
for key, category in re.findall(r'public static final ResourceLocation (\w+) = BlockCategory\.(\w+)\.getGrammarBinding\(\);', data):
    constants[key] = "mystcraft:Block" + category.title()
grammar = (root / "main/java/com/xcompwiz/mystcraft/data/GrammarRules.java").read_text()
grammar_constants = {key: "mystcraft:" + value for key, value in re.findall(
    r'final ResourceLocation (\w+) = asMyst\("(.*?)"\);', grammar)}


def tokens(expression):
    result = []
    for value in re.findall(r'GrammarData\.\w+|GrammarRules\.\w+|asMyst\(".*?"\)|\b[A-Z][A-Z_0-9]*\b', expression):
        if value.startswith("GrammarData."):
            result.append(constants[value.split(".")[1]])
        elif value.startswith("GrammarRules."):
            result.append(grammar_constants[value.split(".")[1]])
        elif value.startswith("asMyst"):
            result.append("mystcraft:" + re.search('"(.*?)"', value).group(1))
        else:
            result.append(grammar_constants[value])
    return result


core = [(rank, tokens(expression)) for rank, expression in re.findall(
    r'registerRule\(buildRule\((null|\d+), (.*?)\)\);', grammar)]
symbol_source = (root / "main/java/com/xcompwiz/mystcraft/data/SymbolRules.java").read_text()
by_symbol = {}
for name, rank, expression in re.findall(r'addRuleInternal\("(.*?)", buildRule\((null|\d+), (.*?)\)\);', symbol_source):
    by_symbol.setdefault(name, []).append((rank, tokens(expression)))
catalog = json.loads((module / "src/main/resources/assets/mystcraft/classic_symbols.json").read_text())
colors, symbols = [], []
for item in catalog["symbols"]:
    name = item["legacy_id"].split(":")[1]
    if name.startswith("ModColor"):
        colors.append(("1", ["mystcraft:ColorBasic", item["legacy_id"]]))
    elif name in by_symbol:
        symbols.extend(by_symbol[name])
assert len(core) == 115 and len(colors) == 16 and len(core + colors + symbols) == 226
header = f"# Mystcraft-Legacy {expected} LGPL-3.0\n# rank<TAB>parent<TAB>ordered children\n"
encode = lambda rows: "".join("\t".join([rank] + values) + "\n" for rank, values in rows)
(module / "src/main/resources/assets/mystcraft/classic_grammar.tsv").write_text(
    header + encode(core) + "# DYNAMIC_BINDINGS\n" + encode(colors + symbols))
