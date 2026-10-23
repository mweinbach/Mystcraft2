# Validation — 0.2.0-alpha.4

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
