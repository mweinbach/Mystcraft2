# Recipe migration audit

Baseline: ICBM Classic `prod/1.12`, revision
`9c1d985a375adc2868ff47707b3a636452d0b63a`. Recipe data is translated into
Minecraft 1.19.2 JSON. No upstream textures, models or language files are used.
The exact input/output mapping and source-relative recipe path are retained in
`tools/upstream_recipe_mapping.json` (124 entries). That file is authoritative
for reproducing this migration, and is applied last by the resource generator.

## Source-equivalent recipe shapes and quantities

- All 23 explosive blocks use source patterns, ingredients and result counts.
  The vanilla fallback nuclear recipe consumes five wither skeleton skulls and
  four thermobaric explosives. Antimatter consumes eight nether stars around a
  nuclear explosive; redmatter consumes eight around an antimatter explosive.
- All 23 missiles combine a missile module and the matching explosive horizontally.
  All 23 carts place the explosive above a minecart. All ten tier-one grenades
  place string above the explosive, producing **one**, not four grenades.
- Steel clumps, steel plates, copper/gold wires, three circuit tiers, batteries,
  poison powder, saltpeter balls and component gunpowder follow source recipes.
  Steel clumps and saltpeter balls smelt into their corresponding material with
  0.1 experience; vanilla furnace time is 200 ticks.
- Base tier-one launcher, launcher frame, connector, screen, cruise launcher,
  radar station, EMP tower, rocket launcher, radar gun, radio detonator, laser
  designator, defuser, antidote, three concrete tiers, reinforced glass, three spike variants and glass
  controls follow the matching source recipe files.
- Cluster missile, empty bomblet casing, condensed bomblet, cargo balloon and
  cargo parachute follow source recipe shapes/counts. Source cluster crafting
  makes the empty carrier. A special crafting recipe combines it with one or
  more mixed bomblet slots, preserving previous contents up to 64. This ports
  source cargo composition into a bounded modern crafting serializer.

## Platform mappings

Legacy explosive metadata is resolved using `ExplosiveInit.newEx` IDs, including
its non-contiguous IDs (thermobaric=13, nuclear=15, antimatter=22, redmatter=23).
Other metadata maps to independent registry entries. Old copper ingots now use
vanilla copper; old note blocks, snow blocks, skulls, charcoal and carpet map to
modern IDs. The wildcard dye ingredient accepts all sixteen modern dyes.
Forge ore-dictionary inputs become the corresponding local component item,
vanilla item, or vanilla planks/leaves/saplings/dirt tag. These vanilla tags may
accept more 1.19.2 variants than existed in 1.12.2. Wooden buttons currently map
to oak buttons. Flint-and-steel crafting accepts damaged tools under modern
vanilla ingredient rules.

The installed component set selects the upstream circuit/steel missile-module
recipe. Unsupported Mekanism and uranium recipes are excluded; the published
vanilla fallbacks are selected instead. Carbon-mod recipes are excluded, while
the published coal/charcoal gunpowder fallback remains. Forge recipe conditions
are resolved at generation time, not exposed as unsupported Fabric serializers.

Sulfur has no crafting recipe: its source is the creeper/blaze runtime loot
integration. Source `ICBMClassic.registerRecipes` installs smelting; source
`ICBMClassic` loot handling adds weight-2, quality-0 entries containing ten
sulfur dust to the existing main creeper/blaze loot pools. Runtime loot parity
must be assessed separately from recipe-graph validation.

## Explicit adaptations and remaining gaps

The compatibility `launcher`, GPS and SAM launcher retain independent prototype
recipes; the first was an existing recipe and the latter two have no matching
source JSON in the audited set. New network energy support and tier-two/tier-three
launcher upgrade recipes are port-specific. The source's launcher support recipe
actually produces its frame; the new energy support has a separate role.
The additional nine non-condensed bomblet recipes extend the source condensed
casing-plus-explosive pattern; they are not published upstream recipes.

The upstream advanced/elite circuit recipes both use the basic circuit in their
center; this apparent oddity is preserved. No ore-processing machines, optional
mod integrations, unsupported launcher devices and cargo carrier data crafting/decrafting
are claimed complete here. Further
new content requires separate source recipe migration.

## Verification

`tools/validate_resources.py` checks JSON syntax, model and texture references,
recipe ingredient tags and local item models, block loot coverage, and a recipe
reachability graph starting with vanilla items plus sulfur from mob loot.
The graph checks that recipe outputs can be made; it cannot prove in-game recipe
matching, registry availability, progression balance or runtime loot behavior.
Run both documented tooling commands after adding content. Dedicated-server
recipe reload and gameplay tests are required alongside this static audit.

Static registration audit additionally resolves the module's explicit Java
registration helper calls and payload enum expansion: 141 registered items have
matching models, English names and survival paths; all 47 blocks have blockstates;
all ten entities have English names. Recipe local item references are checked
against that extracted catalog, and orphan item models fail validation. This
static extractor targets current code conventions and must be updated if those
conventions change. Cluster loading is a special recipe without a fixed output
and is audited through its registered serializer and dedicated gameplay tests.

Sulfur hook audit: `Materials.register` modifies only the first existing pool in
creeper/blaze tables, adding a ten-count item entry with weight 2 and default
quality 0. This matches source main-pool placement for vanilla 1.19.2 tables;
resource packs can reorder pools, so custom-table compatibility is not exact.

Held-item missile casing follows `missile/special/missile.held_item.json`:
sticky piston above basic circuit above missile module. Ballistic launcher
follows `tools/rpgBallistic.json`: rocket launcher plus launcher screen.
`held_item_loading` and `held_item_unloading` special recipe JSONs activate
modern serializers; allowed cargo actions and unloading semantics are verified
by delivery gameplay tests, rather than represented as static fixed outputs.

SAM ammunition follows `missile/special/missile.anti_ballistic.json`: basic
circuit above condensed explosive above missile module. EMP tower extensions
follow `machine/emptower.booster.json`. Radioactive dirt/stone are environmental
fallout blocks, available by nuclear replacement and harvesting; no source
crafting recipes exist for them. Their survival availability is an explicit
external source in the recipe graph alongside sulfur loot.
