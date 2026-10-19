# Validation — classic Mystcraft 0.3.0-dev, 2026-10-18

Six requested GPT-6.1 Sol subagents reviewed and implemented against official Mystcraft-Legacy revision `9bc8ddc061845df0cd5ea47f4fdef773cff786ae` (1.12.2, 0.13.7.06). This is a Java 17 development port for Minecraft 1.19.2 on Forge and Fabric. It includes classic instability, not Mystcraft2 stability. Core source coverage and modern engine boundaries are documented in `classic-parity.md`; coverage is not a claim of every possible symbol combination, addon API or legacy-save compatibility.

## Packaged artifacts and build checks

```text
dfd713ca0ec5cddc79c229ec526b9bfdb718cd16a4ce0faa3e64db180fcb8b82  mystcraft-forge-1.19.2-0.3.0-dev.jar
df3aa0afcd2d671a7379a007747fb4e805f0e1442ac0bf56ed91ef0ebc56121f  mystcraft-fabric-1.19.2-0.3.0-dev.jar
```

- Both builds pass 76 unit tests, with zero failures, errors or skips. Java 17 / Gradle 8.8; Forge 43.5.0; plain Fabric Loader 0.16.14 with API 0.77.0+1.19.2.
- All 455 shared gameplay resources match byte-for-byte. Loader isolation, version metadata, required common/client mixins and production refmaps pass. Port metadata validation passes.
- All 49 unique imported classic assets match the pinned upstream bytes and recorded SHA256. Derived glyphs and modern adapters have separate provenance. Fabric bundles MIT-licensed MixinExtras 0.4.1 for composable ink hooks.
- Packaged optional server-only helpers: Forge `b437b91fba27a1b5d48f0f1a0a3b6b9444dd99a683b265f64eee58021cb5ed10`; Fabric `5ed49fecd58e221af87819bb80c8e6bc254d0ce4a29bbb165e4dd779fd938355`. The final helper adds genuine player ticks between damage assertions for IndRev compatibility, initializes Xaero data through its real login initializer before synthetic respawn tests, and isolates the larger crystal reference region from BCLib’s fixed write radius. The actual bounded region retains all pack hooks. Standalone full/restart runs used the preceding helpers `cdfb77b15dd704bff3001057bd8bf340ea63199b5532c7b9a29fd9d6385c1a8b` / `299f17a6ba05b713a1ca51f85afa2f9bbad0191b3123bf59dccc3fa54031a2e6`; production JARs did not change. Both final helpers build successfully and have no package overlap with production.

## Dedicated-server acceptance

Evidence-log timestamps are UTC. Final artifact runs are recorded in `server-smoke/v03-candidate13-*`, `fabric-server-smoke/v03-candidate13-*` and `tekxit4/v03-candidate13-*` in the evidence archive. Forge passed **140** groups and a clean new-JVM restart restored 42 Ages, then passed both Age and deferred-crystal persistence checks. Plain Fabric passed **135** groups, then a clean new JVM restored 58 Ages and passed both persistence checks. Both standalone servers saved every dimension and exited 0. Actual Tekxit passed **135** groups with the final helper. A fresh Tekxit JVM passed both canonical Age and deferred-crystal persistence checks, then saved every dimension and stopped its IO worker. The known pack thread lingered afterward and received SIGTERM only after those saves; this is distinguished from the standalone exit0 results.

The suites exercise real recipes and native container returns; ink bucket/dispenser/chemistry/tank transactions; paid mixer, binder and desk operations; collection and stale-session checks; physical books and modifiers; native player/item/mob travel and rollback; persistent Ages; permissions/PvP/respawn; frozen grammar/generation/environment plans; source features and huge trees; acquisition and archivist houses/trades; portals/fissures; classic profiling, decks and effects; crystal write persistence. Forge additionally verifies native event veto rollback. Full tests now yield between scenario helpers and retain their own fixture chunk leases until completion.

The independent static-symbol audit passed all 145 source-valid targets across 580 seeded completion/allocation witnesses on both loaders and actual Tekxit. It also checks 126 consumed modifier/controller/feature targets, all 145 deterministic repeats, frozen NBT roundtrips, independent RNG streams, input preservation and no canonical Age allocation. The grammar audit accepted 100 empty and 100 simple authored seeds on each environment. These audits validate valid modifier consumers; they are not proof that an arbitrary malformed page sequence is meaningful. Their exact prior candidate hashes/logs remain recorded; later changes concerned spawn loading, archivist career initialization, tooltip presentation and cleanup of filtered Skylands block entities, not the audited grammar/compiler.

Actual Tekxit 4 server 20.0.0 uses Fabric Loader 0.18.4 and 440 mods. Its prior candidate 9 passed startup and both independent audits, while its synchronous village check exposed MCA replacement timing. The final pack run exercised all 204 registered biomes (141 modded) and 67 source-fluid choices. See the separate current pack record in `tekxit4-testing.md`.

## Defects found and fixed during acceptance

- Removed ink from the global water tag: it no longer makes obsidian beside lava or irrigates farmland. Local entity, boat and camera hooks retain swimming, drowning, buoyancy and dark fog. Composable wrappers resolve the actual Spectrum/Expandability conflict found during Tekxit startup.
- Restored original village marker placement and source treasures. MCA replaces villagers through a tick queue; the fixture now waits for conversion and verifies profession and trades. A one-time career initializer survives conversion and prevents native XP-zero job-site reset from erasing the source archivist career.
- Tekxit combat fixtures complete Origins selection and separate real damage attempts by 21 native player ticks. IndRev records attempts before the anti-PvP veto and suppresses repeated equal damage within ten ticks; immediate synthetic hits were invalid positive controls. Environmental and enabled-PvP positive assertions remain mandatory. Xaero synthetic respawn fixtures also initialize the actual player data normally supplied at login; native clone assertions remain intact.
- Dynamic Ages no longer install vanilla START spawn tickets: a single Age had retained 529 full chunks and 2209 generation dependencies. Spawn data remains persistent, and only players in the Age receive its spawn packet. Cold chunk generation can still consume time; this is not a general performance/soak guarantee.
- Skylands now removes live and packed block-entity data when intentionally filtering generated blocks to air. A native ProtoChunk regression checks removed DUMMY/live entries and exact preservation of surviving structure NBT.
- Deferred crystal geometry is persisted before chunk-save completion and replayed only in already loaded full chunks. Synthetic geometry fixtures now use a private lighting engine.
- Restored embedded desk book navigation/linking and paid first-blank writing before allocation, source sounds/particles and portal tint. Corrected Forge helper native-method mappings and moved expensive test audits across ticks to avoid aggregate watchdog stalls.

Earlier failures and watchdog reports are retained as failure evidence. They are not silently counted as passes.

## Graphical evidence

Real graphical Java clients ran immutable development-classpath snapshots against packaged server JARs. They are not retail-launcher installations. `client-test/v03-results.md` identifies snapshots, screenshots, authoritative transaction markers and artifact scope. The completed Fabric candidate 11 graphical run is recorded there; later candidate 13 changes only Skylands generation cleanup and the optional test helper.

Forge candidate 7 graphical checks passed original desk/portfolio/book layouts, exact paid copying, rename, blank no-overwrite, grouped transfers, dynamic biome/fluid glyphs in GUI/hand/world, resource reload and actual embedded-desk linking into a new Age while retaining one canonical desk book. Explicit environment screenshots show magenta grass, blue foliage and the source rainbow arc. Its rendering code is unchanged except later tooltip cleanup; the later server fixes have separate final runtime evidence. A lightning fixture spawned, but an unambiguous bolt frame was not captured. Headless audio cannot prove audible sound playback.

Fabric candidate 11 repeated the actual paid desk/rename/blank/portfolio flows, dynamic book glyphs and embedded-desk linking. Its final composable ink hooks passed actual player air loss, swimming to the surface with clearing fog, native pig drowning and stable boat buoyancy. The client disconnected normally and all 53 Ages saved; normal Quit Game was not confirmed after a tool transport reset. Earlier 0.2 simultaneous-client mixer and reconnect checks are historical; they are not new 0.3 multiplayer proof.

## Operational scope

The user explicitly accepted the Minecraft EULA; all three disposable servers used `eula=true`. They bind to loopback with local offline test identities. No production server was deployed. Install exactly the matching loader JAR on both server and clients.

Use this build for disposable-world server testing. Actual Tekxit graphical rendering, shaders/Iris, Distant Horizons distant generation, long survival progression/soak, arbitrary mod inventories, 1.12 save conversion and cross-loader multiplayer/world conversion remain unverified or outside this port. Optional LookingGlass cameras and old addon binary APIs are separate integrations. Historical 0.2 results are retained in `historical-validation-0.2.md`; they do not validate later artifacts automatically.
