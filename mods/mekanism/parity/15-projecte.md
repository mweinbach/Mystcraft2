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
  crafting recipe. Raw osmium and uranium are fixed to the ingot, and tin and lead are fixed to Item Alchemy's
  tags, so those chains are not repriced. The guard refuses the recipe instead.

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

The audit is static. The live comparison the other ports run is not wired here because it does not hold for
Mekanism's data:

- ProjectE's `OresBlacklistMapper` and `RawOresBlacklistMapper` clear the ores and raw ores the static table prices
  from Item Alchemy's tag values, so live ProjectE reports 0 for them.
- Mekanism's ProjectE recipe mappers may add a conversion at any cost at least the static value, so a mapped recipe
  can raise the live ProjectE value above the `projecte` column.

A trial `-PwithProjectE` run reported 518 deviations for that reason (505 higher, 13 lower). Item Alchemy's live
table matched all 779 entries in a trial `-PwithItemAlchemy` run, but the shared audit stays static so both loaders
check the same thing. Making the live columns agree would mean changing EMC values or mapper behaviour, which is out
of scope for this test change.

The optional live check remains available to a future change that reconciles those differences.

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
