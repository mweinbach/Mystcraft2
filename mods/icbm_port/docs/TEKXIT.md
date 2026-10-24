# Tekxit 4 integration

The compatibility target is official **Tekxit 4 client 20.0.4** (the client-side
hotfix) with **server 20.0.0**, Minecraft **1.19.2**, **Java 17**, Fabric Loader
**0.18.4**, and Fabric API **0.77.0+1.19.2**. These are the audited pack versions;
this document does not promise identical behavior for other packs or local recipe
changes. The mod's minimum loader declaration remains 0.16.14; the actual Tekxit
validation target uses 0.18.4.

Official distribution links, checked against the Tekxit download listing:

- [Tekxit website](https://tekxit.lol/)
- [Client 20.0.4 archive](https://tekxit.lol/downloads/tekxit4/20.0.4Tekxit4.zip)
- [Server 20.0.0 archive](https://tekxit.lol/downloads/tekxit4/20.0.0Tekxit4Server.zip)
- [Official Technic pack](https://www.technicpack.net/modpack/tekxit-4-official.1921233)

## Installation

Install the production `icbm-fabric-port-0.2.0-alpha.4.jar` from this module's
`build/libs` into **both client and server** `mods` directories. Keep the pack's
matching Fabric API and existing mods. Use the production jar, not `-sources`,
`-dev`, `-tekxit-tests`, or `-emi-tests` artifacts. Back up existing worlds before
adding content. Test jars described below belong only in disposable test instances.

Tekxit includes EMI **1.1.24+1.19.2+fabric**. ICBM supplies a native EMI plugin for
its dynamic cargo, held-item and cluster recipes, including their NBT and empty
carrier remainders. **No additional JEI installation is required.** The native
plugin avoids duplicate ICBM JEI/JEMI displays while EMI is active. Normal vanilla
crafting and smelting recipes continue through EMI's standard recipe integration.
Stock Tekxit ships no REI. Packs that use REI instead get the same ICBM displays
through the native `rei_client` plugin; that combination has not been run in Tekxit.

## Materials and pack policy

The port consumes private `icbm_port:materials/...` unions that import both common
Fabric tag styles, such as `c:steel_ingots` and `c:ingots/steel`, plus optional
Forge-style aliases such as `forge:ingots/steel`. Its own materials are exported
into both common Fabric styles, so other mods' tag-based recipes can consume them.

The actual audited pack jars and merged tag data provide these examples:

| Material | Actual pack providers, in addition to ICBM where applicable |
| --- | --- |
| Steel ingots | Tech Reborn, Industrial Revolution, Ad Astra, Mythic Metals |
| Steel plates | Tech Reborn, Industrial Revolution, Ad Astra |
| Iron plates | Create `iron_sheet`, Tech Reborn, Industrial Revolution, Ad Astra |
| Sulfur dust | Tech Reborn, Industrial Revolution |
| Saltpeter dust | Tech Reborn |
| Coal dust | Tech Reborn, Industrial Revolution |
| Charcoal dust | Tech Reborn |
| Copper ingots | Vanilla copper through shared tags |
| Basic/advanced/elite circuits | ICBM items in the audited shared circuit families |
| Copper/gold wires | ICBM items in the audited shared wire families |

A wire, cable or circuit with a similar display name is not automatically equivalent.
The corresponding common tag must contain it. In particular, the audit did not find
external members in ICBM's circuit/wire unions. Iron ingots are not iron plates.
No uranium-ingot or uranium-235-pellet provider was found in the audited union data:
the source vanilla nuclear recipe therefore remains selected. Available coal and
charcoal dust providers select the source dust-based gunpowder recipe instead of
the coal/charcoal-item fallback. These decisions use live tags on resource reload.

Source ore-dictionary slots accept equivalent materials; source direct item and
metadata slots retain their exact port identities before pack recipe processing.
For example, advanced circuits accept tagged basic circuits and copper wire, while
the source EMP recipe directly names the port's copper-wire item.

Tekxit's Almost Unified **0.8.2** applies an additional pack policy. Its configured
priority order places Minecraft, KubeJS, CraftTweaker, Create, Tech Reborn, Modern
Industrialization and Industrial Revolution ahead of unlisted mods. The expected
unified outputs include Tech Reborn steel/steel plates and Create iron sheets.
Crafted output quantities remain unchanged; duplicate recipes may be removed. The pack's configured common material families
can also turn a direct material ingredient into its common tag: EMP copper wire
is expected to become `c:copper_wires`. At present that audited family has only
ICBM's wire, so this does not add a foreign wire substitute; another provider
could change that later. Mythic Metals 0.17.3 omits its steel nugget from the
common nugget tag, so its recipe can consume shared steel while still producing
Mythic Metals nuggets. That pack exception is preserved.

Existing private union ingredients remain tags under the audited empty
`tagOwnerships` configuration. Saltpeter still interoperates through its matching
tag even though Almost Unified's configured material list names
`potassium_nitrate`, not `saltpeter`. This does not imply saltpeter outputs will
be unified. The inspected CraftTweaker script contains targeted changes to other
mods, with no ICBM-specific removals. Custom scripts, resource packs or Almost
Unified configuration changes can alter these conclusions.

## Reproducing integration checks

From the repository root, with Java 17 available, build production and the two
separate test jars:

```sh
./scripts/gradle.sh :icbm_port:build :icbm_port:remapTekxitTestJar :icbm_port:remapEmiTestJar
```

The outputs are under `mods/icbm_port/build/libs/`:

- `icbm-fabric-port-0.2.0-alpha.4.jar`: production mod, both sides.
- `icbm-fabric-port-0.2.0-alpha.4-tekxit-tests.jar`: server integration test mod.
- `icbm-fabric-port-0.2.0-alpha.4-emi-tests.jar`: client EMI smoke test mod.

Use a disposable copy of the official **client** for the preferred full-pack test
route. Keep all 308 shipped mods and the original recipe, material and world-generation
configuration and scripts; presentation settings may be adjusted for the test
machine. Add the production jar and the two remapped test jars to that copy's `mods` directory.
Also add the separate [Fabric GameTest API 1.1.8+4d0d570390 jar](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-gametest-api-v1/1.1.8+4d0d570390/fabric-gametest-api-v1-1.1.8+4d0d570390.jar).
The pack's Fabric API umbrella jar does **not** include this test module. The
Tekxit test jar must permit the client environment (`environment: "*"`) so its
server-side assertions can run inside the integrated server. Do not install test
jars or this additional test API into normal play instances.

Start the ordinary client, create or open a disposable world with command
permission, and wait for normal world bootstrap. Run:

```mcfunction
/test runall TekxitCompatibilityGameTests
```

Use that exact class selector and case to run only the six ICBM integration
cases. Unfiltered `/test runall` discovers 1,593 tests shipped by other pack mods,
whose unrelated failures do not assess ICBM compatibility. Do not add the ordinary
ICBM GameTest jar. Fabric GameTest API enables the
`/test` command by default on the client. If another launcher disables it, use
`-Dfabric-api.gametest.command=true`. **Do not use `-Dfabric-api.gametest` for this
integrated route**: that flag selects the incompatible headless test server.
Results are shown in test completion feedback and the client/integrated-server
log. The six checks cover actual material inputs, reciprocal crafting, conditional
recipes, Almost Unified outputs, foreign battery transaction conservation and
NBT crafting/remainders. They use actual pack registrations, with no fake providers
or global resource reloads.

### Dedicated harness limitation

The unmodified official pack does not currently support the special dedicated
`GameTestServer` harness. BCLib 2.1.8 fails during generator enforcement with a
null registry access before tests begin; bypassing that startup issue in a
disposable test configuration exposed a second failure: Distant Horizons assumes
the server is a `DedicatedServer` and casts the `GameTestServer` unconditionally.
These are harness integration failures before ICBM assertions, not passing or
failing ICBM compatibility results. The preferred integrated-client route retains
all official BCLib/Distant Horizons mods and configurations; it needs no BCLib
workaround settings or mod removal.

For future harness investigation, the attempted official server command was:

```sh
java -Xms1G -Xmx5G -XX:ActiveProcessorCount=4 \
  -Dfabric-api.gametest \
  -Dfabric-api.gametest.report-file=/absolute/path/to/disposable-server/tekxit-gametest-results.xml \
  -jar Fab_Serv_LaunchWithBatOrSh.jar nogui
```

This command is **blocked by the pack issues above**, not the recommended
validation method. The official launcher's `fabric-server-launcher.properties`
points to `MC_Serv_DoNOTLaunchWithThis.jar`; retain that relationship for ordinary
server startup. The archive ships with `eula=true`. The disposable dedicated
validation copy was reset to `eula=false` before launch; this workflow did not
accept the EULA. Normal server operation requires the operator's own decision.

For the client check, add these JVM arguments in the disposable client's launcher:

```text
-Dicbm.emiSmokeTest=true
-Dicbm.emiSmokeTimeoutTicks=6000
```

Start the ordinary client and enter the same disposable integrated-server world. The
smoke test waits for a stable, baked EMI registry; title-screen time does not count.
Read `icbm-emi-smoke-result.json` in that client's game directory and look for
`[ICBM_EMI_SMOKE] PASS` or `FAIL` in the client log. It checks actual dynamic display
registration, duplicate suppression, payload/cargo NBT distinctions, indexed
recipe lookups, potion defaults and remainders. It leaves the client running for
manual display and visual checks. After running `/reload` and waiting for EMI to
finish rebuilding, run `/icbm_emi_smoke` to repeat these checks in the same session.
The result file records the run number and trigger. Run the six integration cases
again to check the reloaded server recipes as well.

## Existing pack diagnostics

A control launch with ICBM and both ICBM test jars removed reproduced Create's
`PotionFluidHandler.getFluidFromPotionItem` null-NBT exception during EMI late
recipe registration, including after `/reload`. Stock Tekxit also emitted EMI
self-mutated-stack bake warnings for potion, arrow and brew entries. An earlier ICBM
run emitted warnings for filled cells; that specific item family was not
reproduced in the control. The final ICBM startup/reload run emitted no
self-mutated-stack warnings, but the stock Create exception remained. These logs prevent a claim of a warning-free pack.
The ICBM tests separately check its own recipes through EMI's baked input/output
indexes, including cargo NBT and empty carrier remainders. The port does not patch
or disable other mods' recipe integrations.

## Post-alpha.4 extreme payloads in an existing Tekxit world

Antimatter (radius 275) and red matter (horizon up to 300) are deliberate departures from upstream ICBM; see the payload porting notes. An existing `config/icbm_port/payloads.json` from alpha.4 has no `config_version`. On first load, any of antimatter radius 55, red matter radius 12 or red matter duration 2,400 that is still at its old default is moved to 275, 300 or 6,000, and the file is rewritten with `config_version: 2`. Values a server owner changed are kept. To keep the old scale, set the radii back after the upgrade. Both families accept up to 384.

Craters are now cut by direct chunk-section writes. Alpha.4 used `removeBlock`, and neither path fires block-break events, so claim and protection mods were never consulted per block and still are not. They can still cancel the blast as a whole through the ICBM API `BEFORE_ACTION` event, and the config, bounds and admission checks are unchanged. Unlike alpha.4 antimatter, unbreakable blocks such as bedrock now survive. Tekxit's chunk-claim setup should treat these two payloads as world-editing tools. The changes add no mixins (an access widener exposes `Heightmap.set`), so the Tekxit mixin audit is unchanged. No Tekxit client run was repeated for this change.

## Alpha.4 validation status

Local alpha.4 build, 16 JUnit tests, the 92 gameplay GameTests of that time (108 in the current tree; see VALIDATION.md), the five-reload material suite and resource validation passed. The final alpha.4 production JAR passed ordinary client/world startup, all six actual-provider integration tests and 285,316 live EMI assertions both before and after `/reload`. Direct inspection confirmed redmatter terrain consumption and rotating core/rings, antimatter removal, moving radar/EMP hardware and upright launcher models. The run retained all 308 official mod JARs. Shaders were off; ordinary-layer rendering does not by itself establish shader-pack appearance. No upstream artist assets were copied.

The runtime results below were recorded for **alpha.3** and are retained as a historical compatibility baseline. They do not establish alpha.4 visual correctness or revalidate every new change.

## Historical alpha.3 validation

The production build, **88 ordinary GameTests**, **16 JUnit tests** and the isolated
material suite with **five real datapack reloads** passed. Test fixture source sets
also compile in ordinary CI; the full pack is a separate runtime check.

The official client launched with all **308 shipped mod jars**, verified byte for
byte against its archive, plus ICBM and the separate test fixtures. Recipe, material
and world-generation configurations and scripts were retained. Software OpenGL,
render/simulation distance 4, GUI scale 2, disabled shaders and a null audio driver
were used for this environment; audio and shader rendering were not tested.

All **six actual-pack integration GameTests passed** on the ordinary integrated
server. This includes the surviving Mythic Metals nugget recipe and the unique
Industrial Revolution tank, Create coupling and Ad Astra workbench recipes.

The live EMI test passed **285,316 assertions** both after initial world loading
and after an explicit `/reload`, rearmed through `/icbm_emi_smoke`. Both runs
retained **142,061 unique native ICBM displays**, including four information pages,
and checked actual indexed lookups for mixed clusters, Epic Knights pattern cargo,
vanilla potion defaults and reloadable empty casings. All **six integration tests
passed again after the reload**. The native EMI held-item loading pages were
also opened and visually checked in the full client. Stock Tekxit contains no JEI; simultaneous JEI/EMI
loading was not exercised by this pack run.

Full-pack dedicated-server startup remains unverified for the harness reasons
above; integrated-server results do not establish two-client multiplayer or
prolonged survival/automation behavior.
