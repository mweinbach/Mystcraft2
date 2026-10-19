# Dedicated-server testing

This is a development port of classic Mystcraft for Minecraft 1.19.2, with separate Forge and Fabric artifacts. Use a disposable world and install the matching production JAR on the server and every client. The exact validated artifacts and results are recorded in [validation.md](validation.md).

## Install

1. Use Java 17 and Minecraft 1.19.2.
2. Forge baseline: Forge 43.5.0 with `mystcraft-forge-1.19.2-0.4.0-dev.jar`.
3. Fabric baseline: Loader 0.16.14, Fabric API 0.77.0+1.19.2 and `mystcraft-fabric-1.19.2-0.4.0-dev.jar`. Tekxit 4 server 20.0.0 supplies Loader 0.18.4 and that API version; use the pack's loader.
4. Put only the matching loader's production JAR in `mods/`. Do not install sources, development-classifier JARs or both loader variants.
5. Accept Minecraft's EULA before starting a new server. The user explicitly accepted it and authorized applying it to this session's disposable servers; their local `eula.txt` files contain `eula=true`. Build and packaging scripts do not accept it automatically.
6. Preserve logs, exact JAR checksums and the world backup when reporting issues. Start with the baseline loader, then repeat against the target pack.

Shared content does not establish cross-loader multiplayer or world conversion. Existing 1.12 world migration is not implemented.

## Optional automated runtime checks

The test helper is a separate server-only mod that creates and changes fixtures in the disposable world. It is excluded from production artifacts.

```sh
./scripts/gradle.sh -PenableSmokeTests :mystcraft:build :mystcraft:smokeJar
./scripts/gradle.sh -p mods/mystcraft/fabric -PenableSmokeTests build remapSmokeJar
python3 mods/mystcraft/scripts/check_loader_parity.py
```

Install the matching helper from `build/smoke-libs/` or `fabric/build/smoke-libs/`: `mystcraft-smoke-forge-1.19.2-1.0.0.jar` or `mystcraft-smoke-fabric-1.19.2-1.0.0.jar`. Do not install Fabric's `-dev.jar`.

Run `mystcraft_smoke` at the server console. A level-4 operator may use `/mystcraft_smoke`. Preparation is asynchronous: `MYSTCRAFT_SMOKE_PREPARING` is followed by real ticking-chunk readiness, then individual `MYSTCRAFT_SMOKE_PASS` lines. Only `MYSTCRAFT_SMOKE_COMPLETE <count>` means the full suite passed. `MYSTCRAFT_SMOKE_FAILURE` or a stack trace means it did not. The helper releases its own transient chunk tickets; no manual force-loading is needed.

The optional `mystcraft_smoke_parity04` checks the new book lifecycle, lecterns, archivist economy, tab scrolling and specialized surfaces. `mystcraft_smoke_worlds`, `mystcraft_smoke_entities`, `mystcraft_smoke_classic` and `mystcraft_smoke_diagnose` commands run subsets. Run `mystcraft_grammar_audit` and `mystcraft_symbol_audit` separately for the completion-seed and all-static-symbol audits; these deliberately run outside the full gameplay suite to keep each server task bounded. Their subset-complete markers are not full-suite completion. After a successful Age fixture, save and stop the server, start a **new JVM**, then run `mystcraft_age_verify` to check persistent identity, seed, ordered pages, modified chunks, time, weather and the persisted deferred crystal queue without loading its target chunk. A reload within the same process is insufficient.

Checks cover native ink fluid chemistry, dispensers and loader tank transactions; village archivist house placement; recipes/container returns; paid mixer/binder/desk outputs, collection ordering and bounds, real menu actions, linking flags and rollback, native player/item/mob travel, Age persistence, generator invariants, visuals serialization, acquisition/trading, portals/fissures, permission and respawn policy, classic instability, source generation features and decay boundaries. Fixture players use silent connections and direct server APIs. These checks do not prove graphical rendering or simultaneous networked client interaction.

## Real client acceptance

Run each applicable workflow on both loaders, then repeat on the actual target modpack. Preserve client and server logs and screenshots.

- Craft ink with two separate black dyes and a water bottle, or the bucket/glass-bottle variant. Verify container returns. Mix panels with/without additives; result extraction must charge ink and paper exactly once.
- Create and bind a linking book. Opening the book must not teleport. Activate its panel from held main/offhand, stand, lectern and physical book sources. Check normal same-dimension rejection and Intra Linking behavior.
- Recover a physical book by sneaking with an empty hand; verify no automatic pickup, source duplication or lost destination/health NBT. Check fire/water damage and durable-item display.
- Bind a titled descriptive book with ordered glyph pages, including duplicates and a linking panel. Check original artwork, authors, title/page/back-cover navigation, scaling, focus, E/Escape and mouse hit tests.
- Enter its generated Age and return using a prepared return book. Repeat after reconnect and complete server restart. The Age identity, seed and changes must persist. Unsupported pages must report an error instead of silently doing nothing.
- Use both halves/backboards of the desk, spend paper and 50 mB per copy, and preserve the source page. Browse/search/sort pages. Reorder a folder and verify exact payload order after closing/reopening; check offhand containers and full inventories.
- Activate a crystal portal using a receptacle book. Test invalid/broken frames, distant unloaded frame chunks, items/mobs and every supported modifier. Rejected travel must preserve source items and inventory.
- Find a library, recover lectern pages, trade with an archivist and open booster packs. Check that offered pages have supported uses.
- Inspect terrain, weather, sky, fog, water and lighting in explicit custom Ages. Confirm ordinary Overworld/Nether/End visuals remain unchanged, including after disconnecting and joining another server.
- Connect two real clients at once. Have both use the same paid output, book/stand and portal; verify aggregate item counts and independent sessions. Reconnect each client in an Age.
- Run a survival progression/soak session with the target pack, including permission mods, server restart, entity transfer and new chunk generation. Dedicated-server smoke alone does not establish this acceptance.

## Evidence and limitations

See [classic-parity.md](classic-parity.md) for supported scope and remaining work, [validation.md](validation.md) for exact evidence, and [Tekxit testing](../../../docs/tekxit4-testing.md) for pack-specific findings. The [historical plan](historical-linking-test-plan.md) records the original linking-only stage and is not current feature status.

## Dynamic Age chunk loading

An Age's spawn position persists independently, but setting it does not install vanilla's `START` region ticket. Vanilla `ServerLevel.setDefaultSpawnPos` adds a radius-11 ticket: a 23×23 region of 529 full chunks, with up to 47×47 (2,209) chunk holders involved in generation dependencies. Applying that behavior to every dynamic Age caused substantial background generation and amplified cold fissure preparation into a watchdog failure under concurrent server load.

`AgeServerLevel` now updates its saved spawn directly, removes known old/new spawn `START` tickets, and sends the native spawn-position packet only to players in that Age. Ages load chunks requested by readers, generation, or explicit tickets; their spawn alone does not keep a vanilla spawn region loaded. Ordinary Overworld spawn behavior is unchanged. Cold requested chunks can still take time to generate. Keep the watchdog enabled and avoid interpreting a reduced chunk footprint as a completed performance or soak test. The Age runtime smoke checks that an unoccupied prepared Age does not retain the 529-chunk spawn region.

## Linking-book crafting configuration

`config/mystcraft-crafting.json` exposes the original source option:

```json
{
  "crafting.linkbook.enabled": true
}
```

Set it to `false` and restart to disable the leather-plus-linking-panel recipe. Existing books, binding, linking, and operator recovery commands remain available. Unknown keys and nonboolean values stop initialization with a readable config error. The recipe serializer stays registered; a client with a different local setting may show a preview, but server validation rejects disabled crafting. Operator travel, recovery, permission commands, and the separate UUID/home/respawn configuration are documented in [classic-linking-rules.md](classic-linking-rules.md).
