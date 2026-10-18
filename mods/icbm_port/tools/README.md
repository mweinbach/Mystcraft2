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
and the remaining port-specific recipes. Recipe advancement unlocks expose
recipes in the vanilla recipe book. Every placed block has a self-drop loot table.

Explosives use original iron casing and colored bands. All payload missiles,
cluster/held missiles and SAM ammunition now use original 3D body/nose/fin models,
with local +Y as their flight axis. Cargo carriers have original canopy/rope/basket
and balloon models. Spikes use original stepped prong geometry. These models
reference vanilla textures and do not reproduce upstream art. Grenades, carts and
tools still use vanilla item sprites; most machines retain temporary cube models.
Machine power currently has no visual indicator. Glass controls use vanilla model
templates and state rotations with vanilla glass textures.

The checker parses every JSON, resolves all explicit model parents and textures
against the supplied vanilla jar or local resources, checks recipe mod-item
model references, ensures block loot coverage, and checks survival recipe reachability with runtime sulfur loot and environmental radioactive blocks assumed. It does not render models,
execute runtime registries (its static Java catalog audit is convention-specific), prove gameplay behavior, or establish upstream parity.

Resource verification on October 18, 2026: 572 JSON files, 1018 references and all 139 fixed-output recipe ingredient paths
passed against the Minecraft 1.19.2 client jar. Client smoke testing confirmed missile item models and JEI recipe displays. Comprehensive glass transparency, hand transforms, sound, animation and multiplayer appearance checks remain pending.
