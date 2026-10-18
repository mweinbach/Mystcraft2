# Isolated material compatibility fixture

This source set is a separate test mod, `icbm_port_materialtest`. Its fake items
and three optional NORMAL built-in datapacks must never be included in the production
mod jar or the ordinary GameTest source set. The dedicated material GameTest run
loads one sequential test so resource reloads cannot race ordinary gameplay tests.

Provider fixture tags:

- `c:steel_ingots` and `c:ingots/steel` contain different fake steel items.
- `c:ingots/copper` supplies fake copper.
- `forge:plates/steel` supplies fake steel plate.
- `c:sulfur_dusts`, `c:saltpeter_dusts`, `c:carbon_dusts` supply fake dusts.
- `c:uranium_ingots` supplies fake uranium.
- `c:basic_circuits` and `c:copper_wires` supply fake circuit and wire.

Advanced circuits accept dictionary-equivalent wire/circuit ingredients because
the source uses ore-dictionary entries there. EMP explosive direct copper-wire
metadata ingredients reject that same tagged foreign wire and require the exact
port item. This exercises the source distinction with the same foreign item.

The built-in pack profile name is `icbm_port_materialtest/foreign_materials`.
Fake items exist at startup, while their tag memberships remain absent until
that pack is enabled. This distinguishes item registration from material supply.

The test checks baseline recipes and exports, asynchronously enables the provider
using `MinecraftServer.reloadResources`, checks crafting substitutions, negatives,
conditional selection and water-bucket remainders, then asynchronously disables
the provider and repeats baseline checks. It uses no blocking future joins; reload
completion only updates state on the server executor, and test assertions run in
the GameTest tick callback. The extended sequence below allows 3000 ticks for slower resource reloads.

Compile verification can use the normal dedicated Gradle source set configured by
the root task. The dedicated Gradle run and all five server reloads passed; see ../../docs/VALIDATION.md.

The same test continues through two additional NORMAL packs after foreign-provider
removal. `missing_circuit` replaces only the private basic-circuit union with an
empty tag, proving the source's one-family gap: neither main nor vanilla module
recipe exists while steel remains available; repeater SAM fallback does exist.
`missing_components` empties both private circuit and steel unions, enabling the
iron/redstone-block vanilla module fallback. Empty bomblet, held-item and cluster
casing recipes remain absent in both cases. Public common-tag exports stay intact.
A final baseline reload confirms all normal recipes return and fallback recipes
leave no stale entries. The extended test allows 3000 ticks for all five reloads.

Two always-loaded fixture recipes consume old-style and nested common steel tags
and craft distinct-count fixture outputs from the port's own steel ingot. This
checks the export direction through actual recipe matching/crafting, including
while private unions are overridden. A baseline empty Forge steel-plate tag keeps
its runtime TagKey present when the optional provider pack is removed.
