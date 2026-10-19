# Focused 0.4 graphical fixtures

These fixtures are built and registered in the optional smoke mod alongside
`WritingGuiFixture`. Actual Forge and Fabric graphical checks and their artifact
scope are recorded in [validation.md](validation.md) and the packaged evidence
`client-test/v04-results.md`. Production artifacts contain neither command. All actions require operator level4 and an
explicit player argument: `mystcraft_parity_gui ACTION PLAYER`.

Use the disposable graphical world. Commands create only a small platform at
x18..24,y99,z-4..2, a lectern at20,100,0, or one named fixture villager nearby.
They do not clear player inventory: held content is moved to an empty main slot
and full inventories reject. Repeated archivist seeding replaces only tagged
fixture villagers. Existing `work/client-test/env.sh`, `send-chat.sh`, and snapshot
launch scripts retain their earlier roles. New `parity-fixture-command.sh` only
prints a validated console command; it does not launch or send anything.

| Action | Real graphical check |
| --- | --- |
| `folder` | Opens an ordered folder containing all145 static pages with unique `GuiOrder` payloads. Capture initial rows, scroll to final row, hover last page, then return to top. Drag/reorder one page and check no duplicate/disappearing stock. |
| `archivist` | Adds native adult archivist at21.5,100,.5 with careerXP1, gives32emeralds+2blocks. Right-click normally to exercise the real interaction hook and custom shop screen. Capture3rankcards, booster stock, prices/currency. Buy once with pointer and verify displayed currency/stock decreases. In MCA packs allow native conversion ticks before clicking. |
| `lectern_page` | Displays a SunNormal page at 20,100,0. Capture the placed glyph page, then right-click to verify the one-slot display inventory and exact page recovery. Source has no enlarged loose-page GUI here. |
| `lectern_map` | Creates genuine saved filled-map data with deterministic 16-pixel colored squares. Capture the full map canvas on the placed lectern, then right-click to verify the one-slot display inventory. Recover/reinsert the same map and confirm the world-rendered pattern persists. Source has no enlarged map GUI here. |
| `display` | Opens the actual lectern content menu directly, useful for framing after manual interaction proof. |
| `skybook` / `skybook_nohorizon` | Delivers unallocated panel-first descriptive book, fixedseed22345, flatplains, no weather, cyan sky, single north-yaw24k/noon sun, dark moon/stars, explicitdummyfeatures. The second adds NoHorizon only. Activate each panel to create its Age. |
| `sunset` / `above` | Only while already inside an Age: sets that Age time6000 and camera y=horizon+8,yaw90,pitch0. Capture additive orange fan over cyan sky, preserving same camera/FOV across variants. |
| `below` | Same Age-only time6000; camera y=horizon-8,yaw90,pitch35. Capture black side enclosure and lowercap for normal horizon; corresponding NoHorizon Age must omit both. |

The sky clock modifies only the player's current Age through ServerLevel; vanilla
`/time set` affects every server level and is unsuitable for isolated comparisons.
The camera command may place the player inside flat terrain: use spectator mode
for sky-only captures, then restore survival for shop/page interaction. Take the
same below-horizon camera comparison for `skybook_nohorizon`, and an ordinary
Overworld return capture to verify no leaked GL state. Source reference:
`SymbolSunNormal`, `SymbolMoonNormal`, `SunsetRenderer`, `SkyRendererMyst` at pinned
commit9bc8ddc061845df0cd5ea47f4fdef773cff786ae.

For repeat runs, record artifact hashes, loader, dimensions, fixture command,
time, camera/FOV, resolution and screenshot paths. Capture promptly after setting
time: the sky clock continues advancing, so delayed captures differ in brightness. A passing dedicated
smoke suite does not establish graphical execution or pixel parity.
