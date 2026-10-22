## Block entity renderers

Checked on the Fabric client at noon, HUD hidden, in a Creative superflat world. The shared renderer classes match
upstream 10.3.9. Forge registers them on its event bus; Fabric posts the same events into Fabric's renderer registries.
No separate gameplay was added.

The energy cube, industrial alarm, seismic vibrator and pigment mixer block-entity renderers, and the energy cube item
renderer, are the upstream classes. What was missing on Fabric was model-face light. Forge 43.5 stores a face's
`emissivity` and writes it into the quad lightmap after baking. Vanilla drops that JSON key, so the cube's tier-coloured
corners and the lit faces on the alarm, vibrator and mixer stayed dark. Fabric now rejects an emissivity outside 0–15,
stores it on the face, and writes `LightTexture.pack(emissivity, emissivity)` into the quad. Item rendering uses Forge's
quad pass, so a baked light and a baked vertex colour survive. `requestModelDataUpdate` is still empty: Fabric reads
model data when it rebuilds the chunk, and a side-config change already marks the chunk for rebuild.

Seen in that world:

- Five energy cubes in a row. Basic is bright green, advanced dark red, elite light blue, ultimate purple, creative
  black. The corners glow in the tier colour, and the side ports are drawn. The basic cube was holding 3,200,000 J of
  its 4,000,000 J and showed a green core; the creative cube showed a dark core. Two frames of the unpaused view
  differed, which is the core, the alarm and the machine shafts moving. An adjacent creative cube did not raise the
  basic cube's stored energy during this check, so the core was confirmed on a charged cube rather than watched while
  the amount climbed. The held energy-cube item was not watched.
- The industrial alarm, powered by an adjacent redstone block (`activeState` 1, `redstone` 1), showed its red active
  light. Right-click was not confirmed to open a screen; the alarm has no container.
- The seismic vibrator was active and spending energy (the container fell from 1,000,000 J to 891,700 J before it was
  refilled). The shaft is visible above the base.
- The pigment mixer was processing. Its tanks held red and yellow pigment and produced orange (the output tank reached
  1,150 mB). The shaft is visible. The screen was not opened; the earlier pigment-extractor screen check is unchanged.

The personal chest, bin, fluid tank, nutritional liquifier, bio generator and wind-generator item renderers match
upstream and are registered on both loaders. They were not watched in this client session. The simple generators'
block-entity renderers stay in the unchecked list.
