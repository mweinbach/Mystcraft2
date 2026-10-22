## Fabric: client events

These fire at the same points Forge 43.5.0 does. Fabric API is used where it has the callback; the rest are mixins.

- Client tick start and end, and the client level tick, from Fabric's client tick events.
- Render tick start immediately before `GameRenderer.render`, and render tick end after the toast pass. The radiation
  overlay and the fading item-mode line are drawn on the render tick end.
- HUD overlays registered above the hotbar and above the armor level are drawn at those anchors. The crosshair overlay
  pre event can cancel the crosshair. Vanilla still draws its own HUD parts; only the crosshair posts
  `RenderGuiOverlayEvent`, because that is the vanilla overlay Mekanism cancels.
- Fog color, then fog distance, after vanilla has computed each. A canceled distance event replaces the near plane, far
  plane and shape, which is how the MekaSuit's vision enhancement pushes fog out.
- Level stage after translucent blocks, from Fabric's world render event, and after particles (both the normal and the
  fabulous path). Sky, solid, cutout, tripwire and weather stages are not posted.
- Living entity render pre and post. A canceled pre skips the entity and does not post the matching post event.
  First-person arms post `RenderArmEvent` and skip vanilla's arm when it is canceled, which is where the MekaSuit arm
  is drawn.
- `EntityRenderersEvent.AddLayers` after the entity render dispatcher rebuilds its renderers. That attaches the
  MekaSuit armor layer and the elytra layer.
- Block highlight before vanilla's outline, from Fabric's before-block-outline event. Canceling a block highlight
  skips the outline. The configurator side overlay and custom selection boxes use this event. An entity under the
  crosshair posts the entity highlight from the same callback; vanilla still draws no entity outline.
- Mouse wheel in game with no screen open, after a whole notch has accumulated and before the hotbar moves. On macOS a
  horizontal wheel with no vertical movement counts as vertical. Canceling the event consumes the notch. Shift-scroll
  mode changes use this.
- A key press is delivered to every mapping bound to that key, so an unmodified binding and a modified one both see it.
  The mapping still has to be in an active conflict context with its modifier held before it counts as down.

Checked on a Fabric development client in creative and then survival: the MekaSuit module list, jetpack mode,
configurator mode and compass draw, and the survival energy bar draws above the health row. The radiation tint reads
the client dose. That dose is synced from the player tick. The player tick was unwired when the client was checked,
so the tint was not seen. The MekaSuit armor layer is attached when the renderers reload.
