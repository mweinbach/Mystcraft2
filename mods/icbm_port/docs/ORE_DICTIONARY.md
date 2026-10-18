# OreDictionary migration audit

Reference is **only** ICBM Classic `prod/1.12` revision
`9c1d985a375adc2868ff47707b3a636452d0b63a`. Audit date: 2026-10-18.
The upstream checkout's current HEAD is not this baseline. All source observations
below were obtained with `git show <revision>:<path>` / `git grep <revision>`;
no checkout was changed and no upstream artwork is imported.

## Material compatibility

An upstream `forge:ore_dict` ingredient becomes a private
`icbm_port:materials/<name>` union tag. That union accepts canonical Fabric
1.19.2 `c:<material>_<plural-form>` tags, modern `c:<plural-form>/<material>`
aliases, and optional Forge-style tag aliases when supplied by a Fabric provider.
Nonexistent external tags use optional references. Missing integrations therefore
do not invalidate the local recipe. Tags merge additively (`replace: false`).

The port also exports its real material items into both common naming styles,
using literal members rather than mutually recursive alias tags. Other mods can
consume those outputs; the private union lets ICBM consume providers using either
naming style. There must be no cycle through private/common tags. A datapack can
add a substitute to a common tag or the private union without Java changes.

This conversion applies **only to source OreDictionary nodes**. Direct source
item/metadata ingredients remain exact mapped items; ingredient arrays remain
unions. A plate is not an ingot, a dust is not coal, and a uranium-235 ingot or
nugget is not automatically a pellet. Do not place superficially similar items
in a common material tag to make recipes pass. The separate `iron_plate` item
restores the source `plateIron` export; its four-ingot/four-plate recipe is an
explicit port extension analogous to the published steel-plate recipe.

For wood/leaves/saplings/dirt, private unions include the appropriate vanilla
tags plus optional common extensions. `string` and `paper` retain their vanilla
items as defaults. These natural-material tags may include new 1.19.2 variants;
that is intentional modern tag compatibility, not identical 1.12 membership.

## Source exports

`content/reg/ItemReg.java` registers sulfur and saltpeter dust and optional
`ItemCrafting` subtype families. `ICBMClassic.registerOres` invokes
`ItemCrafting.registerOreNames` at highest recipe-registration priority. It
capitalizes the subtype name and prefixes its family. The resulting **12 names**
are listed below; repeated sulfur/saltpeter registration is membership duplication,
not two different materials.

| Source export | Source metadata/item | Modern output form |
| --- | --- | --- |
| `ingotSteel` | ingot:0 | `icbm_port:steel_ingot` |
| `ingotCopper` | ingot:1 | `minecraft:copper_ingot` |
| `clumpSteel` | clump:0 | `icbm_port:steel_clump` |
| `plateSteel` | plate:0 | `icbm_port:steel_plate` |
| `plateIron` | plate:1 | `icbm_port:iron_plate`; never vanilla iron ingot |
| `circuitBasic` | circuit:0 | `icbm_port:basic_circuit` |
| `circuitAdvanced` | circuit:1 | `icbm_port:advanced_circuit` |
| `circuitElite` | circuit:2 | `icbm_port:elite_circuit` |
| `wireCopper` | wire:0 | `icbm_port:copper_wire` |
| `wireGold` | wire:1 | `icbm_port:gold_wire` |
| `dustSulfur` | sulfur dust item | `icbm_port:sulfur_dust` |
| `dustSaltpeter` | saltpeter item | `icbm_port:saltpeter_dust` |

Source category enable switches can suppress component registrations. The current
Fabric component set is always registered; do not claim legacy optional-registration
configuration parity. `clumpSteel` and `plateIron` are exported even though neither
is used as an OreDictionary ingredient in the pinned recipe JSONs.

## Every source OreDictionary ingredient

There are **24 distinct consumed names**, appearing in **90 ingredient nodes across 48 recipe files**. The table records every occurrence,
including alternatives not selected in a standalone install. Source paths are
relative to `src/main/resources/assets/icbmclassic/recipes/`; suffixes identify
the precise JSON node. The common paths are import recommendations/conventions;
only the evidence section below establishes which real providers publish them.

| Source name | Private material suffix / common aliases | All source ingredient nodes |
| --- | --- | --- |
| `circuitAdvanced` | `advanced_circuits`; `c:advanced_circuits`, `c:circuits/advanced` | `machine/cruiselauncher.json.key.C`; `machine/radar.json.key.c`; `tools/remote.json.key.c` |
| `circuitBasic` | `basic_circuits`; `c:basic_circuits`, `c:circuits/basic` | `bomblet/bomblet.json.key.c`; `launcher/base.json.key.c`; `launcher/screen.json.key.c`; `missile/missile.module.json.key.c`; `missile/special/missile.anti_ballistic.json.key.c`; `missile/special/missile.cluster.json.key.c`; `missile/special/missile.held_item.json.key.c`; `parts/circuit.advanced.json.key.b`; `parts/circuit.elite.json.key.b`; `tools/defuser.json.key.c`; `tools/radargun.json.key.c`; `tools/rpg.json.key.c` |
| `circuitElite` | `elite_circuits`; `c:elite_circuits`, `c:circuits/elite` | `machine/emptower.json.key.c`; `tools/laser.json.key.c` |
| `dirt` | `dirt`; `#minecraft:dirt` + optional common aliases | `parts/vanilla/saltpeter.json.key.D` |
| `dustCarbon` | `carbon_dusts`; `c:carbon_dusts`, `c:dusts/carbon` | `parts/mod/gunpowder.json.ingredients[0][0]` |
| `dustCharcoal` | `charcoal_dusts`; `c:charcoal_dusts`, `c:dusts/charcoal` | `parts/mod/gunpowder.json.ingredients[0][1]` |
| `dustCoal` | `coal_dusts`; `c:coal_dusts`, `c:dusts/coal` | `parts/mod/gunpowder.json.ingredients[0][2]` |
| `dustSaltpeter` | `saltpeter_dusts`; `c:saltpeter_dusts`, `c:dusts/saltpeter` | `parts/mod/gunpowder.json.ingredients[2]`; `parts/vanilla/gunpowder.json.ingredients[2]` |
| `dustSulfur` | `sulfur_dusts`; `c:sulfur_dusts`, `c:dusts/sulfur` | `explosives/debilitation.json.key.s`; `explosives/incendiary.json.key.a`; `parts/battery.json.key.S`; `parts/mod/gunpowder.json.ingredients[1]`; `parts/vanilla/gunpowder.json.ingredients[1]`; `spikes.2.json.key.c` |
| `ingotCopper` | `copper_ingots`; `c:copper_ingots`, `c:ingots/copper` | `parts/battery.json.key.C`; `parts/circuit.advanced.json.key.c`; `parts/circuit.basic.json.key.i`; `parts/wire.copper.json.key.c`; `tools/defuser.json.key.i` |
| `ingotGold` | `gold_ingots`; `c:gold_ingots`, `c:ingots/gold` | `machine/radar.json.key.g`; `parts/circuit.elite.json.key.i`; `parts/wire.gold.json.key.g` |
| `ingotIron` | `iron_ingots`; `c:iron_ingots`, `c:ingots/iron` | `concrete.2.json.key.o`; `explosives/sonic.json.key.i`; `launcher/support.json.key.i`; `missile/vanilla/missile.module.json.key.i`; `parts/battery.json.key.I`; `parts/clump.steel.json.key.i`; `reinforcedGlass.json.key.i`; `spikes.json.key.i` |
| `ingotSteel` | `steel_ingots`; `c:steel_ingots`, `c:ingots/steel` | `bomblet/bomblet.json.key.i`; `launcher/base.json.key.i`; `launcher/connector.json.key.i`; `launcher/screen.json.key.s`; `machine/cruiselauncher.json.key.S`; `missile/missile.module.json.key.i`; `parts/plate.steel.json.key.i`; `tools/radargun.json.key.i`; `tools/remote.json.key.i`; `tools/rpg.json.key.s` |
| `ingotUranium` | `uranium_ingots`; `c:uranium_ingots`, `c:ingots/uranium` | `explosives/nuclear.json.key.i[0]` |
| `paper` | `papers`; `c:paper`, `c:papers` | `parts/battery.json.key.P` |
| `pelletUranium235` | `uranium_235_pellets`; `c:uranium_235_pellets`, `c:pellets/uranium_235` | `explosives/nuclear.json.key.i[1]` |
| `plankWood` | `planks`; `#minecraft:planks` + optional common aliases | `parts/circuit.basic.json.key.b`; `parts/wire.copper.json.key.w`; `parts/wire.gold.json.key.w`; `tools/defuser.json.key.w`; `tools/radargun.json.key.w`; `tools/rpg.json.key.w` |
| `plateSteel` | `steel_plates`; `c:steel_plates`, `c:plates/steel` | `launcher/base.json.key.p`; `machine/cruiselauncher.json.key.P`; `machine/emptower.booster.json.key.p`; `machine/emptower.json.key.p`; `machine/radar.json.key.p` |
| `stickWood` | `wooden_rods`; vanilla stick + optional `c:wooden_rods` / `c:rods/wooden` | `parts/wire.copper.json.key.s`; `parts/wire.gold.json.key.s` |
| `string` | `strings`; `c:strings` | `grenades/anvil.json.key.s`; `grenades/attractive.json.key.s`; `grenades/chemical.json.key.s`; `grenades/colors.json.key.s`; `grenades/condensed.json.key.s`; `grenades/debilitation.json.key.s`; `grenades/incendiary.json.key.s`; `grenades/repulsive.json.key.s`; `grenades/shrapnel.json.key.s`; `grenades/smoke.json.key.s` |
| `treeLeaves` | `leaves`; `#minecraft:leaves` + optional common aliases | `parts/vanilla/saltpeter.json.key.P[0]` |
| `treeSapling` | `saplings`; `#minecraft:saplings` + optional common aliases | `parts/vanilla/saltpeter.json.key.P[1]` |
| `wireCopper` | `copper_wires`; `c:copper_wires`, `c:wires/copper` | `launcher/connector.json.key.w`; `launcher/screen.json.key.w`; `parts/circuit.advanced.json.key.w`; `tools/rpg.json.key.g` |
| `wireGold` | `gold_wires`; `c:gold_wires`, `c:wires/gold` | `machine/emptower.booster.json.key.w`; `machine/emptower.json.key.w`; `parts/circuit.elite.json.key.w` |

Common paths for exported-only forms: `c:steel_clumps` / `c:clumps/steel`,
`c:iron_plates` / `c:plates/iron`. Their exact private suffixes are implementation
choices; runtime tag membership and source-node provenance are the contract.

## Conditional alternatives and unions

`lib/recipe/ConditionOreValue.java` checks for at least one nonempty registered
stack when `condition=true`, and its absence for `false`. Equivalent checks must
use the **current reload's resolved tags**, not generation-time assumptions or
previous registry tag state. Fabric API 0.77.0 provides native
`fabric:tags_populated`, `fabric:and`, `fabric:or`, and `fabric:not` under the
recipe's **`fabric:load_conditions`** field. `tags_populated` defaults to the
`minecraft:item` registry and takes a `values` list. Its implementation reads
`ResourceConditionsImpl.LOADED_TAGS`, the current deserialized tag map.

| Source recipe alternatives | Exact source condition / ingredient behavior |
| --- | --- |
| `explosives/nuclear.json` | Load if uranium ingots **or** uranium-235 pellets exist. Every `i` position accepts the union of those two forms. Four thermobaric explosives surround five uranium/pellet ingredients. |
| `explosives/mekanism/nuclear.json` | Load only if both uranium forms are absent **and** `mekanism:atomicalloy` exists; atomic alloy replaces each uranium position. |
| `explosives/vanilla/nuclear.json` | Load only if both uranium forms and atomic alloy are absent; five wither skeleton skulls replace uranium. |
| Mekanism/vanilla antimatter and redmatter | Select solely by atomic-alloy item presence/absence. Eight atomic alloys or eight nether stars surround the prior-tier explosive. |
| `parts/mod/gunpowder.json` | Load if any carbon/charcoal/coal **dust** tag is nonempty. One ingredient accepts their union; the others accept sulfur and saltpeter dust. |
| `parts/vanilla/gunpowder.json` | Load only when all three dust families are absent. Its carbon slot accepts ordinary coal **or charcoal items**, not dust. |
| `missile/missile.module.json`, `bomblet/bomblet.json` | Require both basic circuits and steel ingots. |
| `missile/vanilla/missile.module.json` | Requires **both** basic circuits and steel ingots to be absent. The source has no module fallback for only one missing family; do not silently describe an OR predicate as exact parity. |
| Three `missile/special/` recipes | Require basic circuits. SAM's vanilla alternative requires circuits absent; cluster and held-item have no published vanilla-circuit fallback. |

Official Mekanism has no Fabric 1.19.2 release. The port does not fabricate an
atomic-alloy item or claim binary compatibility. Uranium and carbon alternatives
are useful with real Fabric providers and must remain enabled by their tag
conditions. Vanilla fallbacks must become unavailable after a datapack introduces
the corresponding valid material, and become available again after its removal.
Local circuit/steel exports normally keep their component recipe paths selected.

Other unions and metadata distinctions:

- `parts/vanilla/saltpeter.json` accepts leaves **or** saplings in each `P` slot.
- Antidote accepts exactly melon, pumpkin or wheat seeds, not every tagged seed.
- Coal metadata 0 and 1 map to separate modern coal and charcoal items; they must
  remain a union where the source uses an array.
- Colored explosive wildcard `minecraft:dye:32767` maps to modern dye colors.
  Modern `c:dyes` has all dye items, but the old dye item also represented raw
  lapis, cocoa beans, ink sacs and bone meal. Color-semantic mapping must be
  distinguished from literal preservation of those old raw-item variants.
- Source `missile/missile.module.json` uses flint and steel metadata **0**;
  its vanilla fallback uses wildcard **32767**. Modern vanilla item ingredients
  ignore damage/NBT, so accepting worn tools in both is a documented remaining
  difference unless a damage-sensitive ingredient is implemented.
- Explosive metadata is noncontiguous: 12/14 are unused; thermobaric=13,
  nuclear=15, antimatter=22, redmatter=23, module=24. Do not use enum ordinals.
- Some source ingredients deliberately remain direct component metadata; for
  example `explosives/emp.json.key.s` explicitly names `icbmclassic:wire:0`
  rather than `wireCopper`. Exporting copper wire to a common tag does not
  authorize widening that originally direct ingredient.

Radioactive dirt and stone are environmental/fallout blocks; the pinned source
has **no radioactive crafting recipe or OreDictionary export** for them. Nuclear
recipe materials are not automatically radiation sources, and radiation membership
must not be inferred from an ore tag name.

## Fabric 1.19.2 evidence

Inspected Fabric API's cached `fabric-convention-tags-v1` version
`1.3.0+4bc6e26290`, `ConventionalItemTags.java`: `IRON_INGOTS`, `GOLD_INGOTS`,
`COPPER_INGOTS` and `DYES` explicitly register `iron_ingots`, `gold_ingots`,
`copper_ingots`, `dyes` through `TagRegistration.registerCommon`. Thus the older
plural suffix convention is real for this target, not inferred from current
1.21 documentation.

Read-only Modrinth queries selected actual Minecraft 1.19.2/Fabric releases,
then the published JAR tag/recipe JSON was inspected (no art copied):

- [TechReborn 5.4.0](https://cdn.modrinth.com/data/3eMENr4V/versions/cI44hTAA/TechReborn-5.4.0.jar)
  publishes `c:steel_ingots`, `steel_plates`, `iron_plates`, `sulfur_dusts`,
  `saltpeter_dusts`, `coal_dusts`, `charcoal_dusts`. Corresponding files are
  `data/c/tags/items/<name>.json`, additive literal members such as
  `techreborn:steel_ingot`. Its recipes also use vanilla planks/leaves/saplings.
- [Modern Industrialization 1.6.19](https://cdn.modrinth.com/data/Gov5Dboq/versions/2WEOIbpR/Modern-Industrialization-1.6.19.jar)
  publishes `c:steel_ingots`, `steel_plates`, `iron_plates`, `sulfur_dusts`,
  `carbon_dusts`, `coal_dusts`, `uranium_ingots`, and separate uranium-235
  ingot/dust/nugget/block tags. Its recipe inputs use these common material tags.

Neither inspected JAR publishes generic `c:basic_circuits`, `advanced_circuits`,
`elite_circuits`, `copper_wires` or `gold_wires`. Such tag exports from this port
are a compatibility convention/datapack extension point, **not evidence that
those mods automatically substitute their circuit/cable items**. TechReborn
recipes reference its `electronic_circuit`, `advanced_circuit` and
`insulated_copper_cable` directly. Modern Industrialization has its own circuit
progression and `me_wires` category. Explicit compatibility packs may equate
appropriate components, but this audit does not assert tier/form equivalence.

Likewise, modern hierarchical and optional `forge:` aliases broaden interoperability
for providers that actually publish them. Their existence as aliases does not
prove those two 1.19.2 mods use those names. Do not manufacture uranium pellets
from MI ingot/nugget/dust membership merely because all are uranium-235.

## Verification contract

Validation must check both the generated graph and runtime behavior:

1. Every pinned `forge:ore_dict` JSON node maps to its private union, preserving
   all OR arrays and all original direct-item nodes. Exports use correct forms;
   private/common optional references are resolvable and acyclic.
2. A test datapack adds a nonlocal stand-in to legacy common tags, then modern
   hierarchical aliases, then optional Forge aliases. ICBM recipes accept each
   through the same private union. Removing the provider removes acceptance.
3. Tag additions merge rather than replace another mod's values. Port outputs
   appear under both published common conventions and can be consumed by an
   independent recipe, without private-tag knowledge.
4. Reload from empty uranium/carbon tags to populated tags and back switches
   optional recipes and fallbacks in the same reload; recipe book/JEI and server
   crafting agree. Empty placeholders do not count as populated materials.
5. Coal/dust, ingot/plate and uranium-form negative tests prevent accidental
   substitutions. Radioactive blocks remain environmental, not fabricated ore
   recipes. Real mod-combination tests are additional evidence; stand-in tags
   verify the contract without claiming every external mod is supported.

All 90 source OreDictionary ingredient nodes have retained mappings in `tools/upstream_recipe_mapping.json`; all 26 imported/exported material groups are defined in `tools/ore_dictionary_mapping.json`. The resource generator applies these mappings last, and validation rejects flattened ingredients or cyclic tag references. Final build, resource-audit and GameTest results belong in the validation record.
This source audit is not itself a claim that every external mod combination was run.
