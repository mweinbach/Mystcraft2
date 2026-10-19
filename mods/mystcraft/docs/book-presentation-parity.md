# Classic book presentation source audit

Source: pinned `Mystcraft-Legacy` 1.12 checkout, particularly `GuiElementBook`,
`LinkRendererDisarm`, `RenderLinkbook`, `RenderBookstand`,
`RenderBookReceptacle`, and `integration/lookingglass/DynamicLinkPanelRenderer`.

The core link panel is a blue vertical gradient when its destination dimension
is registered, and black otherwise. “Visited” is the historical method name;
`DimensionUtils.isDimensionVisited` checks dimension registration, not per-player
travel history. Registration and permission to activate a book are separate.
The shared OpenBook v3 presentation carries that distinction; activation still
accepts only its existing server-owned, single-use token and validates the
original held/stand/entity stack and source location. Pages, authors and
source-book retention are unchanged.

Disarm books have the original red/white lightning overlay, with source timing
(rare after three seconds, guaranteed after eight, lasting about 300 ms), random
center/edge branches and jitter. Modern triangle quads replace deprecated
fixed-function wide GL lines. The physical book's native tracked entity event
triggers a ten-tick hurt tint without altering stored book damage. Vanilla
entity metadata and spawn transport remain shared across loaders.

Original physical books do not animate opening on interaction: dropped books
are rendered at fixed openness 1.2 and stand books at 1.05, with zero page flip.
The port retains those original poses and original `linkbook.png`/`agebook.png`
textures. The receptacle uses a nearly closed 0.005 pose. No custom cover-color
or arbitrary cover-texture feature was found in these pinned core renderers or
linking-book item classes. Such behavior must not be advertised as missing
classic core parity. The original title labels are disabled by default and
require both client `renderlabels` and server permission, with a 25-block
range. The shared `config/mystcraft-presentation.json` uses the original
`renderlabels` boolean (default false) on each side. Both must be true; the
server permission is sent on login, and clients reset it on disconnect.
Restart the client after editing its local setting. Reconnect to reload the
server setting.

Live destination camera previews are an optional LookingGlass integration,
not a screenshot capture stored in the book. That adapter creates a separate
132×83 world view for the registered destination and renders its camera texture
with an optional shader and book-damage distortion. No LookingGlass 1.19.2
adapter is provided here. The port does not substitute invented destination
thumbnails, persist screenshots, request client-selected coordinates, or claim
that its core gradient is a live camera preview.

Other confirmed renderer differences: the fissure uses the modern native
End-portal projection shader rather than eight fixed-function texture-gen
layers. Receptacle direction transforms match source.
Pitch and yaw are now persisted as signed modulo-360 shorts and rendered
after the base orientation; operators may set them through ordinary block
entity data. No new rotation gesture is invented. The writing desk/collection
UI is maintained separately. Optional labels require both local preference and server permission. Portal
surfaces now use the source display-name hash and three sequential random color
bytes; missing world context uses source blue, and missing source uses white.
Legacy DisplayName/agename and modern custom book titles provide the name.
Crystals retain their original texture: upstream registers no crystal tint.

Link endpoints now select original sounds in source precedence: item/book pop,
Disarm, explicit portal/fissure sound, Following, Intra Linking, ordinary link.
The four additional original OGGs are recorded in imported-assets.json. Successful
links emit fifty custom gray link particles per endpoint with the original
triangular horizontal distribution, rising randomized drift, lifetime, growth,
and eight smoke frames. Native particle packets replace the legacy burst packet;
this sends fifty bounded packets per endpoint rather than one custom packet.
Artifact-specific server and graphical checks are recorded in
[validation.md](validation.md). Headless playback does not establish audible sound quality.

# Meteor adapter

`MeteorEntity`, `MeteorExplosion`, and `MeteorRenderer` adapt source
`EntityMeteor`, `ExplosionAdvanced`, its no-drop/fire/ore effects, and
`ModelMeteor`. Default scale is one and penetration zero. Environmental
spawning supplies source Y=500, Gaussian horizontal velocity ×0.25, and
vertical velocity `nextFloat()*-2-2`; flight has no invented gravity or drag.
Impact slows vertical velocity ×0.9, clears the source penetration box, and
executes the original eight overlapping explosion centers/powers. A 16³ shell
casts resistance-attenuated rays; exposure controls entity damage and knockback.
There are no terrain item drops. Fire uses 1/3 supported-cell probability;
ore uses 1/20 supported-air probability with coal/iron/gold weights 50/30/20.
The model retains fifteen source slabs, End-portal texture, original roar,
smoke/bubble particles, and thunder. Source meteors discard on save reload;
the adapter avoids saving them at all.

Modern safeguards reject invalid or excessively large explicit scales and
avoid loading new chunks while destroying terrain. Native block-update,
particle, tracked entity data and movement packets replace the source custom
explosion packet. The legacy public MeteorEvent/IMC ore-extension APIs are not
recreated. Modern block/fluid resistance and exposure replace their 1.12
counterparts, so exact cross-version crater voxel identity is not promised.

## Physical book living lifecycle

The physical entity keeps its `ItemEntity` packet/stack and cross-dimension transfer compatibility, but no longer executes dropped-item ticking. Its shared tick adapter uses native `Entity.baseTick` environmental/fire handling followed by passive living movement: dry gravity 0.08 and vertical drag 0.98, ground/air friction, water drag 0.8 and lava drag 0.5 with 0.02 sinking gravity. It does not use item buoyancy, lava bouncing, merging or automatic player pickup. Native collision movement applies fall damage through the book's health rules. Native fire/lava contact retains the living repeated-hit window and source doubled fire damage.

The original `EntityLinkbook.decreaseAirSupply` returns `air - 2`; the adapter consumes two air per submerged-eye tick, deals two drowning damage at -20, resets air to zero, and restores air outside water. Native entity air NBT persists this state. The separate one-point wet/rain damage, 10,000-loaded-tick decay and ten-health book NBT remain. Lethal damage now leaves a noninteractive, moving corpse for twenty loaded ticks before removal, rather than instantly discarding the book.

Source collision with a hopper minecart inserts the book and preserves any remainder. The adapter performs that transfer only for an overlapping cart and copies the exact stack/NBT into native container insertion before removing the source. A narrow loader mixin prevents vanilla loose-item hopper suction from collecting this `ItemEntity` subclass; this preserves the original living entity's exclusion from stationary hopper pickup. `createBookWrapper()` exposes a live, one-slot linking-book container for the book display menu. The old wrapper's unsafe insertion overwrite/simulation defects are not reproduced: normal container ownership and filtering apply.

The implementation remains a modern shared adapter, not a fabricated `LivingEntity` hierarchy: living AI, equipment, potion attributes and addon entity-class checks are not supplied. Focused native fixtures cover gravity, submerged air/drowning/save state, death delay, stationary hopper exclusion and full/available hopper-cart transfer with exact damaged-book NBT; their packaged runtime result must be checked separately.
