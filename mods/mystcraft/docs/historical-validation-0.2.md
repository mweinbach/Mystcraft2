# Historical 0.2 validation (superseded)

The following records the earlier artifacts only. Current results are in validation.md.

# Validation — classic development candidate, 2026-10-18

Six GPT-6.1 Sol subagents reviewed the pinned official Mystcraft 1.12 source and implemented shared gameplay with separate Forge/Fabric adapters. This is version 0.2.0-dev for Minecraft 1.19.2, not a completed classic port. See `classic-parity.md` for the feature matrix and explicit gaps. Historical results are retained in `historical-validation.md`.

## Build and artifact checks

Java 17, Gradle 8.8; Forge 43.5.0; plain Fabric Loader 0.16.14 and Fabric API 0.77.0+1.19.2. The actual Tekxit 4 server 20.0.0 uses Loader 0.18.4 and 440 mods.

- Both production builds passed, with 40 unit tests per loader: zero failures, errors or skips.
- 431 shared gameplay resources are byte-identical. Loader isolation, metadata and both production mixin mappings passed.
- Port metadata checks passed. All 41 imported classic assets have recorded byte-identical SHA-256 provenance; generated glyphs are documented separately.
- The 145-definition catalog exposes 62 supported definitions. Parameter pages still need a valid consumer; unsupported grammar is rejected.
- Source differential tests documented in `classic-terrain.md` verify the adapted noise/density math; this does not establish complete biome/surface/feature parity.

Final production artifacts:

```text
12ce4b3ee314b0c70655c460a77d2ad6519bf530e2527b98e19653753bc39312  mystcraft-forge-1.19.2-0.2.0-dev.jar
ab83205dd6f557f5f8c5ebc2a83d086dd924c7cbc3152eddfa7ea943d6875c4a  mystcraft-fabric-1.19.2-0.2.0-dev.jar
```

## Dedicated-server evidence

Final Forge artifact: **94 scenario groups passed**, followed by a true new-JVM restart that restored 18 Ages before login and verified identity, seed, ordered pages, marker chunk, time and weather. Final shutdown saved every dimension and exited 0. See `server-smoke/final-classic-*` in the evidence archive. Earlier gameplay-identical Fabric candidate `26dc2c74536ff27e993da7cd18b1236f9c9a4e92ec73ea9c4a8d28dc1d8a7da6` passed all 90 scenario groups and a true new-JVM Age restart on plain Fabric and Tekxit. The subsequent `57eb91b643f00f577785f96c5bac243a52dcbe2951fdce2787b87cdd0d95e940` Fabric candidate enabled fourteen implemented celestial controls and passed all 90 groups again on plain Fabric and Tekxit. A later minimal Fabric-only patch marshals disconnect cleanup onto the server thread; its focused validation is recorded below. Forge candidate `fa917d051b1bbb02c4611868684aeeccffd134607f82fad68f196e776281eee8` passed 93 groups and restart; the final Forge rerun additionally covers the subsequent fissure alternate-site and transactional drop fixes.

Final Fabric artifact `ab83205…` passed startup and canonical Age persistence verification on both plain Fabric and Tekxit after the disconnect-thread fix. Plain Fabric restored 19 existing Ages; its final graphical checks then created Age20. Tekxit's full 90-group suite and true JVM restart belong to `57eb91…`; final `ab83205…` received the focused startup/restoration check. All Tekxit dimensions saved before its known lingering process was terminated; no Tekxit graphical client was used. See the distinct callback restart logs/checksums. After the final GUI checks, plain Fabric saved all 20 Ages and exited 0; its client also exited 0. All test servers are stopped.

Tests exercise real recipes, menus and server APIs: paid mixer/binder/desk outputs, pages and collection ordering, malformed/stale network requests, every supported link property, transactional native entity transfers, physical-book attacks/health, persisted Ages and generator settings, terrain controls, celestial serialization/daylight/sleep, acquisition/trades, crystal portals, fissures and decay boundaries. Forge also checks native event vetoes. Fixture players are not graphical clients.

The user accepted the Minecraft EULA; `eula=true` was applied to all three disposable servers. Tests bind to loopback with local offline test identities. No production server was deployed. Cross-loader multiplayer, cross-loader world conversion and 1.12 save migration are not established.

A host-wide stall during concurrent builds, three servers and graphical clients triggered watchdog exits after the earlier successful candidate tests. Crash logs do not establish the root cause. Inspection confirmed that Fabric disconnect callbacks could mutate book-session and pending-visual HashMaps on a Netty thread; both now enqueue cleanup on the server thread. Client disconnect likewise queues presentation-map cleanup on the client thread; GPU deletion already used a render-thread queue. This is a confirmed race fix, not a proven explanation for the stall. Subsequent runs use fewer concurrent JVMs.

## Graphical and multiplayer evidence

Real graphical clients ran shared-code snapshots against packaged dedicated-server JARs. These checks complement production-JAR mapping/runtime validation; they are not a claim that the client JAR was launched through a retail launcher.

Forge candidate: original book artwork, authors, panel activation and physical-book attack without disconnect; custom two-sun positions, moon/phase, twinkle layers, End-sky rendering, stationary sun under a changed world time, sunset color and an actual return to normal Overworld sky. Evidence names begin `client-test/forge-candidate1-`. The source snapshot predates the final non-rendering fixes and lower-horizon-disk adjustment; that last adjustment lacks a dedicated graphical capture. The sharp cyan-sky/blue-fog boundary remains a documented modern rendering approximation.

Fabric candidate: two simultaneously connected real clients opened the same mixer. One vial and one paper produced exactly one linking panel for MystTester and none for MystGuest; authoritative server inventory/NBT confirmed zero ink/paper and one returned bottle. This tests concurrent open menus and sequential extraction attempts, not a deliberately synchronized packet race. See `fabric-server-smoke/candidate2-two-client-mixer-proof.log` and primary/guest screenshots.

Final Fabric snapshot `fabric-candidate3` against production `ab83205…`: folder text search and alphabetical display sorting worked; dragging changed the server's stored order while retaining all three pages. Actual book activation entered cyan-sky Age20 with a stationary noon sun. A server kick while a return book was open produced the intended disconnect, without an associated client/server exception. Reconnect restored the same Age visuals; a new return-book session linked back to the rainy night Overworld with no cyan leakage. These are observed workflow checks, not proof of every possible concurrency interleaving. See `client-test/classic-results.md` and final callback GUI logs in the evidence archive.

Earlier graphical checks covered original mixer/book screens, main/offhand round trips, source book drops, binder, desk, folder and descriptive-Age entry. Their historical scope is preserved separately. Headless audio output does not prove audible sound playback. Actual Tekxit graphical-client rendering, Iris/shaders and a long survival soak remain unverified. Distant Horizons reports an unknown custom generator; distant generation compatibility is not established.

## Limits

The full weighted grammar, some biome/material/cave/feature controls, full classic desk workspace and dynamic linking preview remain incomplete. Automatic classic instability profiling/decks/effects and black decay are unported; no Mystcraft2 stability design was added. See `classic-parity.md`, `book-ui-parity.md`, `classic-terrain.md`, and `celestial-implementation.md`. Use the candidate for disposable-world testing and preserve exact hashes/logs when reporting failures.
