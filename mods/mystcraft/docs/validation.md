# Validation — classic Mystcraft 0.4.0-dev

This candidate implements the remaining core source-derived book, lectern, page-browser, archivist and specialized-surface work on Forge and Fabric. It remains a development build for disposable-world server testing. Older accepted artifacts and their evidence are preserved in [historical-validation-0.3.md](historical-validation-0.3.md).

The later port-specific [Agewriter's Guide](in-game-guide.md) has its own artifact
hashes and focused build, GameTest and graphical evidence. The full-port results
below retain their original artifact scope; they do not claim that those entire
gameplay suites were rerun for the guide addition.

## Artifacts and static checks

| Artifact | SHA-256 |
| --- | --- |
| Forge production | `1ef309e789008a3cceceb3f6a12eef717408e7be679125ca5ab3f7cda59c6f62` |
| Fabric production | `fa372eea04436fc440d3de5728aeedf1d3063ca881de1fff9884686b47129151` |
| Forge optional helper | `d5e105f44c8e5eb508a7be5ddf2f33f07bbbb5cf117b1cb0cbcfb991d035511e` |
| Fabric optional helper | `2f47fc07a13326b08bd4d6c4b27f294f8a321063babf534da6be2977fc69a203` |

Both Java17 builds passed **83 unit tests** with no failures, errors or skips. The packaged loader check passed **458 identical shared resources**, metadata, loader isolation and production mixin refmaps. All **52 original imported assets** match their manifest hashes. Production JARs contain no smoke helpers and share no Java packages with their corresponding optional helpers. Repository metadata and all five tooling tests passed.

Independent specialized-surface oracles matched1260 Mesa/Bryce/wooded columns (322560 blocks) and16641 swamp noise samples; these are numerical comparisons to the documented source reference, not a 1.12 versus1.19 whole-world claim. New generated Ages use terrain version4; frozen versions0–3 retain their previous generation path.

## Dedicated servers

| Environment | Full gameplay suite | New focused checks | Fresh JVM persistence |
| --- | --- | --- | --- |
| Forge43.5.0 | **150 passed** | **25 passed** | Age identity/seed/pages/chunk/time/weather and deferred crystals passed;47 Ages restored |
| Fabric0.16.14/API0.77.0+1.19.2 | **145 passed** | **25 passed**, including forced rain | Both persistence checks passed;65 Ages restored |
| Official Tekxit4 server20.0.0 (440 mods) | **145 passed** | **25 passed** | Both persistence checks passed;37 Ages restored |

The five-check difference between Forge (150) and Fabric (145) is not a Fabric failure: Fabric runs every shared check, and Forge runs five more from three Forge-only helpers in `src/smokeForge`. `ForgeTravelVetoSmoke` (1 check) cancels a real `EntityTravelToDimensionEvent` during a linking-book jump and verifies the held book, inventory, drops and platform are rolled back. `ForgeEntityVetoSmoke` (3 checks) cancels item and mob travel and a destination `EntityJoinLevelEvent`, and verifies the source entity, stack, motion and equipment survive without duplicates. `ForgeBlackDecayVetoSmoke` (1 check) cancels the black-decay falling entity's `EntityJoinLevelEvent` and verifies the original chest and its items stay intact. Fabric API 0.77 has no cancellable dimension-travel or entity-join event, so on Fabric a protection or claim mod cannot veto a Mystcraft link or decay spawn through an event and there is nothing for these checks to exercise. The gap is recorded in [PARITY.md](../PARITY.md#forge-only-veto-checks).

The production hashes are identical throughout the final runtime and graphical checks. Forge full150 and initial restart checks used helper `ff13307714c513c43bb1df885fb89d28bc802134e40b7dfb8a362b76bc2ffee2`. Fabric full145 and graphical checks used helper `f70d1fd173d1a8c510ed26044535159d6a9fc9f8d251f32757ca9bda4e9c39bb`; its next restart/focused checks used `a9d4528e552f4d61802b60d1bf32ba0e510b418cec4f01b2fc9df7dcbb8c6165` after a helper-only native-base call fix for Forge remapping. The shipped helpers add the proven modded-loot fixture correction described below. Separate `v04-final-helper-validation` logs and hashes record focused checks. Plain Fabric used helper `bd393ad39a117c0600e1221fc3d96ec0688443ea598e3bebfdefb05e6db61295`; the shipped helper then added a Forge decoder callback while preserving Fabric's default decoder behavior. The final Tekxit run uses the shipped Fabric helper. Earlier full runs are not relabeled as runs of later helpers.

New focused coverage includes book gravity, submerged air/drowning/save state, moving death delay, native lava timing, stationary-hopper exclusion and hopper-cart exact-stack transfer; two-viewer lectern inventory and reach checks; actual treasure weights/counts/luck; archivist stock clock/caps/currency/full-inventory rollback/menu transactions; final four-tab viewport21..24; native chunk v4 surfaces and frozenv3 contrast.

## Graphical evidence

Final Fabric client checks exercised actual archivist interaction, scaled symbol captions, purchases, native lectern cover-slot pickup, live replacement refresh and physical-book cursor recovery. Original map canvas and exact-map recovery, scrolling through all145 folder pages and exact last-page extraction were also exercised on the preceding candidate, whose relevant implementation is unchanged. New sky rendering executed with a sunset fan and distinct below-horizon/NoHorizon output. The detailed record names each screenshot and artifact scope in evidence `client-test/v04-results.md`.

Final Forge graphical checks exercised actual archivist interaction and purchases, map rendering and exact-map recovery, original lectern item rendering, full folder scrolling and exact last-page extraction, native linking-book cover pickup, and above/below-horizon sky rendering. Both clients exited normally through Quit Game. Clients use immutable snapshots of built development classes/resources against the packaged dedicated servers. These are real graphical clients, but not production-launcher or full-Tekxit client tests.

## Corrections found during verification

- A drowning fixture reused an entity whose prior rain damage retained its hurt window. It now uses a fresh entity; the unchanged strong assertions pass with rain forced.
- Isolated loot-pool decoding in the Forge helper initially omitted Forge's required native loot-load context. Its adapter now uses `ForgeHooks.loadLootTable`; Fabric keeps its native decoder. Production code was unchanged.
- A redundant restart-verification command on plain Fabric rejected its already-consumed one-shot deferred-crystal fixture. The successful prior fresh-JVM result remains the persistence evidence; this later command is retained in the helper-validation log and is not claimed as another pass.
- A synthetic ProtoChunk had filled biome palettes without advancing to BIOMES status. The fixture now follows native staging before surface reads.
- Forge remapped inherited native methods on the production custom classes, while direct helper calls retained development names. Native-base calls corrected the helper; gameplay code was unchanged.
- Tekxit adds Simply Swords and Amethyst Imbuement loot pools to the library table. A diagnostic seed produced the correct eight Mystcraft rolls plus one sword; the initial fixture incorrectly bounded the entire modded table to eight outputs. The helper now separates source-pool assertions from whole-table compatibility checks; production loot and other mods remain unchanged.
- Graphical shop labels initially truncated the generic “Symbol Page” prefix. The final production UI uses the original full localized symbol caption scaled to fit.

## Limits

These checks do not establish every classic symbol combination, pixel-identical rendering against a running1.12 client, long survival/soak sessions, simultaneous networked human clients, shader/Distant Horizons rendering, addon APIs or legacy save conversion. Modern biome/registry/rendering adapters and the intentionally retained source behavior are documented in [classic-parity.md](classic-parity.md). No Mystcraft2 stability design is included.
