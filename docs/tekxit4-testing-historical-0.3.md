# Tekxit 4 integration testing

This record covers the current Mystcraft 0.3 development candidate in the official pack. It establishes the checks actually observed below, not exhaustive classic parity or general modpack compatibility. The [historical 0.2 record](tekxit4-testing-historical-0.2.md) preserves earlier artifacts, failures and results.

## Exact distribution and runtime

- Official server: [20.0.0Tekxit4Server.zip](https://tekxit.lol/downloads/tekxit4/20.0.0Tekxit4Server.zip), inspected 2026-10-18.
- Archive SHA-256: `23434145235ab5b36f1d5597f01241dcfc2f7f65d60ede1e1357e2592c24dcf6`.
- Minecraft 1.19.2, bundled Fabric Loader 0.18.4 and Fabric API 0.77.0+1.19.2. This pack's loader differs from the port's standalone 0.16.14 target.
- The downloaded distribution resolves 438 baseline mods; adding Mystcraft and its separate optional smoke helper resolves **440 mods**. This is the official pack, not an approximate reconstructed list.
- Isolated Java 17 server under ignored `work/tekxit4/`, 4 GiB heap, localhost Minecraft port 25567, offline mode, seed 12345, view/simulation distance 3. EULA acceptance was explicitly authorized by the user. The historical record documents baseline third-party warnings and the pack's independent ancillary listener.

## Candidate 13 evidence

Production JAR: `mystcraft-fabric-1.19.2-0.3.0-dev.jar`.

SHA-256: `df3aa0afcd2d671a7379a007747fb4e805f0e1442ac0bf56ed91ef0ebc56121f`.

Final optional helper SHA-256: `5ed49fecd58e221af87819bb80c8e6bc254d0ce4a29bbb165e4dd779fd938355`.

Final evidence uses `work/tekxit4/v03-candidate13-bclib-*` logs and matching artifact manifests. Evidence-log timestamps are UTC.

## Compatibility fixes and fixture diagnosis

- Composable MixinExtras ink wrappers preserve existing fluid queries before adding ink membership. Earlier direct redirects conflicted with Expandability/Spectrum at startup; the corrected implementation coexists with them.
- MCA replaces vanilla villagers through a deferred queue. The village fixture now waits forty ticks and verifies the actual converted archivist profession and page/booster trades. A one-use career initializer restores source career permanence after conversion without continuously overriding later profession changes.
- Staaaaaaaaaaaack can merge provisional dropped items before a link commits. Transactional drops use the native never-pickup delay until successful transfer, then restore normal pickup behavior.
- Dynamic Age spawn data no longer creates a vanilla permanently loaded spawn region. Runtime checks distinguish full chunks from generation dependencies.
- Skylands removes pending/live block-entity data for intentionally filtered blocks while retaining exact data on surviving structure blocks.

Earlier candidate11 full attempts stopped after 104 passing groups at a synthetic combat-player assertion. Origins intentionally makes unchosen players invulnerable; the fixture now completes actual installed origin selection. A second cause was confirmed in transformed player bytecode: IndRev records damage attempts before Mystcraft's anti-PvP veto and suppresses repeated equal damage within ten ticks. The helper now advances21 genuine native player ticks between attempts, retaining mandatory environmental and enabled-PvP positive controls plus melee/projectile rejection checks. No damage bypass or production combat change was used. The corrected combat assertions then passed, revealing a separate synthetic respawn setup issue: XaeroLib expects per-player data created at login. The helper now calls Xaero’s public login initializer before native cloning, retaining the real respawn checks. Exact versions, hashes and method offsets are in `work/tekxit4/v03-damage-fixture-diagnosis.md`. Failed logs remain preserved and are not counted as successful suites.

BCLib forces native generation-region writes to radius one even when a larger constructor radius is requested. This truncated the deferred-crystal fixture’s radius-two reference geometry. The helper now permits the explicitly bounded synthetic reference region while leaving the actual radius-one region fully subject to pack hooks; complete geometry/RNG equality, boundary crossing, no forced chunks and persistence checks remain mandatory. See `work/tekxit4/v03-bclib-fixture-diagnosis.md`.

## Separate earlier diagnostics

Candidate 9's `work/tekxit4/v03-candidate9-runtime.log` records 100/100 empty and 100/100 simple grammar inputs accepted: **200/200** across those two bounded sets. Its static-symbol audit accepted **145/145** catalog terminals in **580** deterministic completion/allocation witnesses, with 126 queue targets consumed and frozen-state roundtrips. These are earlier diagnostic results, not a claim that every symbol/environment combination was exercised by candidate11.

## Final acceptance

The final full suite emitted **MYSTCRAFT_SMOKE_COMPLETE 135** at21:11:38, with 135 pass lines and no failure marker. The run exercised 204 registered biomes (141 modded) and 67 source-fluid choices, real MCA archivist conversion/trades, native linking and respawn, generation and source huge-tree geometry, classic instability, deferred crystals, decay and fissure return.

Every dimension saved at21:12:02; the Distant Horizons IO worker stopped at21:12:03. The known lingering process received SIGTERM only afterward. A fresh JVM reached `Done (1.605s)` at21:13:04. At21:13:22, `mystcraft_age_verify` passed both canonical Age identity/seed/pages/chunk/time/weather restoration and deferred-crystal pending geometry persistence without loading destination chunks. All dimensions saved again at21:13:28, and the IO worker stopped at21:13:29. The known lingering process received SIGTERM only after these saves. Exact restart log: `v03-candidate13-bclib-restart.log`.

No graphical client running the complete Tekxit pack was tested. Standalone Forge/Fabric graphical evidence is scoped in the port's validation record. Dedicated-server checks do not prove every claim/portal/progression interaction, shader compatibility, Distant Horizons distant generation, or long-term survival/soak behavior. The known pack shutdown thread can linger after every dimension and the Distant Horizons IO worker finish; any subsequent termination is recorded explicitly in the exact run evidence.
