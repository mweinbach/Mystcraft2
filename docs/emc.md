# EMC values for the ports

Every item a port registers either has an EMC value or is deliberately excluded. The values are plain data
in each mod's shared resources. No port depends on an EMC mod, so nothing changes when one is absent.

| Port | Loaders | Items | Explicit | Excluded | Details |
| --- | --- | ---: | ---: | ---: | --- |
| Advanced Genetics | Forge, Fabric | 94 | 39 | 0 | [`mods/advancedgenetics/emc`](../mods/advancedgenetics/emc/README.md) |
| BuildCraft | Forge, Fabric | 212 | 1 | 34 | [`mods/buildcraft/emc`](../mods/buildcraft/emc/README.md) |
| Extreme Reactors | Forge, Fabric | 113 | 6 | 6 | [`mods/extremereactors/emc`](../mods/extremereactors/emc/README.md) |
| Gridwright | Forge, Fabric | 14 | 0 | 0 | [`mods/gridwright/emc`](../mods/gridwright/emc/README.md) |
| ICBM | Fabric | 142 | 3 | 0 | [`mods/icbm_port/emc`](../mods/icbm_port/emc/README.md) |
| JurassiCraft | Forge, Fabric | 626 | 326 | 55 | [`mods/jurassicraft/emc`](../mods/jurassicraft/emc/README.md) |
| Mekanism | Forge, Fabric | 779 | 15 | 27 | [`mods/mekanism/emc`](../mods/mekanism/emc/README.md) |
| Mystcraft | Forge, Fabric | 24 | 1 | 8 | [`mods/mystcraft/emc`](../mods/mystcraft/emc/README.md) |

Each port's `emc/README.md` is generated. It lists every explicit value and exclusion with its reason, the
values ProjectE's own mappers decide, the machine conversions, and the processes checked for EMC gain.

## Which EMC mod reads the data

**ProjectE.** The pack's ProjectE is [CurseForge file 4860858](https://www.curseforge.com/minecraft/mc-mods/projecte/files/4860858):
ProjectE 1.1.0 for Minecraft 1.19.2. It is a Forge-only jar: its `META-INF/mods.toml` requires Forge 43.1.3 or
newer, and it has no `fabric.mod.json` or Quilt metadata. Tekxit 4 runs on Fabric, and no Forge-on-Fabric bridge
exists for 1.19.2 (Sinytra Connector starts at 1.20.1). So this ProjectE cannot load in Tekxit 4, and it would
never see the Fabric builds of these mods. It only reads our data in Forge instances.

**ProjectE for Fabric (this repository).** [`mods/projecte`](../mods/projecte/README.md) ports that same ProjectE
1.1.0 to Fabric from upstream's sources, with mod id `projecte` and upstream's data loading, so it reads every port's
`pe_custom_conversions` file unchanged. Its own vanilla values match upstream's (a GameTest checks them on both
loaders). Live EMC audits of the ports under it on Fabric, and the Tekxit 4 check with and without Item Alchemy, are
phase 4 of that port and not done yet; until then the Fabric audits below run against Item Alchemy only.

**Item Alchemy.** The Fabric EMC system available to Tekxit 4 is Item Alchemy 1.1.3 (pitan76), which offers
ProjectE-style transmutation. It is what actually reads EMC data on Fabric.

Each port therefore ships two files in its shared `src/main/resources`, which both loader jars contain:

- `data/<ns>/pe_custom_conversions/<ns>.json` is a ProjectE 1.1.0 custom-conversion file, used by ProjectE on
  Forge and by the ProjectE Fabric port in this repository. It has three parts:
  - `values.before` holds the explicit values, except for items ProjectE's ore blacklists zero (a `before` value
    would race those mappers, which run at the same priority).
  - `groups` holds the machine conversions ProjectE cannot see.
  - Each excluded item gets a conversion from the never-valued `FAKE|no_emc`, ProjectE's own idiom for "no
    EMC". Everything else is left to ProjectE's recipe mapper.
- `data/<ns>/default_emcs/<ns>.json` is an Item Alchemy default table, a flat `{item: value}` map in which `0`
  means no EMC. It lists every item. Item Alchemy's own recipe generator is greedy: it takes the first
  recipe and first stack it finds, floors each ingredient, and counts unknown ingredients as 0. Its results
  would differ from ProjectE's minimum-cost values, so the table leaves nothing for the generator to guess.
- `data/<ns>/default_emcs/pack_overrides_<n>.json`, only in ports an override lists (see below), holds the pack
  overrides for a fresh Item Alchemy install.

### Item Alchemy in an existing pack

Item Alchemy reads `default_emcs` only when `config/itemalchemy/emc_config.json` does not exist. Tekxit ships
that file, so on an existing install the defaults are ignored. Merge the port values into it instead:

```sh
python3 scripts/emc.py merge-itemalchemy path/to/config/itemalchemy/emc_config.json
```

Values a port fills in for unvalued vanilla items (`vanilla` in the policy) are added only where the config has
none. Then restart the server or run `/itemalchemy reloademc`. `/itemalchemy resetemc` also picks up the defaults,
but it discards every customisation in the pack's config.

Tekxit's Item Alchemy config has some quirks of its own: a glass bottle is 48 but a water bottle is 1, coal is
3904, and a pumpkin is 9 against a melon's 144. The tool uses Tekxit's values as the vanilla baseline and leaves
them alone, so our values stay consistent with the pack. The checks below are run against them. The one exception
is a short allowlist of pack values that open an EMC loop.

### Pack overrides

[`scripts/emc/itemalchemy-overrides.json`](../scripts/emc/itemalchemy-overrides.json) is the only place a pack's
existing Item Alchemy value may be changed. Every other baseline or config value is still never overridden: a
`vanilla` fill for an item the pack already values fails the check, and `merge-itemalchemy` adds fills only where
the config has none. Each entry has:

- `replaces`: the pack value being corrected, as recorded in `scripts/emc/itemalchemy-vanilla.json`.
- `emc`, or `like` with optional `times` and `round` (`up` or `down`), as in a policy value.
- `ports`: the port directories that ship it.
- `why`: the loop it closes.

How an override is applied:

- **Analysis.** `emc.py` applies it to the Item Alchemy baseline before pricing any port, so every port's values and
  checks see the corrected value. `emc.py check` and `generate` print each applied override as a `pack:` line.
- **Stale entries.** If the recorded baseline no longer holds `replaces` (the pack changed), the check fails and the
  override is not applied.
- **Existing configs.** `merge-itemalchemy` writes it into the config where the config holds `replaces` or has no
  value. Port values were generated against the recorded baseline with the overrides applied, so the config must
  agree with that baseline. If the overridden item holds some other value, or a `like` anchor differs from the
  baseline (a cheaper nether star would make ICBM's skull recipes gain), the command writes nothing and exits 1.
  Refresh the baselines from that config and regenerate first.
- **Fresh installs.** Each port in `ports` ships the values in `default_emcs/pack_overrides_<n>.json`. Item Alchemy
  1.1.3 collects every `default_emcs` file into a `java.util.HashMap` and the last file read wins, and its own
  `itemalchemy:default_emcs/minecraft.json` holds the fresh-config values (a skull of 10,246). `emc.py` picks `<n>`
  so the file's identifier hashes into the last bucket of any table up to 65,536 files, which Item Alchemy's own
  files never do, so the override is read last. `EmcAudit` checks the live value on the Fabric GameTest.
- **Agreement with ProjectE.** A port's `vanilla` fill for the same item must scale the same anchor with the same
  `times` and `round`, so both systems agree.

**The wither skeleton skull** is the only entry. Three skulls on four soul sand or soul soil summon a Wither,
which drops a nether star. Tekxit values the skull at 16 and the star at 139,264, so three transmuted skulls
bought a star for 48 EMC plus the sand. The override sets the skull to a third of the star, rounded up:
ceil(139,264 / 3) = 46,422. ICBM's ProjectE fill uses the same formula, and ProjectE's star is also 139,264, so
the skull is 46,422 in both systems. Three skulls then cost 139,266, at least the star, before the sand. Soul sand
(49 in both systems) and soul soil (1 in Tekxit, 49 in ProjectE) therefore cannot reopen the loop at any value.
ICBM's nuclear-tier recipes and Mekanism's nucleosynthesizer use the skull, so both ship the override.

Item Alchemy also hard-codes values for some vanilla tags (`VanillaEMCDef`, for example `minecraft:fences`), and
those win over `default_emcs`. `emc.py` reports any port item in such a tag whose value would differ.

### Multiplayer

Server and client must use the same loader, as for the ports themselves. EMC values are server-side data.
ProjectE and Item Alchemy sync them to clients.

## How values are chosen

`scripts/emc.py` reproduces ProjectE 1.1.0's graph mapper for each port and writes both files from one
hand-written `emc/policy.json`:

- **Recipes first.** Shaped, shapeless, smelting-family, stonecutting and smithing recipes are mapped as
  ProjectE maps them:
  - Each recipe gives a minimum-cost fixpoint.
  - An ingredient that accepts several items is valued at its cheapest member.
  - Container remainders such as buckets are subtracted.
  - Division results of one or more are floored. Below one they stay fractions, and an item that ends below
    one EMC gets none; `values.json` records it under `below_one`.
  - Recipe load conditions are evaluated for the port's loader (Forge `conditions`, Fabric
    `fabric:load_conditions`).
  - **Recipes other mods enable.** A recipe that loads only alongside another mod is still a way to make its
    output, and Item Alchemy ships fixed values instead of recalculating them. When the other mod is a port in
    this repository, the policy declares it in `optional_mods`. `emc.py` then evaluates every combination of
    those mods. For each combination it loads recipes with the mod present and the mod port's tags added, and
    adds every recipe whose conditions name the mod and could hold whatever the tags are. Every recipe active in
    the combination is read against the merged tags, including unconditional ones. A mod that adds a cheap
    item to a tag the port's recipe accepts makes that recipe cheaper: Mekanism's sulfur dust joins
    `icbm_port:materials/sulfur_dusts`, which lowers ICBM's battery. A mod can also fill a tag that switches its
    own recipe off; Mekanism fills the uranium tags, which disables ICBM's atomic-alloy nuclear recipe. These
    alternatives are priced with the values the other port ships, such as Mekanism's atomic alloy, and every
    output takes the minimum over all its recipes in every combination. The gain check covers every active
    recipe in every combination.

    A recipe gated on a mod with no EMC data in the repository is not priced. `emc.py check` fails if it would
    gain EMC at the shipped values, or if it needs an ingredient with no known EMC. In that case declare the mod,
    exclude the output, or gate the recipe off. The port README lists each optional mod, the recipes it enables
    ("loads with"), and the recipes whose tags it widens ("wider tags with").

    Only the tags of declared optional mods are read. In a pack where an undeclared mod adds a cheaper item to
    one of the port's tags (a third mod's sulfur, say), a recipe can still make its output for less than the
    shipped value.
  - Special recipe types, whose result is empty to ProjectE, are listed in the port README so their outputs get
    a conversion, value or exclusion.
- **Tags per loader.** ProjectE is analysed with Forge tags and Item Alchemy with Fabric conventional `c:` tags,
  using their 1.19.2-era plural names (`c:iron_ingots`, `c:black_dyes`, `c:water_buckets`, ...). Fabric API
  0.77.0+1.19.2's convention tags and Forge 43.5.0's tags are vendored in `scripts/emc`. A port's own tag files,
  such as ICBM's `c:sulfur_dusts`, are added on top. ICBM is Fabric-only, so both of its analyses use `c:` tags.
- **Explicit values only for base items**: things with no recipe, such as mob drops, worldgen, loot or machine
  results. They are written as `like` a vanilla anchor (optionally `times` a factor), so each system scales from
  its own baseline. Fixed numbers are used only where no anchor fits.
- **Machine conversions** (`conversions`) describe machine steps ProjectE cannot see, using its own conversion
  format, including negative amounts for returned containers. Placeholders such as `{species}` expand over the
  registry.
- **ProjectE's own mappers.** The ProjectE analysis also reproduces the mappers ProjectE 1.1.0 always runs, so the
  predicted value of every item is what ProjectE computes:
  - `OresBlacklistMapper` and `RawOresBlacklistMapper` set every member of `#forge:ores` and
    `#forge:raw_materials` to 0. Those items, and anything made only from them (raw-ore storage blocks), have no
    ProjectE value; `values.json` records `projecte: 0` with the reason under `no_value`. Their Item Alchemy values
    are unchanged.
  - ProjectE's `metals.json` prices `#forge:ingots/<metal>` tags with `propagateTags` conversions (tin as one iron
    ingot, lead and osmium as two, bronze from copper and tin, ...). A member without an explicit value takes only
    that price. `emc.py check` fails when a recipe makes such an item for less (nuggets, dusts), because ProjectE
    would only log it; the port then pins an explicit value (Mekanism's tin, lead and bronze ingots).
  - `TagMapper` adds `tag = member` and `member = tag` for every tag a custom conversion names, so each member of
    such a tag is worth at most its cheapest member. ProjectE's own files name the tags in
    `scripts/emc/projecte-tags.json`. A policy conversion that names a tag is written to the ProjectE file as the
    cheapest member when the tag's members are not all worth the same, or when ProjectE prices the tag through
    `metals.json`; otherwise naming it would devalue vanilla members (stained glass, coal) or give excluded members
    EMC. The README lists each such tag.
  - `remainders` may be limited to one system. Mekanism's fluid buckets return an empty bucket, which ProjectE's
    crafting mapper subtracts; Item Alchemy reads only the shipped values, which are left as they were.

Vanilla baselines (`scripts/emc/*-vanilla.json`) come from ProjectE's pregenerated values with its default
configuration, and from Tekxit's Item Alchemy config, falling back to a fresh one.
`python3 scripts/emc.py refresh-baselines` rebuilds them from the jars. With `--projecte-jar` it also records
ProjectE's tag values, its `metals.json` tag conversions and the tags its own conversion files name
(`projecte-tag-values.json`, `projecte-tag-conversions.json`, `projecte-tags.json`).

### Exploit rules

- **No duplication.** Creative, debug and operator items, spawn eggs and anything that places living mobs get no
  EMC.
- **NBT items.** Items whose function lives in NBT (link destinations, Age books, potions, display species) are
  excluded when a blank copy would be broken or dangerous. Otherwise they are valued as their blank form,
  because transmutation never copies NBT.
- **Random yields.** Ores and ore-like blocks with Fortune-boosted or random yields get no value, as in ProjectE.
  Only what they drop is valued.
- **No gain from processes** (`processes`): machine, mob and world steps are checked so that no output is
  worth more than its input, under both value tables. Examples are fossil grinding, bug crates, scalpel scraping,
  booster packs and the ink mixer. A case whose input has no EMC in a system cannot be bought there, so it is
  skipped.
- **No gain from vanilla summons.** [`scripts/emc/pack-processes.json`](../scripts/emc/pack-processes.json) lists
  vanilla summons that turn items into items: the Wither (three skulls on four soul sand or soul soil, one nether
  star) and the iron and snow golems. Every port checks them in both systems.
- **No gain from machine recipes** (`recipe_processes`): every loaded recipe of a machine type is checked in both
  systems. Item inputs cost their cheapest member, and a chemical input costs the item it is stored as, divided by
  `per`. Mekanism checks every antiprotonic nucleosynthesizer recipe this way. At any price an antimatter pellet
  can be bought, which oxidizing turns back into 1,000 mB of gas, most recipes made items worth more than their
  input (13 of 20 at a 256,000 pellet). Antimatter pellets, and the five items crafted from them (the
  nucleosynthesizer, the supermassive QIO drive, and the elytra, gravitational modulating and teleportation
  modules), therefore have no EMC, which closes every recipe. Polonium pellets keep their value: no recipe turns
  them back into gas, so they cannot feed the SPS.
- **No devaluing vanilla.** A port recipe that would make a vanilla item cheaper than its fixed value fails the
  check; ICBM's sulfur is three gunpowder for this reason. So does a port item that would become the cheapest
  member of a tag vanilla recipes use or of a tag ProjectE's `TagMapper` equalises, and the ProjectE file never
  names a tag whose members are worth different amounts.
- **Explosives** are worth their cheapest recipe, so crafting then burning one never gains under any recipe, and
  transmuting one costs as much as its cheapest ingredients. ICBM's nuclear, antimatter and red matter tiers
  have nether-star and skull recipes and atomic-alloy recipes that load only with Mekanism. They are valued at
  the cheapest of those recipes, with Mekanism's items at Mekanism's shipped values (about 140 to 200 thousand
  EMC). That is less than the nether-star recipes cost, which never gains.
- **No gain from any recipe, conditional or not.** Every recipe the port ships counts, including recipes behind
  load conditions and recipes only another mod enables, and none may make an output worth more than its
  ingredients. Where no value can satisfy a recipe, the recipe is gated off instead. Mekanism converts gunpowder
  and sulfur dust one-to-one, so ICBM's dust gunpowder recipe (one sulfur dust makes three gunpowder) does not
  load with Mekanism.
- **Vanilla gaps** (`vanilla`): when an EMC mod's defaults leave a vanilla ingredient unvalued, a port may ship a
  value for that mod only. It applies pack-wide, and `emc.py` refuses to override an existing default; only the
  [pack overrides](#pack-overrides) may do that. ICBM gives ProjectE's wither skeleton skull a third of a nether
  star, rounded up, matching the Item Alchemy override, so the Wither summon check passes in both systems.
- **All ports together.** After every port is built, the vanilla summons, each port's processes and machine recipe
  processes, and each port's recipe gain check run again with one merged table: the baseline, the overrides,
  every port's vanilla fills and every port's values. A value one port ships, such as ICBM's ProjectE skull, cannot
  open a loop through another port's recipe. Two ports filling the same vanilla item differently also fail.

Villager trades, loot and mob drop rates are not mapped, as in ProjectE.

## Checks

- `python3 scripts/emc.py check` (also run by `python3 -m unittest discover -s scripts/tests` and by CI) fails
  if any of these hold:
  - A registered item has neither a value nor an exclusion.
  - Two policy entries claim the same item.
  - A process, vanilla summon or machine recipe process gains EMC, or a recipe devalues vanilla, alone or with
    every port's values merged.
  - A pack override is stale, names an unknown port, or disagrees with a port's `vanilla` fill.
  - Any recipe makes a port item worth more than its ingredients at the shipped values. This covers recipes
    behind load conditions, alternatives an `optional_mods` mod enables, and recipes gated on undeclared mods. A
    conditional alternative that needs an ingredient with no known EMC also fails.
  - A shipped file breaks the ProjectE or Item Alchemy grammar.
  - A generated file is stale.
  - A port ships a `pe_custom_conversions` file the policy does not generate (Mekanism's upstream `defaults.json`
    priced fluorite, HDPE pellets and salt behind the generator's back and was removed), or a `default_emcs` file
    it does not generate.
  - A recipe undercuts a `metals.json` price, an excluded item sits in a tag ProjectE's `TagMapper` values, or a
    `metals.json`-priced tag with several members is named in a conversion.
- **GameTests.** Each port's GameTest server runs `EmcAudit`, which checks that:
  - Every item in the live registry has a decision in `emc/values.json`.
  - `values.json` names no unregistered item.
  - Both shipped files load.
  - With Item Alchemy, every pack override the port ships (`itemalchemy_overrides` in `values.json`) is the live
    value.

  The run also writes the live registry to `emc-registry.txt`.

  With the real EMC mod installed, it also requires every live value to equal the expected one:
  - Forge: `-PwithProjectE` adds ProjectE 1.1.0 (file 4860858) and turns on `pregenerate`.
  - Fabric: `-PwithItemAlchemy` adds Item Alchemy 1.1.3 with a fresh config.

| Port | Run |
| --- | --- |
| Extreme Reactors | `./scripts/gradle.sh :extremereactors:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/extremereactors/fabric runGametest -PwithItemAlchemy` |
| JurassiCraft | `./scripts/gradle.sh :jurassicraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithItemAlchemy` |
| Mystcraft | `./scripts/gradle.sh :mystcraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/mystcraft/fabric runGametest -PwithItemAlchemy` |
| BuildCraft | `./scripts/gradle.sh :buildcraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/buildcraft/fabric runGametest -PwithItemAlchemy` |
| ICBM | `./scripts/gradle.sh :icbm_port:runGametest -PwithItemAlchemy` |
| Advanced Genetics | `./scripts/gradle.sh :advancedgenetics:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest -PwithItemAlchemy` |
| Mekanism | `./scripts/gradle.sh :mekanism:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/mekanism/fabric runGametest -PwithItemAlchemy` |
| Gridwright | `./scripts/gradle.sh :gridwright:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/gridwright/fabric runGametest -PwithItemAlchemy` |

CI runs every audit in the table against its real EMC mod: each port's matrix entry passes `-PwithProjectE` to the
Forge GameTest run and `-PwithItemAlchemy` to the Fabric one, so there is no separate EMC job, and
`scripts/tests/test_ci_workflow.py` keeps every port with EMC data on those flags. ProjectE comes from CurseMaven
and Item Alchemy, mcpitanlibarch and Architectury API from Modrinth's Maven, both declared in each port's build
script, so a clean runner downloads them. That catches stale values, changed dependency coordinates and
data-format regressions; no live value is exempt.

Mekanism ships those two data files for the core module and again for Generators, Tools and Additions;
`scripts/emc.py check` covers them, and its four-namespace `EmcAudit` compares all 779 live values. On Forge,
Mekanism's own ProjectE recipe mappers are an optional compile-only dependency and add a machine recipe only when it
does not undercut the static table (`projecte_mapper_guard.json`), so they never change a value once the table
matches ProjectE; see [`mods/mekanism/parity/15-projecte.md`](../mods/mekanism/parity/15-projecte.md). Item Alchemy
has no mapper API, so the Fabric build uses the static values alone.

## Adding EMC to a port

Use this for Extreme Reactors or any new port.

1. **Registry.** Copy `EmcAudit` (Mojang-mapped: `mods/jurassicraft/src/gametestCommon/.../EmcAudit.java`;
   Yarn: `mods/icbm_port/src/gametest/.../EmcAudit.java`) into the port's GameTest sources. Add a one-line test
   calling `EmcAudit.run(server, MOD_ID)`. Pass `-Demc.values=<port>/emc/values.json` (a Forge run `property`
   or a Loom `vmArg`). Run the GameTests once and copy the `emc-registry.txt` they write to
   `mods/<id>/emc/registry.txt`.
2. **Policy.** Write `mods/<id>/emc/policy.json`; JurassiCraft's is the most complete example. It needs:
   - `namespace`, the port's mod id. `namespaces` lists extra mod ids the same policy prices (Mekanism's
     Generators and Tools modules); it must start with `namespace`. `outputs` then maps every namespace to the resource
     root that ships that namespace's data files. Omit both when the port is one mod.
   - `loaders`: `forge` or `fabric` for each EMC system. Use `fabric` for both on a Fabric-only port.
   - `resources`: the roots holding recipes and tags. Include loader-specific roots, such as `src/forge/resources`.
   - `output`: the shared resources root for `namespace`. After a restructure, point it at the new shared root.
     When `outputs` is set it must match `outputs` for the primary namespace.
   - Then `values`, `exclude`, `conversions` and `processes`, each with a `why`. A `like` value may add
     `"round": "up"` so a share of an anchor never undercuts it (the default floors, as ProjectE does).
   - `recipe_processes` for machine recipe types that turn items, and chemicals stored in items, into items:
     `type`, the recipe fields holding single item inputs (`items`), each chemical field's chemicals with the item
     that stores them and how many units one holds (`chemicals`), and the output fields (`outputs`).
   - `vanilla` only for vanilla ingredients an EMC mod leaves unvalued (see the exploit rules).
   - `ignored` is only for test-only registrations.
   - `conditions` fixes the outcome of the port's own recipe condition types, such as Extreme Reactors'
     `bigreactors:config_condition`, with `holds` set to the shipped config default and a `why`.
   - `optional_mods` maps another mod's id to the port directory under `mods/` that prices it (`port`) and a
     `why`. Declare one when the port ships recipes that load only with that mod. For example, ICBM declares
     `mekanism` → `mekanism` so its atomic-alloy recipes are priced. That port's `emc/values.json` must be
     generated first.
3. **Generate and read.** Run `python3 scripts/emc.py generate <id>` and fix every problem it reports:
   - Give base items a `like` anchor.
   - Turn each custom machine recipe into a conversion.
   - Exclude anything that duplicates or depends on NBT.
   - Describe every machine or mob process that yields items as a `process`.
   - Rerun until it reports `ok`.
4. **Verify against the real mods.** Add the optional runtime dependencies behind the same flags.
   - Forge `build.gradle`: `curse.maven:projecte-226410:4860858` via `fg.deobf` under `-PwithProjectE`. The
     shared `gradle/forge-mod.gradle` already turns on pregeneration.
   - Fabric: `maven.modrinth:item-alchemy:1AYUTFS2`, `mcpitanlibarch:cTAftMK5` and `architectury-api:cde9Su0w`
     under `-PwithItemAlchemy`, with Loom 1.7.4 or newer.
   - Delete `config/itemalchemy` before each Fabric GameTest run.
5. **Commit** the policy, registry, generated README and `values.json`, and the two shipped data files. CI runs
   `emc.py check`.

When a port gains a Fabric build later, its existing data already works there. Rerun step 4 with Item
Alchemy and check that the Fabric resources root is listed under `resources.itemalchemy`.
