## ProjectE recipe mappers

Upstream Mekanism `v1.19.2-10.3.9.13` turns machine recipes into ProjectE conversions with the classes in
`mekanism.common.integration.projecte`. Those classes import ProjectE and Forge, so this port keeps them in
`src/forge`. The Fabric JAR does not contain them. ProjectE is optional: both loader JARs load and the GameTests
run without it. A Forge development run adds it with `-PwithProjectE`.

| Mod | Loader | Version | Coordinate |
| --- | --- | --- | --- |
| ProjectE | Forge | 1.1.0 (CurseForge file 4860858), the build whose custom-conversion format `scripts/emc.py` targets | `curse.maven:projecte-226410:4860858`, compile-only; runtime only with `-PwithProjectE` |
| Item Alchemy | Fabric | 1.1.3 | No mapper API. Tekxit reads `default_emcs` |

ProjectE discovers `@RecipeTypeMapper` and `@NBTProcessor` by scanning the mod file. Mekanism does not call those
classes itself. `MekanismHooks` sets `ProjectELoaded` from the mod list and, only then, loads `NSSHelper` by name
to register the gas, infuse type, pigment and slurry serializers. A missing class is ignored, which is the Fabric
case. The shared hook does not mention a ProjectE type.

### What the mappers are allowed to add

The static table in `emc/policy.json` is already the crafting cost of every survival item, including the machine
steps ProjectE cannot see on its own. `scripts/emc.py` writes `data/mekanism/projecte_mapper_guard.json` with every
item whose ProjectE value is at least one. `ProjectEMapperGuard` is consulted from `IngredientHelper.addAsConversion`,
which is the only place a mapper records a conversion.

A conversion is added only when every ingredient is a plain item in that guard, the output is a plain item in the
guard, every amount is positive, and `cost / count` (integer division) is at least the output's static value.
Anything else is left out:

- Chemical, fluid, slurry, pigment and infusion inputs. Their JSON form contains `|`. The static conversions
  already price the item those recipes produce, including the enriched material an infuser consumes.
- NBT variants, tags, excluded items and items worth less than one EMC. They are absent from the guard.
- Negative amounts. The sawmill mapper subtracts its byproduct; that ingredient fails the amount check. The
  sawmill and pressurized reaction mappers also stay disabled (`isAvailable` returns false), matching upstream,
  so a player who enables them in ProjectE's config still cannot undercut the table.
- Ore multipliers and the other item recipes whose output would be cheaper than the static value. Enriching three
  raw ore into four dust, or sawing a log into six planks, would pull dust and planks below the ingot and the
  crafting recipe. ProjectE's ore blacklists already zero the ores and raw ores, so those chains have no ProjectE
  value to reprice, and the guard refuses the recipe for every other item.

Equal-cost item recipes, such as crushing one ingot into one dust when both have the same value, may be mapped.
ProjectE keeps the minimum, so a duplicate at the same cost does not move the value.

### GameTest coverage

The shared GameTests run `EmcAudit` on both loaders. It covers the core module and the three separate module jars
(`mekanism`, `mekanismgenerators`, `mekanismtools`, `mekanismadditions`) and checks that:

- every live registry item in those namespaces has a decision in `emc/values.json`;
- `emc/values.json` names no unregistered item, skipping entries listed under `ignored`;
- `data/<namespace>/pe_custom_conversions/<namespace>.json` and
  `data/<namespace>/default_emcs/<namespace>.json` both load for each of the four namespaces.

The audit reads the expected decisions from `-Demc.values`, which both GameTest runs set to
`mods/mekanism/emc/values.json`. It writes the live registry to `emc-registry.txt` in the run directory, the input
`scripts/emc.py` expects when `emc/registry.txt` is refreshed.

With ProjectE (`-PwithProjectE`, Forge) or Item Alchemy (`-PwithItemAlchemy`, Fabric) installed, the audit also
requires every live value in the four namespaces to equal the `projecte` or `itemalchemy` column, as in the other
ports, and writes the live values to `emc-actual-<mod>.json`. CI runs both comparisons in Mekanism's matrix entry.
All 779 values match on both loaders, and no item is exempt.

### Why the live ProjectE values used to differ

A trial `-PwithProjectE` run in #106 reported 518 deviations (505 higher, 13 lower), while Item Alchemy matched all
779. The differences came from ProjectE itself and from a file the generator did not manage, not from Mekanism's
mappers:

- **Upstream `pe_custom_conversions/defaults.json`.** Mekanism's own ProjectE file gave fluorite 576, HDPE pellets 32
  and salt 8 as `before` values. They overrode the generated table (fluorite 256) and fed every pellet, plastic and
  polonium recipe. The pellet and salt figures also let a machine create EMC: salt is evaporated from water, which
  has no EMC, and a pellet is made from 2/5 EMC of substrate. The port removed the file; the policy prices or
  excludes those items, and `emc.py check` now fails if a port ships a ProjectE file it does not generate.
- **`metals.json`.** ProjectE prices `#forge:ingots/tin` as one iron ingot, lead and osmium as two, and bronze as
  three copper plus one tin over four. That raised every conversion that named `#forge:ingots/osmium` by 128 per
  ingot (circuits, factories, casings) even though the osmium ingot keeps its explicit 384. The generator now models
  these conversions and writes the item, not the tag, into Mekanism's conversions. Tin, lead and bronze ingots keep
  explicit values (189, 252, 99) because the `metals.json` prices would make nuggets and dusts craft into ingots
  worth more than their inputs.
- **Ore blacklists.** `OresBlacklistMapper` and `RawOresBlacklistMapper` zero the tin, lead and deepslate ores and the
  raw lead, osmium, tin and uranium the static table priced; the raw-ore storage blocks then have no recipe value.
  The generator now predicts 0 for them and records why under `no_value`.
- **`TagMapper`.** Every tag a custom conversion names becomes worth its cheapest member and so does each member.
  Naming `#forge:glass`, `#forge:glass_panes`, `#minecraft:coals` and `#mekanism:personal_storage` lowered stained
  and tinted glass, coal and the personal chest. The generated file now names the cheapest member for such tags.
- **Bucket remainders.** Mekanism's fluid buckets leave an empty bucket in the grid, which ProjectE's crafting
  mapper subtracts (the frost walker unit uses two hydrogen buckets). The policy now applies that remainder for
  ProjectE.

Mekanism's recipe mappers were not changed. With the table equal to ProjectE's own result, the guard only admits
conversions that cost at least the mapped value, so a mapper can never move a value.

### Fabric

Item Alchemy has no recipe-mapper API and no custom NSS types. It reads `data/<namespace>/default_emcs/<namespace>.json`.
The same policy writes ProjectE's `pe_custom_conversions` groups for the machine steps. Both files are in the
shared resources, so the Fabric JAR carries the identical values. There is nothing for a Fabric mapper to add, and
no chemical EMC that could flow back into an item.

### Generator buckets

Bioethanol, deuterium, tritium and fusion fuel have no item recipe. They used to be excluded. The bio generator
turns one bio fuel into 200 mB, so a bioethanol bucket is five bio fuel plus the bucket (769). Deuterium is
separated from heavy water, tritium is lithium from the solar neutron activator, and one of each makes two fusion
fuel. Those inputs come from water, so each of those buckets is an empty bucket (768). The processes are in the
policy, and `emc.py check` fails if a route fills one for less than its value.
