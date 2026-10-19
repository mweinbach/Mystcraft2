# Current expansion validation

The expanded development implementation has passed **37 runtime scenario groups on plain Fabric and on the actual Tekxit 4 server 20.0.0 pack**. The scenarios cover crafting, mixer, binder, writing desk, linking, crystal portals and custom Ages. A fresh plain-Fabric server process restored the saved Age identities, seeds, pages, marker block and independent weather/time. The Tekxit source-book test initially queried an inactive fake-player chunk; the exact same JAR passed after the test region was force-loaded. The fixture is being corrected to wait for real server ticks and restore its loading leases.

Further source changes (including modifier transactions, exploration acquisition, portable collections and expanded graphics) are in progress. The 37-group result does **not** validate changes made afterward. The Forge expansion and new graphical interactions still require the current-artifact rerun. See `work/fabric-server-smoke/expanded-runtime.log`, `expanded-restart.log` and `docs/tekxit4-testing.md` for the recorded expansion evidence.

The following section is retained as historical evidence for the earlier linking-only build, not the current feature inventory.

---

# Validation record — 2026-10-18

Environment: Eclipse Temurin Java 17.0.20.1, Minecraft 1.19.2, Gradle 8.8.
Forge 43.5.0; Fabric Loader 0.16.14, Fabric API 0.77.0+1.19.2, Loom 1.6.12.
Six GPT-6.1 Sol subagents handled reference review, linking core, book UX/network,
items/resources, bookstand/loader adapters and ink mixer; the parent integrated
and verified their changes.

| Check | Result | What it establishes |
| --- | --- | --- |
| `python3 scripts/ports.py check` | Passed | Port metadata and status are valid. |
| Port-management tests | 5 passed | Existing workspace tooling remains valid. |
| `:mystcraft:build` | Passed | Forge compiles, reobfuscates its JAR and passes 4 destination NBT tests. |
| `-p mods/mystcraft/fabric build` | Passed | Fabric compiles, remaps its JAR/refmap and passes the same 4 NBT tests. |
| NBT tests on both loaders | 8 total executions passed | Exact coordinates/orientation roundtrip; missing fields, malformed dimension IDs, unknown schema, non-finite and out-of-range data rejected. |
| `scripts/check_loader_parity.py` inside this mod | Passed | 31 gameplay resource files byte-identical; mod IDs/versions match; no other-loader bytecode references/descriptors; production mixin mapping present. |
| Resource JSON/local references | Passed | 19 shared JSON files parse; referenced local model textures and literal translations resolve. |
| Asset provenance | Passed | All 12 imported PNG/OGG files are byte-identical to the pinned classic source; see `asset-provenance.md`. |
| Forge `:mystcraft:runData` after loader split | Passed | Headless Forge loads common registrations and Forge adapters. There are no data providers; this does not test recipe execution or screen rendering. |
| Standalone Forge with packaged JAR | Passed boot and restart | Created a real world and preserved occupied bookstand, mixer inventory and custom book entity across a clean restart. |
| Standalone Fabric with packaged JAR | Passed boot and restart | Created a real world; preserved destination/custom metadata, mixer inventory/ink and custom book entity across a clean restart; production mixin loaded. |
| `git diff --check` | Passed | No whitespace errors in tracked changes. |
| Forge packaged runtime smoke helper | 15 assertion groups passed | Loaded recipes, mixer stock/NBT, two-player menu transfers, binding, real dimension travel, source entity/stand retention, invalid destinations and session rejection; real Forge travel cancellation exactly once with rollback. |
| Fabric packaged runtime smoke helper | 14 assertion groups passed | The same shared runtime scenarios on the Fabric production server. |
| Forge graphical client against packaged server | Passed tested workflow | Original book/mixer screens render; E closes book; shift-click3 vials plus3 paper yields3 panels/bottles; mouse activation links to exact Nether destination; offhand returns Overworld; Q-drop yields one custom entity, zero ordinary items, unchanged NBT. |
| Fabric graphical client against packaged server | Passed tested workflow | Same original screens, mixer output extraction, E-to-close, mainhand Nether travel and offhand Overworld return; Q-drop produces exactly one custom entity and no ordinary duplicate. |

Production test artifacts (smoke helper is a separate, optional JAR):

```text
287a32a60fbfd490d676da881521582ce74f3a8c3f5ccfb0d79a72a02d576a46  mystcraft-forge-1.19.2-0.1.0-dev.jar
a1e2c71c480beb06f693f919de16bbdd9f06e8039861cb01f9d445bef7fe20a2  mystcraft-fabric-1.19.2-0.1.0-dev.jar
```

The review caught and removed duplicate Forge travel-event invocation.
Destination decoding resolves registry keys only at the runtime boundary, so
saved-data tests need no running game. CI now builds both variants and checks
artifact resource equality, loader isolation and Fabric's production refmap.

Fresh-server Forge configuration writes initially emitted a transient NightConfig
`maxThreads` duplicate-key warning. The settled configuration contains one key;
no Mystcraft startup exception was observed in the final standalone Forge run. Fabric logs
`No data fixer registered for mystcraft:link_book` at ERROR level while registering
the custom entity. It is not a startup exception; legacy book-entity schema/save
migration is not implemented. Standard Forge library metadata/URL warnings and
Gradle deprecations also appear.

The user explicitly accepted the Minecraft EULA and requested that it be applied.
Both isolated test servers now have `eula=true`, generated worlds, and passed
save/restart checks. They bind only to loopback (Forge 25565, Fabric 25566), with
offline authentication for local test clients. No production server was modified.
Matching resources/NBT do not prove cross-loader world migration or enable Fabric
clients to join Forge servers (or vice versa).

This remains a linking prototype. Full classic parity is not established:
generated Ages, descriptive books, writing, portals, link modifiers and the other
systems in `classic-parity.md` remain absent. New Mystcraft2 stability is excluded.

## Runtime evidence and limits

The runtime helpers execute inside actual dedicated servers using loaded recipes,
real block entities/menus, and real `ServerPlayer` objects. Menu tests use two
player inventories; linking tests use silent packet connections and capture the
book-session transport while exercising the real server validation and teleport
APIs. They do not substitute for two networked clients or packet/render testing.
The temporary transport, players, entities, listener and pad blocks are restored
on completion. Run only in disposable worlds: tests can generate chunks.

Evidence retained under `work/`:

- `server-smoke/runtime-smoke.log`: `MYSTCRAFT_SMOKE_COMPLETE 15`.
- `fabric-server-smoke/runtime-smoke.log`: `MYSTCRAFT_SMOKE_COMPLETE 14`.
- `server-smoke/world-restart-persistence.log`: four persisted fixture markers.
- `client-test/results.md`, screenshots, Forge `graphical-interaction-server.log`
  and Fabric `graphical-client-server.log`: actual graphical client interactions and server-side counts.
- `fabric-server-smoke/world-restart.log` and `persistence-result.txt`: persisted
  bookstand, mixer and custom-book fixtures.

Initial helper builds exposed inherited-method mapping failures in the separate
test JARs. Their remapping classpath and explicit vanilla method owners were fixed
before the successful runs. These were harness failures, not passing tests.
Forge's actual travel veto test verifies both a single event and transaction
rollback, rather than inferring success from the absence of a crash.

Still required: survival block break drops, two simultaneous networked clients,
longer survival/soak sessions, target-modpack
permission integrations, and the unimplemented classic systems. The original
linking audio is packaged and invoked, but the headless test host has no audio
device for audible verification.

Graphical runs use matching development clients against packaged dedicated-server
JARs, under Xvfb/Mesa llvmpipe. The Forge client was closed by destroying its X
window after the successful tests; the resulting `BadDrawable`/nonzero runClient
exit is an automation shutdown error, not a gameplay crash. Offline-profile key
fetches, missing audio device and Forge version-check network warnings also appear.

Fabric exited through Disconnect and Quit Game; its runClient task completed
successfully.
Both dedicated servers then stopped cleanly with exit code 0 and all dimensions
saved. Their isolated world folders remain available for subsequent testing.
