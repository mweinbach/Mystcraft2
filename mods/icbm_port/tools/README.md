# Resource tooling

These resources are original JSON and use Minecraft texture identifiers. No
upstream ICBM textures, language files, models, sounds, or other art are imported.

Run from any directory with Python 3:

```sh
python3 tools/generate_resources.py /path/to/minecraft-1.19.2-client.jar
python3 tools/validate_resources.py /path/to/minecraft-1.19.2-client.jar
```

Generation applies the audited source recipe mapping last, replacing the original
prototype progression. See `../docs/RECIPES.md` and `upstream_recipe_mapping.json`
for exact source files, modern item/tag mappings, optional dependencies excluded,
and the remaining port-specific recipes. `ore_dictionary_mapping.json` defines additive common-tag exports and private ingredient unions. Generation preserves source direct items, applies all audited recipes last, and generates tag-aware recipe-book unlocks. `resource_tags.py` resolves nested/optional tags and load conditions; tag ingredients are reachable only through obtainable members.

Recipe advancement unlocks expose
recipes in the vanilla recipe book. Every placed block has a self-drop loot table.

Payload identity lives in `payload_visuals.py`. Generation paints a glyph label per family, builds six missile bodies with the nose on local +Y, and writes `payload_visuals.json`. Cluster, held-item, and SAM rockets keep the shared body. Cargo, spikes, glass, concrete, and radioactive base blocks still reference vanilla block textures. Component items use original sprites. Glass controls use vanilla model templates. Run `python3 tools/check_payload_visuals.py` after generation. It checks distinct labels, full glyph UVs, the four focus silhouettes, and the blast catalog.

The checker parses every JSON, resolves all explicit model parents and textures
against the supplied vanilla jar or local resources, checks recipe mod-item
model references, ensures block loot coverage, and checks survival recipe reachability with runtime sulfur loot and environmental radioactive blocks assumed. It does not render models,
execute runtime registries (its static Java catalog audit is convention-specific), prove gameplay behavior, or establish upstream parity.

Resource verification on October 18, 2026: 656 JSON files, 976 references, 90 audited OreDictionary nodes and all 140 enabled fixed-output recipe ingredient paths
passed against the Minecraft 1.19.2 client jar. Client smoke testing confirmed missile item models and JEI recipe displays. Comprehensive glass transparency, hand transforms, sound, animation and multiplayer appearance checks remain pending.

Run `python3 -m unittest discover -s tools/tests -v` for tag graph/condition tests, and the root Gradle task `:icbm_port:runMaterialGametest` for the isolated real-server datapack reload sequence. Four optional recipes are disabled in a standalone install and are validated separately by that sequence.
