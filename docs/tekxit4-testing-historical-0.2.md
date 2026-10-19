# Tekxit 4 integration testing

This is a compatibility test of the current classic Mystcraft development port, not evidence of full classic Mystcraft parity. The pack and test worlds live in ignored `work/tekxit4/`.

## Exact pack

Official sources inspected on 2026-10-18:

- https://tekxit.lol/ lists Tekxit 4 as Fabric Minecraft 1.19.2, server 20.0.0, and client 20.0.4 (client hotfix).
- Server archive: https://tekxit.lol/downloads/tekxit4/20.0.0Tekxit4Server.zip
- SHA-256: `23434145235ab5b36f1d5597f01241dcfc2f7f65d60ede1e1357e2592c24dcf6`.
- Bundled Fabric Loader: **0.18.4**. Fabric API: **0.77.0+1.19.2**.
- Archive inventory: **264 top-level Fabric manifests**; loader baseline resolves **438 mods**, including nested modules. Exact manifest versions and dependencies are captured in `work/tekxit4/mod-inventory.json`.

The upstream pack distribution is used as downloaded, rather than reconstructed from an approximate mod list. Java 17 is required by its README. The supplied launcher is `Fab_Serv_LaunchWithBatOrSh.jar`; its launcher properties select `MC_Serv_DoNOTLaunchWithThis.jar`.

## Runtime isolation

Initial baseline: 27 GiB disk available, 16 GiB system memory available, cgroup memory limit 16 GiB. Baseline runs used one test server at a time, Java 17, `-Xmx4G -Xms512M`. Later parallel build/client/server sessions encountered host pressure and watchdog exits, recorded below; their successful validation logs and subsequent focused reruns are distinguished. The user explicitly accepted the Minecraft EULA before these tests.

Local settings: `server-ip=127.0.0.1`, `server-port=25567`, `online-mode=false`, seed `12345`, view/simulation distance `3`, spawn protection `0`. Voice chat is bound to localhost. The bundled Seedlight Riftways mod independently opens TCP 27999 on all interfaces even though its saved server IP is localhost; the Minecraft game port remains localhost. This ancillary listener was observed in the unmodified baseline too. Other pack settings are retained, including BCLib's `level-type=bclib:normal` and ServerCore dynamic view-distance handling; the latter can override vanilla distance settings.

## Relevant coexistence surfaces

These are areas requiring explicit testing, not established conflicts:

| Pack component | Exact manifest version | Mystcraft integration surface |
| --- | --- | --- |
| `dimdoors` | `5.0.4` | Pocket dimensions, player transfer, portal interactions |
| `ad_astra` | `1.12.7` | Additional planets and dimension travel |
| `bclib` | `2.1.8` | World preset and biome/registry lifecycle |
| `betterend` | `2.1.7` | Modified End biomes and structures |
| `betternether` | `7.1.5` | Modified Nether biomes and structures |
| `customportalapi` | `0.0.1-beta63-1.19.X` | Portal traversal and dimension transfer |
| `twilightforest` | `4.2.3-CHKNFork` | Custom dimension, progression gates and portals |
| `the_bumblezone` | `6.9.13+1.19.2` | Custom dimension and entry/exit behavior |
| `spectrum` | `1.7.12` | Progression-gated dimension and teleport behavior |
| `terrestria` | `5.0.13` | Biome registration and world generation |
| `traverse` | `6.0.8` | Biome registration and world generation |
| `waystones` | `11.4.2` | Alternative teleportation and player state |
| `seedlight_riftways` | `0.0.1` | Pack-specific rift travel |
| `lithium` | `0.11.1` | Entity, inventory and world optimizations |
| `servercore` | `1.3.6+1.19.2` | Dynamic distances and ticking behavior |
| `starlight` | `1.1.1+fabric.ae22326` | Lighting and chunk lifecycle |
| `kubejs` | `1902.6.2-build.73` | Recipe/script modification |
| `polymorph` | `0.46.6+1.19.2` | Overlapping recipe selection |

| `minecells` | `1.5.8` | Progression dimension and player transfer |
| `deeperdarker` | `1.2.5` | Otherside dimension entry and portal rules |
| `openpartiesandclaims` | `0.30.0` | Claims, interaction permissions and teleport policy |
| `pehkui` | `3.8.3+1.14.4-1.21` | Scaled player bounds and destination safety |

Open Parties and Claims, Pehkui, item stack mods, and grave/death mods are also present. Successful startup does not establish claim bypass policy, dimension progression compatibility, scaled-player destination safety, or grave behavior. Mystcraft currently lacks a Fabric travel-veto API equivalent to Forge's travel event; cross-mod policy must be tested explicitly.

## Evidence

Baseline reached `Done (38.473s)` with Minecraft listening on `127.0.0.1:25567`. `save-all flush` and `stop` saved all dimensions. A lingering pack thread prevented automatic JVM exit after server shutdown; SIGTERM was sent only after all dimensions were saved. The baseline log is `work/tekxit4/baseline.log`.

The baseline already reports empty CorgiLib/Enhanced Celestials registries, third-party missing refmaps, client-class lookups, outdated structure files, and a third-party `class_2358.canBurn` lookup failure. These messages precede Mystcraft installation and are not attributed to Mystcraft.

Mystcraft results are recorded below as the integration stage completes. No full-pack graphical client test has been performed by this task.

### First Mystcraft integration run

Adding only the production Mystcraft JAR and its separate test harness resolves **440 mods**, reaches `Done (2.481s)`, and loads the existing pack world. Artifact hashes are in `work/tekxit4/added-artifacts.sha256`.

The first explicit `execute in minecraft:overworld run mystcraft_smoke` failed at the water-bottle ink recipe remainder assertion: `Water bottle recipe left an extra container`. The test had already verified that the matched recipe produced a Mystcraft ink vial. This assertion passed in plain Fabric and therefore reveals a pack-sensitive recipe behavior. The inherited default remainder behavior needs an explicit classic-compatible override; the precise third-party mixin responsible has not been established. Log: `work/tekxit4/mystcraft-integration-failure.log`.

This run establishes startup coexistence, not passing gameplay compatibility. Subsequent test results must supersede this failure only after retesting the rebuilt artifact in the pack.

### Expanded integration rerun

The next rebuilt production artifact includes the explicit ink-recipe remainder fix and the binder, writing desk, crystal portal, and Age foundation. Exact hashes are in `work/tekxit4/added-artifacts-expanded.sha256`. Tekxit reached `Done (1.946s)`.

The expanded smoke command progressed through the recipe/mixer, binder, and writing-desk stages, then failed in linking: `held link must leave exactly one interactive source book`. Before this assertion, the test confirmed successful cross-dimension transfer, exact destination/orientation, and an empty source hand. The expected `LinkBookEntity` lookup near the source did not return exactly one book. A later console count was zero; because test cleanup and subsequent ticking had already occurred, that later count does not establish the initial count or root cause. The test now requires diagnostic refinement before claiming pack linking compatibility.

Log: `work/tekxit4/expanded-smoke-failure.log`. The stage order and a thread snapshot (`smoke-progress-threads.txt`) confirm earlier recipe/mixer, binder, and writing-desk assertions returned successfully, but no final all-tests-passed marker was emitted. Portal and Age tests were not reached; Age restart verification was therefore not attempted. No complete Tekxit graphical-client run has been performed.

### Source visibility diagnosis and passing run

The prior source-book assertion was a test-fixture visibility issue: the harness uses synthetic players without normal player chunk tickets. On the **same production and harness JARs**, the Overworld had no forced chunks. Adding the source test area's nine chunks (`forceload add 16 16 48 48`), waiting for real chunk/entity ticking, and rerunning the command produced **`MYSTCRAFT_SMOKE_COMPLETE 37`**. This experiment changes no gameplay source and does not weaken the source-book assertion. The complete pass is logged in `work/tekxit4/expanded-smoke-pass-forced.log`, with artifact hashes alongside it.

All 37 groups passed, covering ink recipe container accounting, mixer input/output conservation and NBT, binder/page handling, writing-desk copying, linking/session safety, crystal portal construction/invalidation, and actual independently seeded Age worlds with isolated time/weather and saved chunks.

The server was then saved/stopped and restarted in a genuinely new JVM. `execute in minecraft:overworld run mystcraft_age_verify` emitted **`MYSTCRAFT_AGE_RESTART_PASS`**, verifying canonical Age identity, seed and pages restored before login, a retained chunk marker, and independent time/weather. Log: `work/tekxit4/age-restart-pass.log`. Exactly the nine manually added forced chunks were then removed and all dimensions saved. The general harness is being updated to establish its own real ticking source fixture; a final rebuilt-artifact run remains desirable.

This is a passing dedicated-server integration test against the specific hashed artifact and pack. It does not prove every possible Tekxit dimension/claim/progression interaction, full classic symbol parity, or full-pack graphical-client rendering.

### Frozen 0.2.0 artifact retest

Production Fabric SHA-256 `6ae335b304e87ec955831f3a78e24bfb6cdc9ec99e31b2a0fe177f538624574d` was installed as the sole Mystcraft production JAR, replacing the old 0.1.0 file. The separately remapped harness SHA-256 is `20e73986cd2929dd4029e059f06c90414b5b39d5b252dbf760ab85d317d6425e`. Exact installed hashes: `work/tekxit4/0.2-artifacts.sha256`.

Tekxit reached `Done (1.790s)`. The current asynchronous smoke preparation established its real ticking fixtures without manual forced chunks. Recipe/mixer, binder, writing desk, collections, ink effects, and modifier-editor groups emitted pass lines. The full run then failed in `LinkTravelModifierSmoke` at `Disarm travel must succeed`; no `MYSTCRAFT_SMOKE_COMPLETE` marker was emitted. Log: `work/tekxit4/0.2-smoke-disarm-failure.log`. This artifact does not inherit a full passing claim from the earlier 37-group artifact; its new failure requires diagnosis and retesting before final compatibility status.

The independent `mystcraft_smoke_entities` subset then completed all **four** groups against the same frozen 0.2 production JAR: actual item collision transfer with exact stack/NBT/momentum, mob transfer retaining UUID/type/name/equipment, unchanged rejection of Disarm item travelers, and exactly-once source ejection of guaranteed mob equipment with cleared destination equipment. Log: `work/tekxit4/0.2-entities-pass.log`. This isolates the unresolved full-run Disarm failure to its player test path; it does not replace the missing full completion marker.

### Intermediate diagnostic artifact

Production SHA-256 `6e0830355a3b480cc01fb338dfece321850067a0f3d537fa318416ec71635805` is an intermediate diagnostic build, not a release candidate. It includes the decay update-flag fix and additional rejected-travel logging. With harness `5497e4e9b396ca7eaa40e1d71c69e362dbcedf6a17c12c88dc49f23a2709eb3c`, startup reached `Done (1.963s)`. The full run stopped earlier at the collection extraction fixture: its selected index remained 2 after replacing the collection with one page. This requires a harness selection reset and did not reach Disarm. Log: `work/tekxit4/intermediate-collection-failure.log`.

The separate worlds subset returned from terrain and decay assertions, then failed while preparing a new seed-zero void Age with a star fissure: `Fissure would destroy a block entity` (`StarFissureGenerator.java:69`). The Age itself loaded before this protective rejection. No worlds completion marker was emitted. Log: `work/tekxit4/intermediate-worlds-fissure-failure.log`. The block entity's identity and whether another pack world-generation feature placed it remain under investigation.

The travel-only harness (`be2589b678d04e5f360c3dee17d8c06780df37f206938e0413dd738460cd9e39`) was then installed without changing production, after a saved shutdown and new JVM startup (`Done (4.182s)`). `mystcraft_smoke_travel` failed with the decisive diagnostic: `Linking provisional drop insertion rejected: dimension=minecraft:overworld, type=minecraft:item, removed=true, item=minecraft:air, count=0, provisional=3`. Player travel was rejected with `cannot_link`; the original inventory and player location remained unchanged. This identifies the insertion of a provisional dropped item as the rejected step, before native dimension travel. A pack insertion hook consuming or merging the entity is consistent with this result; the responsible hook has not yet been identified. Log: `work/tekxit4/intermediate-travel-drop-rejection.log`.

### Candidate: complete 90-group server pass

Production SHA-256 `26dc2c74536ff27e993da7cd18b1236f9c9a4e92ec73ea9c4a8d28dc1d8a7da6` and harness `98c7af1a4733bfa0a5e09af4e0347e6862b4abdb41114d7b3a08658380de97b7` were verified before installation; installed hashes are retained in `work/tekxit4/candidate-final-artifacts.sha256`. A new JVM reached `Done (3.784s)`. No manual forced chunks were added.

The targeted travel subset emitted `MYSTCRAFT_SUBSET_COMPLETE mystcraft_smoke_travel 5`, including both Disarm variants with exact source totals and no duplicate book. The pack's Staaaaaaaaaaaack item-creation hook immediately merges eligible incoming item entities; transactional drops now use vanilla's never-pickup delay during the provisional stage, which that hook excludes from merging, and restore their intended delay after successful travel. The pack configuration was unchanged. Evidence: `work/tekxit4/candidate-travel-pass.log`.

The full command then emitted **`MYSTCRAFT_SMOKE_COMPLETE 90`** at 16:55:10 UTC. It covers recipes and inventory accounting, writing and portable collections, modifiers, physical book health, entity/player portal transfer, exploration injection into all 204 registered biomes, loot and merchant purchases, bounded visual packet validation, real Age creation, terrain versions, loaded-edge decay, and fissure placement/return. The fissure collision test preserves a foreign chest and its exact inventory using a bounded alternate site. Evidence: `work/tekxit4/candidate-full-pass.log`. The saved shutdown is retained separately in `candidate-full-pass-saved.log`.

These are dedicated-server assertions against the exact candidate and pack, not evidence of graphical celestial rendering, every third-party portal/progression interaction, or complete classic parity. A true JVM restart verification follows separately.

The same candidate was saved with `save-all flush`, stopped, and started in a **new JVM** without replacing either JAR. After `Done (4.050s)`, `mystcraft_age_verify` emitted **`MYSTCRAFT_AGE_RESTART_PASS`** at 16:58:04 UTC: canonical identity, seed and pages restored before login, retained chunk marker, and independent time/weather. Evidence: `work/tekxit4/candidate-age-restart-pass.log`. The complete 90-group run plus this restart check establishes the tested server behavior for the hashed candidate.

### Catalog-final build and host-pressure recovery

After the candidate's successful restart proof, a later host-wide RAM-pressure episode stalled the server and even shell reads. A queued `stop` was not acknowledged before that process exited; its log has no saved-shutdown marker. No agent sent a termination signal to that gameplay process. This later unclean exit is recorded in `work/tekxit4/candidate-post-pass-host-pressure.log`; it does not replace the separately captured earlier restart result.

The catalog-final build, production SHA-256 `57eb91b643f00f577785f96c5bac243a52dcbe2951fdce2787b87cdd0d95e940` with unchanged harness `98c7af1a4733bfa0a5e09af4e0347e6862b4abdb41114d7b3a08658380de97b7`, loaded the existing world successfully (`Done (3.857s)`) and emitted **`MYSTCRAFT_SMOKE_COMPLETE 90`** at 17:16:43 UTC. All dimensions were then saved before shutdown. A genuinely new JVM reached `Done (4.825s)` and emitted **`MYSTCRAFT_AGE_RESTART_PASS`** at 17:18:58 UTC. `save-all flush` and `stop` saved all dimensions again at 17:19:04; the known lingering process received SIGTERM only afterward.

Evidence requested for this build is retained as `work/tekxit4/final-classic-runtime.log`, `final-classic-restart.log`, and `final-classic-artifacts.sha256`. A subsequent Fabric disconnect-thread cleanup fix requires a separate targeted build verification; these exact-hash results do not imply that later build has been tested yet.

### Final callback-cleanup artifact

The final Fabric production SHA-256 is `ab83205dd6f557f5f8c5ebc2a83d086dd924c7cbc3152eddfa7ea943d6875c4a`; the harness remains `98c7af1a4733bfa0a5e09af4e0347e6862b4abdb41114d7b3a08658380de97b7`. The only subsequent change schedules Fabric disconnect cleanup on the owning server/client threads. Installed hashes are in `work/tekxit4/callback-artifacts.sha256`.

A new JVM loaded the saved Tekxit world and Ages, reached `Done (2.826s)`, and emitted **`MYSTCRAFT_AGE_RESTART_PASS`** at 17:22:30 UTC. `save-all flush` and `stop` saved every dimension at 17:22:35. The lingering process received SIGTERM afterward; the server is stopped. Evidence: `work/tekxit4/final-classic-callback-restart.log`.

This exact final artifact has startup/restoration evidence. The full 90-group gameplay run remains attributed to the preceding `57eb91…` artifact; the full suite was not repeated for the callback-only change. No graphical-client disconnect test was performed in Tekxit by this task.
