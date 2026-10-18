# Validation — 0.2.0-alpha.2

Verified October 18, 2026 with Java 17.0.20.1, Gradle 8.8, Fabric Loom 1.6.12, Minecraft 1.19.2 and Fabric Loader 0.16.14.

| Check | Result |
| --- | --- |
| `./scripts/gradle.sh build :icbm_port:runGametest` | Passed; both Mystcraft and ICBM built; remapped ICBM production/source JARs generated |
| `./scripts/gradle.sh :icbm_port:runMaterialGametest` | Passed; isolated material test completed five real server datapack reloads, provider substitution/removal, conditional recipe changes, source missing-component behavior, output/remainder checks and independent consumer recipes |
| JUnit | 16 passed: flight math, catalog contracts, cluster manifests and configuration validation |
| Dedicated-server GameTests | 87 passed: registrations/recipes/loot, priming/source/cancellation, launcher energy/ammo/network splits, flight stages/fuel/health/virtual reentry, ray shielding/reload, gas enclosure, flying-block inventory conservation, singularity persistence, exposure/fallout/infection/cleanup, carrier crafting/passengers/actions, radar/radio/SAM/EMP screens/extenders, addon commands/API and JEI display recipes |
| Metadata/tooling | Both ports validated; seven port-management tests and five tag graph/condition tests passed |
| Resource validator | 656 JSON files, 976 references, 142 item models/names, 47 blockstates, 10 entity names, 140 enabled fixed recipes and five activating special serializers passed; four optional recipe branches validated separately; all 90 audited OreDictionary nodes and 26 material groups checked |
| Artifact audit | Correct version and upstream MIT notice; bundled Energy API retains its MIT license; no gameplay/material test classes, fake provider items/packs or JEI runtime bundled |
| Whitespace | `git diff --check` passed |
| Prior alpha.1 client without JEI | Successful startup and saved creative-world load under Xvfb/software OpenGL; launcher, radar and EMP screens inspected at default 854×480 window / GUI scale 2; radar range/channel and launcher target/channel changes survived close/reopen |
| Prior alpha.1 client with JEI 11.8.1.1034 | Successful startup and creative-world creation; `icbm_port:payload_crafting` plugin registered; item models, normal recipes and dynamic held-item loading recipes displayed in JEI |

Client observations below are retained from alpha.1; alpha.2 changes material data, one plain component item and validation tooling. No new client smoke run is claimed.

The prior client check found oversized machine layouts and indistinguishable inventory slots. Defense and launcher screens now fit a 240-pixel scaled viewport with visible slot outlines. Defense controls vary by machine; launcher coordinates and fuel slots are labeled. The final layout was rechecked in a fresh client, and the build and all 87 server tests passed again.

Client logs contained development-account profile-key/authentication failures and vanilla shader warnings. The first run lacked an audio device; the second used OpenAL's null driver. Audio output was not verified. No mod missing-model/texture errors or mod runtime exceptions were observed in these sessions.

The GameTest tasks recreate only their disposable `build/gametest/world` and `build/materialtest/world`. Material tests use a separate mod/source set so live reloads cannot race the gameplay suite; none of those fixtures ship. Resource regeneration was also checked for deterministic output. Tests explicitly serialize/restore state for persistence checks. The client used a disposable development creative world, saved and reopened between runs; no regular dedicated-server EULA was accepted.

These checks establish the tested implementation contracts, not exact upstream parity. Two-client networking, prolonged real-world flight/automation, every model/animation, sound, all third-party integrations and complete survival balancing still require broader playtesting. The resource audit is convention-specific; runtime recipe/loot and JEI display tests provide additional coverage. The source comparison and deliberate differences are maintained in [PARITY.md](PARITY.md).

The material suite uses deliberate stand-in items and real Fabric tag/resource reloads. Published TechReborn 5.4.0 and Modern Industrialization 1.6.19 JAR tag data was inspected for naming evidence, but those complete mods were not run together. This verifies the shared-material contract, not all of their systems. The new material tests and static source-mapping audit run in GitHub CI.
