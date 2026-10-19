# EMC values for the ports

Every item a port registers either has an EMC value or is deliberately excluded. The values are plain data
in each mod's shared resources. No port depends on an EMC mod, so nothing changes when one is absent.

| Port | Loaders | Items | Explicit | Excluded | Details |
| --- | --- | ---: | ---: | ---: | --- |
| Advanced Genetics | Forge, Fabric | 94 | 39 | 0 | [`mods/advancedgenetics/emc`](../mods/advancedgenetics/emc/README.md) |
| BuildCraft | Forge, Fabric | 43 | 0 | 4 | [`mods/buildcraft/emc`](../mods/buildcraft/emc/README.md) |
| ICBM | Fabric | 142 | 3 | 0 | [`mods/icbm_port/emc`](../mods/icbm_port/emc/README.md) |
| JurassiCraft | Forge, Fabric | 626 | 326 | 55 | [`mods/jurassicraft/emc`](../mods/jurassicraft/emc/README.md) |
| Mystcraft | Forge, Fabric | 24 | 1 | 8 | [`mods/mystcraft/emc`](../mods/mystcraft/emc/README.md) |

Each port's `emc/README.md` is generated. It lists every explicit value and exclusion with its reason, the
machine conversions, and the processes checked for EMC gain.

## Which EMC mod reads the data

**ProjectE.** The pack's ProjectE is [CurseForge file 4860858](https://www.curseforge.com/minecraft/mc-mods/projecte/files/4860858):
ProjectE 1.1.0 for Minecraft 1.19.2. It is a Forge-only jar: its `META-INF/mods.toml` requires Forge 43.1.3 or
newer, and it has no `fabric.mod.json` or Quilt metadata. Tekxit 4 runs on Fabric, and no Forge-on-Fabric bridge
exists for 1.19.2 (Sinytra Connector starts at 1.20.1). So this ProjectE cannot load in Tekxit 4, and it would
never see the Fabric builds of these mods. It only reads our data in Forge instances.

**Item Alchemy.** The Fabric EMC system available to Tekxit 4 is Item Alchemy 1.1.3 (pitan76), which offers
ProjectE-style transmutation. It is what actually reads EMC data on Fabric.

Each port therefore ships two files in its shared `src/main/resources`, which both loader jars contain:

- `data/<ns>/pe_custom_conversions/<ns>.json` is a ProjectE 1.1.0 custom-conversion file, used by Forge builds
  and by any future Fabric ProjectE port that keeps the same data format. It has three parts:
  - `values.before` holds the explicit values.
  - `groups` holds the machine conversions ProjectE cannot see.
  - Each excluded item gets a conversion from the never-valued `FAKE|no_emc`, ProjectE's own idiom for "no
    EMC". Everything else is left to ProjectE's recipe mapper.
- `data/<ns>/default_emcs/<ns>.json` is an Item Alchemy default table, a flat `{item: value}` map in which `0`
  means no EMC. It lists every item. Item Alchemy's own recipe generator is greedy: it takes the first
  recipe and first stack it finds, floors each ingredient, and counts unknown ingredients as 0. Its results
  would differ from ProjectE's minimum-cost values, so the table leaves nothing for the generator to guess.

### Item Alchemy in an existing pack

Item Alchemy reads `default_emcs` only when `config/itemalchemy/emc_config.json` does not exist. Tekxit ships
that file, so on an existing install the defaults are ignored. Merge the port values into it instead:

```sh
python3 scripts/emc.py merge-itemalchemy path/to/config/itemalchemy/emc_config.json
```

Values a port fills in for unvalued vanilla items (`vanilla` in the policy) are added only where the config has
none. Then restart the server or run `/itemalchemy reloademc`. `/itemalchemy resetemc` also picks up the defaults, but
it discards every customisation in the pack's config.

Tekxit's Item Alchemy config has some quirks of its own: a glass bottle is 48 but a water bottle is 1, coal is
3904, a pumpkin is 9 against a melon's 144, and a wither skeleton skull is 16, so three transmuted skulls can
summon a Wither for a nether star worth 139,264. The tool uses Tekxit's values as the vanilla baseline and does not
change them, so our values stay consistent with the pack. The checks below are run against them.

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

Vanilla baselines (`scripts/emc/*-vanilla.json`) come from ProjectE's pregenerated values with its default
configuration, and from Tekxit's Item Alchemy config, falling back to a fresh one.
`python3 scripts/emc.py refresh-baselines` rebuilds them from the jars.

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
  booster packs and the ink mixer.
- **No devaluing vanilla.** A port recipe that would make a vanilla item cheaper than its fixed value fails the
  check; ICBM's sulfur is three gunpowder for this reason. So does a port item that would become the cheapest
  member of a tag vanilla recipes use.
- **Explosives** keep their recipe cost, so they are never cheaper to transmute than to craft, and crafting then
  burning one never gains. This includes ICBM's nuclear, antimatter and red matter tiers, which cost a few
  hundred thousand to a few million EMC because of their wither skeleton skulls and nether stars.
- **Vanilla gaps** (`vanilla`): when an EMC mod's defaults leave a vanilla ingredient unvalued, a port may ship a
  value for that mod only. It applies pack-wide, and `emc.py` refuses to override an existing default. ICBM gives
  ProjectE's wither skeleton skull a third of a nether star, because three skulls summon a Wither that drops one.
  A cheaper skull would let transmuted skulls farm nether stars. A `process` limited with `systems` checks this
  for ProjectE only, since Tekxit's own Item Alchemy skull value already makes it a loop there.

Villager trades, loot and mob drop rates are not mapped, as in ProjectE.

## Checks

- `python3 scripts/emc.py check` (also run by `python3 -m unittest discover -s scripts/tests` and by CI) fails
  if any of these hold:
  - A registered item has neither a value nor an exclusion.
  - Two policy entries claim the same item.
  - A process gains EMC, or a recipe devalues vanilla.
  - A shipped file breaks the ProjectE or Item Alchemy grammar.
  - A generated file is stale.
- **GameTests.** Each port's GameTest server runs `EmcAudit`, which checks that:
  - Every item in the live registry has a decision in `emc/values.json`.
  - `values.json` names no unregistered item.
  - Both shipped files load.

  The run also writes the live registry to `emc-registry.txt`.

  With the real EMC mod installed, it also requires every live value to equal the expected one:
  - Forge: `-PwithProjectE` adds ProjectE 1.1.0 (file 4860858) and turns on `pregenerate`.
  - Fabric: `-PwithItemAlchemy` adds Item Alchemy 1.1.3 with a fresh config.

| Port | Run |
| --- | --- |
| JurassiCraft | `./scripts/gradle.sh :jurassicraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithItemAlchemy` |
| Mystcraft | `./scripts/gradle.sh :mystcraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/mystcraft/fabric runGametest -PwithItemAlchemy` |
| BuildCraft | `./scripts/gradle.sh :buildcraft:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/buildcraft/fabric runGametest -PwithItemAlchemy` |
| ICBM | `./scripts/gradle.sh :icbm_port:runGametest -PwithItemAlchemy` |
| Advanced Genetics | `./scripts/gradle.sh :advancedgenetics:runGameTestServer -PwithProjectE`, `./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest -PwithItemAlchemy` |

CI runs every audit in the table against its real EMC mod. That catches stale values, changed dependency
coordinates and data-format regressions.

## Adding EMC to a port

Use this for Extreme Reactors or any new port.

1. **Registry.** Copy `EmcAudit` (Mojang-mapped: `mods/jurassicraft/src/gametestCommon/.../EmcAudit.java`;
   Yarn: `mods/icbm_port/src/gametest/.../EmcAudit.java`) into the port's GameTest sources. Add a one-line test
   calling `EmcAudit.run(server, MOD_ID)`. Pass `-Demc.values=<port>/emc/values.json` (a Forge run `property`
   or a Loom `vmArg`). Run the GameTests once and copy the `emc-registry.txt` they write to
   `mods/<id>/emc/registry.txt`.
2. **Policy.** Write `mods/<id>/emc/policy.json`; JurassiCraft's is the most complete example. It needs:
   - `namespace`.
   - `loaders`: `forge` or `fabric` for each EMC system. Use `fabric` for both on a Fabric-only port.
   - `resources`: the roots holding recipes and tags. Include loader-specific roots, such as `src/forge/resources`.
   - `output`: the shared resources root. After a restructure, point it at the new shared root.
   - Then `values`, `exclude`, `conversions` and `processes`, each with a `why`.
   - `vanilla` only for vanilla ingredients an EMC mod leaves unvalued (see the exploit rules).
   - `ignored` is only for test-only registrations.
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
