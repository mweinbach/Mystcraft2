# Historical linking prototype test plan

Archived from the initial linking-only build. Some feature statements and filenames are obsolete; use [server-testing.md](server-testing.md) for the current plan.

# Dedicated-server test plan

The development build includes linking between existing dimensions and generated
Mystcraft Ages, plus writing, binding and crystal portals. Use a fresh disposable world and the same JAR on every client.
Do not migrate a 1.12 world or install this into a production save.

## Install

1. Use Java 17 and Minecraft 1.19.2. Choose Forge 43.5.0 or Fabric Loader 0.16.14.
2. For Forge, install `mystcraft-forge-1.19.2-0.1.0-dev.jar` on server and clients.
   For Fabric, install `mystcraft-fabric-1.19.2-0.1.0-dev.jar` plus Fabric API
   0.77.0+1.19.2 on server and clients. Use the same loader throughout the test.
   Do not install both variants or the `-sources.jar` as mods.
3. Accept the Minecraft EULA before starting a new test server. In this session
   the user explicitly stated “i agree to EULA do it for me,” authorizing acceptance
   in both disposable standalone server worlds. That explicit instruction superseded
   the repository default against accepting it on the user's behalf. The two test
   worlds have been accepted; build/setup tasks do not accept it automatically.
4. Start with no other gameplay mods. After baseline checks, repeat with the
   intended server's mod list and permission/claim mods.
5. Keep server and both clients' `logs/latest.log`. Record the JAR SHA-256, Java,
   Forge version, mod list and the test world backup when reporting a problem.

From this source checkout, use `./scripts/gradle.sh :mystcraft:runServer` and
`./scripts/gradle.sh :mystcraft:runClient`; these use separate ignored `run/`
folders under `mods/mystcraft`. For Fabric, add `-p mods/mystcraft/fabric` and
use `runServer` / `runClient`; run folders are `run/fabric-server` and
`run/fabric-client`. The graphical client needs a display.

Run every gameplay check separately on both loaders. Shared code gives matching
intended behavior; loader adapters still require independent runtime checks.
Do not infer cross-loader save compatibility or cross-loader multiplayer from
matching registry IDs and NBT.

## Historical linking-only runtime evidence

Both standalone loaders have booted successfully in their accepted disposable worlds.
A save/stop/restart round trip preserved an occupied bookstand and its book destination,
ink mixer input/output inventory and loaded ink state, and the custom book entity.
These checks establish boot and persistence for the observed fixtures, not completed
client interaction or multiplayer travel validation. Final results and evidence belong
in [validation.md](validation.md).

The opt-in runtime command completed successfully on both loaders:
`MYSTCRAFT_SMOKE_COMPLETE 15` on Forge and `MYSTCRAFT_SMOKE_COMPLETE 14` on Fabric.
These are scenario-group counts, not counts of every assertion. The shared checks exercise:

- Loaded recipes: both water-based ink-vial variants and returned bucket, two-dye
  validation, panel/leather recipe with nested NBT copying, and tagged stand ingredients.
- Mixer processing: three paid batches, no idle duplication, blocked bottle/panel
  outputs, preserved inputs, one held paid ink charge and NBT inventory/ink round trip.
- Menus: two harness viewers extracting the same real output, insertion rejection,
  full-inventory stock preservation and distance invalidation.
- Binding and travel: preserved metadata, exact cross-dimension position/orientation,
  one source book left behind, rejected same-dimension/malformed/missing/solid/liquid
  targets, interactive entity travel, no automatic pickup and explicit sneak pickup.
- Bookstands and sessions: stored-book travel without consumption, empty-hand recovery
  directly into that hand, identical-stack substitution rejection, source-movement
  rejection, another player's token rejection and spent entity-token replay rejection.

Forge additionally passed a real `EntityTravelToDimensionEvent` cancellation check:
exactly one event, unchanged source player and original book/NBT, and no leftover
provisional book entity. Fabric has no equivalent Forge event.

The helpers use real `ServerPlayer` instances with silent test connections and invoke
real gameplay/menu/session APIs directly. The capture transport exercises session logic
without simulating a graphical client or proving end-to-end packet delivery. Separate graphical clients passed the linking/mixer, offhand and manual-Q
workflow described below. Survival block destruction, multiple simultaneous real
clients, long-duration soak and target-modpack compatibility remain pending. The checklist below contains both covered cases and
remaining manual checks; it must not be read as fully completed.

## Opt-in server smoke mod

Run only in a **disposable world**: the helper creates and modifies test blocks, items,
entities and player fixtures. It is a separate server test mod; production Mystcraft
JARs do not include its classes or command.

From the repository root, build the loader-specific helper explicitly:

```sh
./scripts/gradle.sh :mystcraft:smokeJar -PenableSmokeTests
./scripts/gradle.sh -p mods/mystcraft/fabric remapSmokeJar -PenableSmokeTests
```

Install the corresponding production Mystcraft JAR and only that loader's finished
smoke JAR into the disposable server's `mods/` folder:

- Forge: `mods/mystcraft/build/smoke-libs/mystcraft-smoke-forge-1.19.2-1.0.0.jar`.
- Fabric: `mods/mystcraft/fabric/build/smoke-libs/mystcraft-smoke-fabric-1.19.2-1.0.0.jar`,
  together with Fabric API. Do not install the `-dev.jar`.

Start the server and enter `mystcraft_smoke` at its console. An authorized level-4
operator can also run `/mystcraft_smoke`. Preserve `logs/latest.log`: successful
assertions emit `MYSTCRAFT_SMOKE_PASS`, the complete run emits
`MYSTCRAFT_SMOKE_COMPLETE <count>`, and failures emit `MYSTCRAFT_SMOKE_FAILURE`.
Run separately on both loaders and record the actual output in the validation report.
Remove the test mod after the run; it is not part of the user-facing gameplay artifact.

## Survival crafting and mixer

- Craft an ink vial with **two ink sacs or black dyes in separate slots** plus
  one water bottle. Alternatively use two dyes, a glass bottle and water bucket;
  the bucket must remain in the crafting grid. Non-water potions must not match.
- The mixer uses classic wood material, hardness/resistance and wood sounds.
- Craft an ink mixer using the rows `stone / empty / stone`,
  `stone / glass bottle / stone`, and `plank / stone / plank`.
- Open its inventory. Insert vials in the upper-left slot and paper below.
  Empty bottles appear upper-right, linking panels lower-right. Each panel costs
  one vial's ink charge and one paper; a vial returns a glass bottle.
- Shift-click inputs and outputs; repeat with full player inventory and full
  output slots. No item may disappear or duplicate. Two players may open the
  same mixer simultaneously: aggregate ingredient/output counts must balance.
- Stop/restart with partially filled slots and with ink loaded but no paper.
  All contents and the ink charge must survive. Break the mixer: stored items
  drop once; basin ink is lost, as in classic Mystcraft.
- Craft one panel plus one leather into an unlinked linking book. Extra or
  invalid ingredients must not match. Panel NBT is copied, but link modifiers
  themselves are not implemented.

## Basic travel and recovery

1. In the Overworld, stand on solid ground with two clear blocks above and bind
   an unlinked book using right-click. Record coordinates and facing.
2. Open the bound book in the Overworld. The panel must be disabled: normal
   books do not support same-dimension travel. Opening/closing must not teleport.
3. Take it through a vanilla Nether portal. Open the book; activate the panel.
   You should return to the exact saved Overworld position and facing, hear the
   original linking sound, and have no book in that hand.
4. Return to the Nether by portal. The book should remain as a physical book
   entity. Walking over it does not collect it; right-click opens it. Sneak with
   an empty hand to recover it. It must survive more than five minutes unloaded
   and loaded and a full server restart.
5. Repeat using the offhand. Use two clients to inspect the original location,
   item removal and link destination. One book must not be recovered twice.
6. Drop a book with Q. It should use the same interactive entity. Click its
   panel to travel; the entity remains for the next reader. Move/pick it up
   while another player has it open; that stale screen must not link.

## Bookstand

Craft a stand with two sticks separated by an empty slot in one row and a
plank directly below that empty slot. Place it and right-click with a
bound linking book to insert. Open and activate it from the other dimension:
the book must remain on the original stand. Repeat with two players.

- Sneak with an empty interacting hand to recover the book directly into that
  same hand. Sneaking with a nonempty hand must not remove it. Break the stand: exactly one stand and one book drop
  in survival. Restart while occupied and verify destination NBT and rendering.
- Remove/replace the book, destroy the stand, unload its chunk, or move away
  while its screen is open. Stale activation must fail without losing a book.
- Check north/east/south/west placement, visual alignment, rotated collision/outline
  bounds, and model/texture logs.
  The prototype stand is simplified; it is not the original eight-way model.

## Rejection and regression checks

- Opening a book must never authorize a future arbitrary destination. Swapping
  books/slots, moving, dying, changing dimensions or waiting over 60 seconds
  invalidates the session. Repeated activation has no second effect.
- An invalid/missing destination must fail without consuming the source book.
- Obstruct the saved landing point, fill it with liquid, or move the world border
  across it: linking must fail, leaving both player and book at the source.
- On Forge, test a server mod vetoing `EntityTravelToDimensionEvent`. It must receive one
  event; rejection must retain the held book and leave no duplicate entity.
  On Fabric, separately test the actual permission/protection mods in the target
  modpack; Forge-specific travel events do not exist there.
- Open screens at multiple GUI scales; use Tab/Enter to activate the book panel;
  verify both Escape and the configured inventory key close the book screen;
  inspect language strings, textures, recipe outputs, sounds and subtitles.

## What remains before classic Mystcraft gameplay testing

Descriptive books, ordered page writing, binder/desk, generated/persistent Ages,
symbol grammar, libraries/trading, crystal portals, classic instability and
other items need implementation. Link modifiers, destination previews, exact
book/stand models, classic physical-book health/decay and mixer additives also
remain incomplete. New Mystcraft2 stability is outside the requested scope.

Passing this checklist only establishes the linking prototype's behavior; it
cannot establish feature parity for systems that do not exist yet.

### Graphical checks completed

Matching Forge and Fabric development clients joined their packaged dedicated
servers. Both rendered the original book/mixer artwork, collected paid mixer
outputs, closed books with E, linked by mouse-click to the exact Nether
coordinates, and returned using an offhand book. Manual Q-drop produced exactly
one custom book entity and zero ordinary item duplicates with destination data
retained on both loaders. Full acceptance still needs survival
block-break drops, two simultaneous networked clients, multiple GUI scales,
soak/modpack sessions and the missing classic features. See `validation.md` for
scope, logs and shutdown/environment limitations.
