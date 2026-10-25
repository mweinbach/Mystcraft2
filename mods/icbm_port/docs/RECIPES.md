# Recipe migration audit

Baseline: ICBM Classic `prod/1.12`, revision
`9c1d985a375adc2868ff47707b3a636452d0b63a`. Recipe data is translated into
Minecraft 1.19.2 JSON. No upstream textures, models or language files are used.
The exact input/output mapping and source-relative recipe path are retained in
`tools/upstream_recipe_mapping.json` (135 entries). That file is authoritative
for reproducing this migration, and is applied last by the resource generator.

## Source-equivalent recipe shapes and quantities

- All 23 explosive blocks use source patterns, ingredients and result counts.
  When no uranium provider exists, the vanilla fallback nuclear recipe consumes five wither skeleton skulls and
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
  more mixed bomblet slots, preserving previous contents within upstream
  `RecipeCluster`'s volume rule (carrier 20 + 2 per bomblet <= 200 by default,
  i.e. 90 bomblets; see DELIVERY.md for the settings and the 200-droplet hard
  cap). This ports source cargo composition into a bounded modern crafting
  serializer.

## Platform mappings

Legacy explosive metadata is resolved using `ExplosiveInit.newEx` IDs, including
its non-contiguous IDs (thermobaric=13, nuclear=15, antimatter=22, redmatter=23).
Other metadata maps to independent registry entries. Old copper ingots now use
vanilla copper; old note blocks, snow blocks, skulls, charcoal and carpet map to
modern IDs. The wildcard dye ingredient accepts all sixteen modern dyes.
Source OreDictionary nodes are migrated through private `icbm_port:materials/`
union tags accepting additive Fabric 1.19.2 common tags and optional modern/Forge
aliases. Real output forms are exported under both common naming styles; direct
source item/metadata nodes stay direct, and ingredient arrays stay unions.
Vanilla planks/leaves/saplings/dirt tags supply modern natural-material variants.
See [ORE_DICTIONARY.md](ORE_DICTIONARY.md) for all 24 consumed names, all 90 source
ingredient nodes, exports, exact external-mod tag evidence and form safeguards.
Wooden buttons currently map to oak buttons. Flint-and-steel crafting accepts
damaged tools under modern vanilla ingredient rules; the source component module
specifically requested undamaged metadata 0, while its fallback used wildcard
32767, so that wear distinction remains adapted.

The installed component set selects the upstream circuit/steel missile-module
recipe. Uranium-ingot/uranium-235-pellet and carbon/charcoal/coal-dust alternatives
are retained with native Fabric reload-time tag conditions. Their vanilla
fallbacks use the inverse predicates, so datapack or mod-provided materials
change the selected path on reload. Ordinary coal/charcoal items remain distinct
from dust. Native conditions use `fabric:load_conditions`; no Forge condition
serializer is installed.

The three upstream `explosives/mekanism/` atomic-alloy alternatives are now
shipped for the repository's Fabric Mekanism port (mod ID `mekanism`), whose
Mekanism 10.3 item is `mekanism:alloy_atomic` (1.12.2 `mekanism:atomicalloy`).
Upstream gated them with `minecraft:item_exists`; the port uses
`fabric:all_mods_loaded ["mekanism"]`:

| Recipe | Loads when | Shape |
| --- | --- | --- |
| `nuclear_explosive_uranium` | a uranium ingot or U-235 pellet tag is populated | five uranium/pellets, four thermobaric |
| `nuclear_explosive_mekanism` | no uranium **and** Mekanism loaded | five atomic alloys, four thermobaric |
| `nuclear_explosive` | no uranium **and** Mekanism absent | five wither skeleton skulls, four thermobaric |
| `antimatter_explosive_mekanism` / `redmatter_explosive_mekanism` | Mekanism loaded | eight atomic alloys around the prior tier |
| `antimatter_explosive` / `redmatter_explosive` | Mekanism absent | eight nether stars around the prior tier |

The vanilla antimatter/redmatter fallbacks previously loaded unconditionally;
they now carry the inverse Mekanism gate, as upstream's did, so each tier has
exactly one active recipe. Fabric Mekanism fills `c:uranium_ingots`, which
`icbm_port:materials/uranium_ingots` includes. With Mekanism installed, the
uranium recipe is therefore the active nuclear recipe and
`nuclear_explosive_mekanism` stays off, as upstream's conditions intend; the
atomic-alloy nuclear recipe applies only where Mekanism's uranium tag is empty.
Recipe-book unlock advancements share their recipe's conditions.
`IntegrationGameTests.mekanismAlternativesFollowModPresence` derives each
recipe's expected state from Mekanism presence **and** the live uranium and
dust tags, then checks it, and its unlock, on a real server. Without Mekanism,
the gated recipes and unlocks are absent and the fallbacks load.
`tools/tests/test_resource_tags.py` checks both branches statically. A server
with Mekanism installed has not been run.

`gunpowder_from_dusts` is also off when Mekanism is loaded (port addition, not
upstream). Mekanism fills the carbon/charcoal/coal dust tags and converts
gunpowder and sulfur dust one-to-one, so a recipe that turns one sulfur dust
into three gunpowder would duplicate gunpowder and create EMC. With Mekanism,
neither ICBM gunpowder recipe loads, because the coal fallback is already off
once a dust tag is populated. Other mods' dusts still select the dust recipe.

EMC: `scripts/emc.py` prices every one of these alternatives, with Mekanism's
atomic alloy and uranium at the values `mods/mekanism` ships. Each tier then
takes its cheapest recipe, so crafting and burning never gains EMC with or
without Mekanism. Ordinary recipes are priced with Mekanism's tag contributions
as well. Mekanism's sulfur dust in `icbm_port:materials/sulfur_dusts`, for
example, sets the battery's value, along with everything built from it. See
[emc/README.md](../emc/README.md).
Source condition predicates and the one-component-missing module fallback gap
are documented in the OreDictionary audit.

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
A real `iron_plate` restores the source's `plateIron` export; its four-iron-ingot
to four-plate recipe is a port extension analogous to the source steel recipe.
Iron ingots are not mislabeled as plates in common tags.

The upstream advanced/elite circuit recipes both use the basic circuit in their
center; this apparent oddity is preserved. Ore-processing machines and Forge-only integrations remain outside this recipe migration. Cargo load/unload serializers are described in DELIVERY.md. Further new content requires separate source recipe migration.

## Verification

`tools/validate_resources.py` checks JSON syntax, model and texture references,
recipe ingredient tags and local item models, block loot coverage, and a recipe
reachability graph starting with vanilla items plus sulfur from mob loot.
The graph checks that recipe outputs can be made; it cannot prove in-game recipe
matching, registry availability, progression balance or runtime loot behavior.
Run both documented tooling commands after adding content. Dedicated-server
recipe reload and gameplay tests are required alongside this static audit.

Static registration audit additionally resolves the module's explicit Java
registration helper calls and payload enum expansion: 142 registered items have
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
