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
  mode changes use this. The wheel posts `MouseScrollingEvent`; while the player is sneaking and the held stack is a
  mode item, `ClientTickHandler` sends `PacketModeChange` for that hand. The server steps the configurator, the atomic
  disassembler and a Meka-Tool that has a module handling mode changes.
- Client level load at the end of the `ClientLevel` constructor. `Mekanism.onWorldLoad` stores that level, which is
  the world player-state sounds read.
- Client level unload at the start of `Minecraft.setLevel`, before the joining screen, while the previous level is
  still current, and inside `Minecraft.clearLevel` after the screen is shown and before the integrated server is
  waited on. Unload drops oredictionificator invalidation listeners that belong to that level.
- `RecipesUpdatedEvent` at the end of `ClientPacketListener.handleUpdateRecipes`, after the client recipe book is
  rebuilt. `ClientTickHandler` clears the recipe cache there.
- A key press is delivered to every mapping bound to that key whose conflict context and modifier are active. A release
  clears every mapping on that physical key, including one whose modifier is no longer held, so a chord cannot stay down.
  The mapping still has to be in an active conflict context with its modifier held before it counts as down.

Fog color and distance, `EntityRenderersEvent.AddLayers` and the mouse wheel already fired from the mixins above.
This slice checked them again and left those adapters in place. Fabric API 0.77 has no client-world or recipe
callback, so level load, level unload and the recipe refresh are mixins at Forge 43.5.0's patch sites.

The shared GameTests encode and handle the mode-change packet: a forward shift, a backward shift and a shift of zero
on the configurator, the atomic disassembler and a Meka-Tool with one excavation escalation module, a plain item, a
packet with no sender, and a packet aimed at the other hand. A Fabric development client reached the title screen with
these mixins applied: client level load, unload from `setLevel` and `clearLevel`, and the recipe refresh at the end
of `handleUpdateRecipes`. The mouse wheel, fog and armor-layer mixins still applied. No world was joined.

Checked on a Fabric development client in creative and then survival: the MekaSuit module list, jetpack mode,
configurator mode and compass draw, and the survival energy bar draws above the health row. The radiation tint reads
the client dose. That dose is synced from the player tick. The player tick was unwired when the client was checked,
so the tint was not seen. The MekaSuit armor layer is attached when the renderers reload.
