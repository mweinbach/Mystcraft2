# Validation — 0.2.0-alpha.4

## Machine, launcher network and radio parity (October 25, 2026)

Branch `icbm/parity-5-machines-radio`, rebased onto the delivery/item, seat/actionable-missile, blast and gas work. Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run, so the revised launcher/defense screens (delay/lock fields, status text, radar Redstone and ±100 channel buttons, EMP cost lines) are not yet visually checked.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` (JDK 17) | Passed; JUnit **32** passed, including the new `LauncherRulesTest` (6: minimum/maximum range, inaccuracy, lock-height and delay clamps) |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy -PwithProjectE` | All **192** required GameTests passed. New: `DefenseParityGameTests` (5: radar column/age/incoming split, state and strong redstone with persisted toggle; SAM-delayed radar alerts; radio lifecycle and recursion bounds; server-side defense button validation; EMP dynamic capacity, upkeep, strong-redstone firing, cooldown energy refusal and radio-triggered firing) and `LauncherParityGameTests` (10: range rules, inaccuracy and lock height, persisted firing delay, a delayed launch carrying its seat rider onto the missile, fail-closed settings, strong-redstone screen firing the network, radio target/trigger with replies on base and cruise launchers, remote detonator, laser designator). Updated: defense radar/extension/radio tests, the launcher network energy test, and `EntityParityGameTests.targetedLauncherClusterUsesProximityTrigger`, which now expects the release point at the missile's inaccuracy-offset target (upstream `ConditionTargetDistance` reads the missile target) rather than the exact aim point |
| Same with `-PwithEmi -PwithJei -PwithRei` | All **192** passed |
| `./gradlew :icbm_port:runMaterialGametest` | Passed (1/1) |
| `python3 tools/validate_resources.py <client jar>`, `tools/check_payload_visuals.py` | Passed |
| `python3 -m unittest discover -s tools/tests` | 10 passed |
| Repository `ports.py check`, `emc.py check`, `scripts/tests` (155, 3 skipped), `check_mixin_member_names.py`, `check_unmapped_overrides.py` (0 unmapped), Tekxit mixin audit | Passed; 2 injectors, 0 conflicts with the 805 in the pack |

The two radar parity tests run in their own batch (`pr5RadarParity`). In the default batch they had failed: the radar scans a full-height column of half-width 100, which covers neighbouring GameTest structures, so other fixtures' missiles changed the incoming count and nearest track. A temporary copy of the original strict assertions at the original height passed once isolated, confirming the cause; the kept tests also compare against the radar's own lists.

## Delivery and item parity (October 25, 2026)

Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run. Run after rebasing onto the gas/fragment, blast and seat/actionable-missile/defense work.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` (JDK 17) | Passed; JUnit **26** passed, including the new `ClusterBurstTest` (4 tests: ActionCluster disc geometry for horizontal and vertical headings, per-layer rotation/speed-up, bounded random spread, projectile yaw/pitch convention) |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy -PwithProjectE` | All **177** required GameTests passed, 0 failed |
| `./gradlew :icbm_port:runGametest -PwithEmi -PwithJei -PwithRei` | All **177** passed, 0 failed |
| `./gradlew :icbm_port:runMaterialGametest` | Passed (1/1) |
| `python3 tools/validate_resources.py <client jar>` | 673 JSON files, 1,899 references, 140 enabled fixed recipes, 7 optional recipes disabled standalone; `tools/check_payload_visuals.py` passed |
| `python3 -m unittest discover -s tools/tests` | 10 passed |
| Repository `ports.py check` (12 ports), `scripts/tests` (155, 3 skipped), `emc.py check`, `check_mixin_member_names.py`, `check_unmapped_overrides.py`, Tekxit mixin audit | Passed; 2 injectors, 0 conflicts with the 805 in the pack |

New `DeliveryParityGameTests` (14): grenade fuse from config and full duration; grenade throw vector and consumption; primed explosive pop and fuse; chain reaction using the payload block fuse; explosive placed beside fire primes burning; EMP removes primed explosives without detonating; defuser disarms thrown grenades and primed explosives; bomblets ignore damage and use upstream ballistics; cluster volume and heading; balloon air-kill release; handheld stack sizes and no launcher wear; ballistic cluster aims 20 blocks above the target; radar gun stores a point and configures a launcher; dungeon chests carry upstream components. Existing `DeliveryGameTests` and `ContentGameTests` assertions were updated for the two-tick grenade fuse check, unbounded launcher durability, the 200-bomblet hard cap, the 88/89 volume fill and the uniform 0–9 sulfur drop. `SmokeGameTests.smokeDeliveriesLeaveSmokeEntities` now ticks a one-tick grenade and primed charge twice, matching upstream's fuse + 1 detonation. `ballisticClusterAimsAboveTarget` expects the `ActionableMissileEntity` cluster flight, whose release uses the same `ClusterBurst` geometry as silo clusters.

These checks do not cover multiplayer presentation, client rendering of thrown/primed entities or the rocket launcher behaviours still listed as partial in [PARITY.md](PARITY.md).

## Seats, light beams, actionable missiles and defense blocks (October 25, 2026)

Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` (JDK 17) | Passed; JUnit **22** passed |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy -PwithProjectE` | All **163** required GameTests passed. New `EntityParityGameTests` (13): seat creation/removal on loaded tiered bases and none on prototype/cruise launchers, automatic seat ticker, launcher break dismounting the rider, launch moving the seat rider onto the missile, missiles never hitting their rider or seats, light-beam growth/close/reload and the timed `LightBeamEntity.spawn` API, actionable-missile action/trigger reload, run-once API actions, explosion/fire (not generic) damage setting off the payload, cluster proximity release versus impact duds, targeted launcher clusters and the `explosive_missile` API building actionable clusters. New `BlastLightBeamGameTests` (2): exothermic and endothermic blasts spawn one orange/blue `LightBeamEntity` through the mod-init `BeamEffects` listener, step it 0.1/0.2/0.5/0.8/1, close it on completion and it despawns. New `DefenseBlockParityGameTests` (6): upstream hardness/resistance and sounds for concrete, reinforced glass, spikes, glass plate/button and radioactive blocks; pickaxe requirements and self-drops; spike damage source/config minimum; item-triggered glass plate; 20-tick glass button pulse; radioactive decay chain starting on a random tick with the radioactive-block damage source. `LauncherGameTests.launcherPreservesClusterDelivery` now expects the actionable cluster entity |
| Same with `-PwithEmi -PwithJei -PwithRei` | All **163** passed |
| `./gradlew :icbm_port:runMaterialGametest` | Passed |
| `python3 mods/icbm_port/tools/validate_resources.py …/minecraft-client.jar`, `tools/check_payload_visuals.py` | Passed |
| `python3 scripts/ports.py check`, `python3 scripts/emc.py check` | Passed (no new items) |
| `python3 -m unittest discover -s scripts/tests -v`; `… -s mods/icbm_port/tools/tests -v` | 155 (3 skipped) and 10 passed |
| `python3 scripts/check_mixin_member_names.py`, `tools/tekxit4-probe/mixin_audit.py`, `scripts/check_unmapped_overrides.py` | Passed; 2 injectors, 0 conflicts with the 805 in the pack, 0 unmapped overrides |

## Blast parity (October 25, 2026)

Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` (JDK 17) | Passed; JUnit **22** passed (`PayloadConfigTest` adds the `config_version` 2→3 move of rot/mutation 80→3 and ender 30→20; version 4 is now rejected) |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy -PwithProjectE` | All **142** required GameTests passed. New: `ConventionalBlastParityGameTests` (7: 1,352 cube-surface TNT rays, condensed breaking dirt but not obsidian and hurting a pig, repulsive/attractive push directions without damage, incendiary fire and melted ice, sonic throw, stone removal and explosive priming, a seven-block breach along a stored direction, a placed breaching charge aiming into its surface across a reload), `ThermalBlastParityGameTests` (2: thermobaric sphere and lethal pulse with bedrock surviving; nuclear fallout queued and a pig mutated), `BeamBlastParityGameTests` (2: upstream heat/cold transforms; endothermic beam progress 0.1/0.2/0.5/0.8/1/0, the 100-tick wait and the completion effects), `AntiGravityParityGameTests` (2: one zero-gravity block lifted per tick, mobs rising to 0.45, gravity restored from a reloaded blast; flying-block gravity NBT, launch grace and `ceil(speed × 2)` impact then drop) and `UtilityBlastParityGameTests` (4: ender teleport to target, pull and eight endermen; placed ender GPS target through primed charge, reload and legacy saves; EMP charging a creeper and fizzling a primed charge, waiting out the defence field; rot/mutation/ender defaults and the size-3 mutation cube). Updated: the saved-terrain test uses a rot job (leaves) instead of the endothermic cube cursor, the ray-shielding test drives `ThermalBlast` with the time budget disabled |
| Same with `-PwithEmi -PwithJei -PwithRei` | All 142 passed |
| `./gradlew :icbm_port:runMaterialGametest` | Passed |
| `python3 mods/icbm_port/tools/validate_resources.py …/minecraft-client.jar`, `tools/check_payload_visuals.py` | Passed |
| `python3 scripts/ports.py check`, `python3 scripts/emc.py check` | Passed (no new items) |
| `python3 -m unittest discover -s scripts/tests -v`; `… -s mods/icbm_port/tools/tests -v` | 155 (3 skipped) and 10 passed |
| `python3 scripts/check_mixin_member_names.py`, `tools/tekxit4-probe/mixin_audit.py`, `scripts/check_unmapped_overrides.py` | Passed; 2 injectors, 0 conflicts with the 805 in the pack, 0 unmapped overrides |

## Gas, fragment and smoke parity (October 25, 2026)

Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run.

| Check | Result |
| --- | --- |
| `./scripts/gradle.sh :icbm_port:build` (JDK 17) | Passed; JUnit **21** passed |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy -PwithProjectE` | All **125** required GameTests passed. New: `GasParityGameTests` (6: upstream radius schedule and one layer per radius change, five-tick hit cadence and full-rating helmet protection, chemical toxicity above 20 hits with `icbm_port.chemical`, contagious toxicity above 10 hits plus mutation, colors never touching entities and ending on the run past its duration, cloud/hit/job save and reload), `FragmentParityGameTests` (9: 900 burning critical shrapnel, 625 owned anvil fragments, 225 explosive fragments, the 1,024 live explosive cap, 16 fragment explosions per tick with the rest deferred, `ceil(speed × damage)` hits naming fragment and owner and igniting the target, no hits in the first five airborne ticks, glass broken and stone stopping a fragment, NBT round-trip) and `SmokeGameTests` (2: one 100-tick smoke entity instead of queued work, alive mid-life, saved age, removed after its lifetime; grenade, primed block and burning cart each leave one). Updated: the queue lifetime test now uses radius-0 debilitation clouds and allows 33 ticks for the first served batch to come round again; the enclosure test uses the new cloud API with an open-air control; the old 30-arrow shrapnel test was replaced |
| Same with `-PwithEmi -PwithJei -PwithRei` | All 125 passed |
| `./gradlew :icbm_port:runMaterialGametest` | Passed |
| `python3 mods/icbm_port/tools/validate_resources.py …/minecraft-client.jar` | Passed (new `entity.icbm_port.fragment`/`smoke` names and chemical/contagious death messages are required keys) |
| `python3 mods/icbm_port/tools/check_payload_visuals.py` | Passed |
| `python3 scripts/ports.py check`, `python3 scripts/emc.py check` | Passed (no new items) |
| `python3 -m unittest discover -s scripts/tests -v`; `… -s mods/icbm_port/tools/tests -v` | 155 (3 skipped) and 10 passed |
| `python3 scripts/check_mixin_member_names.py`, `tools/tekxit4-probe/mixin_audit.py`, `scripts/check_unmapped_overrides.py` | Passed; 0 mixin conflicts, 0 unmapped overrides |

## Extreme payloads and flat explosive art (October 24, 2026)

Dedicated-server and offline checks only; no client was launched and the Tekxit pack was not run.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` | Passed; JUnit **21** passed (`PayloadConfigTest` now covers the 275/300/6,000 defaults, the 384 cap and `config_version` 1→2 migration); Tekxit/EMI test source sets compile |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy` (CI command) | All **109** required GameTests passed, including the new `ExtremePayloadGameTests`: an r=40 antimatter sphere cleared in one tick with bedrock and outside-sphere blocks intact, a red matter footprint that is a sphere (bounding-cube corners survive, interior points cleared), a pig and a survival player pulled in and killed by `icbm_port.redmatter` with the "consumed by red matter" message, creative/spectator immunity, a merged core's deferred crater still being erased once its chunk loads, and 70 legacy antimatter sweeps from an older save all becoming scars (64 at once, the other 6 kept and saved until slots free, and a full 256-job regular queue saved beside them without losing either). The Item Alchemy audit compared all 142 values |
| Same with `-PwithEmi -PwithJei -PwithRei` (CI compat run) | All 107 passed (before the merge test was added) |
| `./gradlew :icbm_port:runMaterialGametest` | Passed |
| `./gradlew :icbm_port:runGametest -PicbmBenchmark` | r=275 antimatter: 1,004 chunks and 28.0 M blocks in one tick, 375–717 ms; crater light drained by the light thread in 2.8 s; red matter reached its 300 horizon in about 1,190 ticks at a mean tick of about 7 ms (longest 51 ms). See the payload porting notes |
| `python3 tools/validate_resources.py <client jar>` | 673 JSON files, 1,894 references, 97 original PNG textures, 140 enabled fixed recipes |
| `python3 tools/check_payload_visuals.py` | Passed, including the new flat-explosive and face-uniqueness checks |
| `python3 -m unittest discover -s tools/tests` | 10 passed |
| Repository `ports.py check`, `scripts/tests` (147), `emc.py check`, `check_unmapped_overrides.py`, Tekxit mixin audit | Passed; 2 injectors, 0 conflicts with the 805 in the pack |
| Art | `tools/render_contact_sheet.py` rendered `docs/images/explosives-before.png`, `explosives-after.png` and `explosives-compare.png` offline |

## Current tree (October 23, 2026)

Dedicated-server checks only; no client was launched for this pass.

| Check | Result |
| --- | --- |
| `./gradlew :icbm_port:build` | Passed; JUnit **18** passed |
| `./gradlew :icbm_port:runGametest -PwithItemAlchemy` (CI command) | All **102** required GameTests passed. The Item Alchemy audit compared all 142 live values with no deviation. `IntegrationGameTests` passed: expected recipe and unlock presence is derived from Mekanism presence and the live uranium/dust tags, Mekanism-gated recipes are absent without Mekanism, fallbacks are loaded, and the REI entrypoint is packaged |
| Same with `-PwithRei` | All 102 passed with REI 9.2.784, Cloth Config 8.3.134 and Architectury 6.6.92 loaded on the server; every shared viewer display converted to an REI display with identical NBT-bearing inputs/outputs |
| Same with `-PwithEmi` / `-PwithJei` | All 102 passed; both viewer plugins are client entrypoints, so this proves only that the server starts with them present |
| `./gradlew :icbm_port:runMaterialGametest` | Passed |
| `python3 tools/validate_resources.py` | 672 JSON files, 2,101 references, 140 enabled fixed recipes; seven optional recipes (four material, three Mekanism) disabled in a standalone install |
| `python3 -m unittest discover -s tools/tests` | 9 passed, including both branches of the Mekanism gate and the dust gunpowder recipe staying off with Mekanism |
| `python3 scripts/emc.py check` (repository root) | Passed. Every recipe, including the alternatives Mekanism enables and every recipe re-read with Mekanism's tag contributions (priced with `mods/mekanism`'s shipped values), costs at least what its output is worth |

The REI plugin, Mekanism-present recipe loading and the new icon have not been
checked in a live client or a Mekanism server.

## Alpha.4 release validation

Verified locally October 19, 2026 with Java 17, Minecraft 1.19.2 and Fabric. The alpha.4 production JAR was also run in the full Tekxit 4 client. The alpha.3 record below remains historical.

| Alpha.4 check | Result |
| --- | --- |
| Build and test-fixture remapping | Passed; production/source artifacts and separate Tekxit/EMI fixtures generated |
| JUnit | 16 passed |
| Dedicated-server gameplay suite | All 92 required tests at that time passed, including placed redmatter reaching distant terrain, core/debris load ordering and conserved fallback, antimatter neutralization, synchronized cart countdowns and defense visual packets preserving machine data |
| Isolated materials suite | Passed, including five real datapack reloads |
| Static resources | 662 JSON files, 2,095 references and 47 original PNG textures passed validation; this checks references and image/animation metadata, not in-game appearance |
| Full Tekxit 4 alpha.4 client | All 308 official mod jars retained; ordinary client/world startup passed; six actual-pack integration tests and 285,316 EMI assertions passed before and after `/reload` |

A later pass regenerated the payload labels, six missile bodies, the exhaust strip, the redmatter core and disc, and the radioactive mark from `tools/payload_visuals.py`. `python3 tools/check_payload_visuals.py` passed. Resource validation then reported 663 JSON files, 2,095 references, and 50 original PNGs. `:icbm_port:test` and all 92 GameTests of that suite passed again. Those checks do not render the client.

Direct client inspection confirmed the growing redmatter core and rotating rings, terrain consumption and antimatter removal, radar dish motion, EMP coil animation and corrected upright launcher models. The new presentation uses ordinary Minecraft render layers, original geometry/textures and vanilla sound events. Grenades and bomblets use 3D tumbling, cargo canopies sway, and cart flashes use the server countdown. No custom shader is required. The client used software OpenGL with shaders off and a null audio driver. Shader-pack appearance, real audio playback, two-client synchronization and every visual variation remain unverified. No restricted upstream artwork was copied. See [asset provenance](ASSET_PROVENANCE.md).

## Historical alpha.3 validation

Verified October 18, 2026 with Java 17.0.20.1, Gradle 8.8, Fabric Loom 1.7.4, Minecraft 1.19.2 and Fabric Loader 0.16.14.

| Check | Result |
| --- | --- |
| `./scripts/gradle.sh build :icbm_port:runGametest` | Passed; both Mystcraft and ICBM built; remapped ICBM production/source JARs generated |
| `./scripts/gradle.sh :icbm_port:runMaterialGametest` | Passed; isolated material test completed five real server datapack reloads, provider substitution/removal, conditional recipe changes, source missing-component behavior, output/remainder checks and independent consumer recipes |
| JUnit | 16 passed: flight math, catalog contracts, cluster manifests and configuration validation |
| Dedicated-server GameTests | 88 passed: registrations/recipes/loot, priming/source/cancellation, launcher energy/ammo/network splits, flight stages/fuel/health/virtual reentry, ray shielding/reload, gas enclosure, flying-block inventory conservation, singularity persistence, exposure/fallout/infection/cleanup, carrier crafting/passengers/actions, cargo decode and returned-stack NBT isolation, radar/radio/SAM/EMP screens/extenders, addon commands/API and JEI display recipes |
| Metadata/tooling | Both ports validated; seven port-management tests and five tag graph/condition tests passed |
| Resource validator | 656 JSON files, 976 references, 142 item models/names, 47 blockstates, 10 entity names, 140 enabled fixed recipes and five activating special serializers passed; four optional recipe branches validated separately; all 90 audited OreDictionary nodes and 26 material groups checked |
| Artifact audit | Correct version and upstream MIT notice; bundled Energy API retains its MIT license; no gameplay/material test classes, fake provider items/packs or EMI/JEI runtime bundled |
| Tekxit 4 client 20.0.4 / Fabric Loader 0.18.4 | All 308 official mod jars retained; six actual-pack integration GameTests passed before and after `/reload`; native EMI 1.1.24 index checks passed 285,316 assertions on each run; [pack diagnostics and dedicated-harness limits](TEKXIT.md) recorded |
| Whitespace | `git diff --check` passed |
| Prior alpha.1 client without JEI | Successful startup and saved creative-world load under Xvfb/software OpenGL; launcher, radar and EMP screens inspected at default 854×480 window / GUI scale 2; radar range/channel and launcher target/channel changes survived close/reopen |
| Prior alpha.1 client with JEI 11.8.1.1034 | Successful startup and creative-world creation; `icbm_port:payload_crafting` plugin registered; item models, normal recipes and dynamic held-item loading recipes displayed in JEI |

The historical client observations below are retained from alpha.1. Alpha.3 added native EMI support, cargo NBT isolation, valid item defaults for recipe displays and the separate Tekxit client and integrated-server checks documented in [TEKXIT.md](TEKXIT.md).

The prior client check found oversized machine layouts and indistinguishable inventory slots. Defense and launcher screens now fit a 240-pixel scaled viewport with visible slot outlines. Defense controls vary by machine; launcher coordinates and fuel slots are labeled. The final layout was rechecked in a fresh client, and the build and all 87 server tests passed again.

Client logs contained development-account profile-key/authentication failures and vanilla shader warnings. The first run lacked an audio device; the second used OpenAL's null driver. Audio output was not verified. No mod missing-model/texture errors or mod runtime exceptions were observed in these sessions.

The GameTest tasks recreate only their disposable `build/gametest/world` and `build/materialtest/world`. Material tests use a separate mod/source set so live reloads cannot race the gameplay suite; none of those fixtures ship. Resource regeneration was also checked for deterministic output. Tests explicitly serialize/restore state for persistence checks. The client used a disposable development creative world, saved and reopened between runs; no regular dedicated-server EULA was accepted.

These checks establish the tested implementation contracts, not exact upstream parity. Two-client networking, prolonged real-world flight/automation, every model/animation, sound, all third-party integrations and complete survival balancing still require broader playtesting. The resource audit is convention-specific; runtime recipe/loot and JEI display tests provide additional coverage. The source comparison and deliberate differences are maintained in [PARITY.md](PARITY.md).

The material suite uses deliberate stand-in items and real Fabric tag/resource reloads. Published Tech Reborn 5.4.0 and Modern Industrialization 1.6.19 JAR tag data was inspected for naming evidence. Modern Industrialization remains an artifact-only audit. Actual Tech Reborn and other Tekxit providers are covered by the separate [Tekxit checks](TEKXIT.md); that does not establish every third-party system. The new material tests and static source-mapping audit run in GitHub CI.
