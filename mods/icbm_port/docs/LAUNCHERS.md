# Launcher and targeting port

This subsystem adapts the MIT-licensed ICBM Classic launcher inventory, energy,
coordinate targeting, and radio concepts to Fabric 1.19.2. It does not reproduce
the original multipart launcher network, frame tiers, RF/Forge energy capability,
or original graphical assets.

- Insert a missile by using it on the launcher or opening the empty-hand screen.
  The actual missile stack is saved, retaining its payload and item data. Slot 0
  accepts missile items; slot 1 accepts redstone dust. Hoppers can access these
  inventory slots. Breaking the launcher drops its inventory.
- Every new launcher contains 1,000 units of internal charge. Firing costs 1,000;
  each redstone dust supplies 1,000; capacity is 10,000. Use dust on the block or
  place dust in the fuel slot and press Charge. Launching also consumes fuel if
  the buffer is empty. This is a standalone gameplay energy system, not an RF or
  Tech Reborn energy bridge.
- Direct fire follows the placement facing, including vertical placements.
  Target arc mode uses the port's bounded coordinate flight approximation rather
  than upstream staged ballistic physics. A clear muzzle is required in both
  modes. Targets must be within 4,000 blocks, inside the world border and build
  height. Flight does not load chunks.
- Use a GPS in the air to record your position, or on a block to record that
  block. The laser designator records a server raycast hit up to 256 blocks away;
  without a hit it records the player's position. Use either on a launcher to
  copy its recorded target. Sneak-use records the launcher position instead.
  Targets store their dimension and cannot transfer across dimensions.
- Configure coordinates, direct/arc mode, and a 0–9,999 radio frequency in the
  screen. Save Target applies the settings. Launch applies them and attempts a
  launch; failures preserve the payload. A radio detonator pairs by using it on
  a launcher. Air-use requests launch from that paired launcher within 256
  blocks in the same dimension with its matching channel and a loaded chunk.
- A rising redstone edge schedules a launch four ticks later. Sustained power
  does not repeatedly fire or launch ammunition inserted after the pulse.
  EMP zones temporarily prevent launch.

Screen packets are resolved against the sender's currently open launcher menu,
matching synchronization ID, living block entity, eight-block interaction
range, spectator state, bounded target and channel, fixed packet length and
known action. They never specify an arbitrary launcher position or load chunks.
Invalid settings leave the prior settings unchanged. Inventory, energy, target,
mode and radio frequency survive chunk saves; malformed saved energy/channel
values are clamped. Legacy loaded block states without an inventory stack fire
one condensed missile so worlds from the initial prototype remain usable.

Models reference vanilla textures. Client layout and multiplayer interaction
still require a manual in-game visual check.

## Launcher structures and connected controls

Registered tier 1/2/3 bases have respective ranges of 1,000/2,500/4,000 blocks,
energy capacities of 10,000/25,000/50,000, and launch costs of 1,000/2,000/3,000.
They face upward and require 1/2/3 launcher frame blocks touching their horizontal
sides. The original `launcher` remains the inexpensive directional prototype
without a frame requirement. The cruise launcher has tier 1 energy/range limits
but fires directly toward configured coordinates instead of following an arc,
and does not require frames.

Launcher connectors, support blocks, frames, screens, and bases form a physical
six-neighbor network. Traversal never loads chunks, visits at most 128 component
nodes, and stays within 32 blocks of its starting component. Removing a connector
immediately breaks access; no stale network cache or remote position packet is
trusted. A launcher screen opens the first connected base; sneak-use cycles
the player's selected base in the connected network. The connected screen must
remain within eight blocks of the player to keep remote menus valid. Redstone
at a screen schedules a network launch pulse and marks the bases triggered until
power drops. A support accepts redstone dust and adds 1,000 units to the first
connected base with room; connectors and frames report network status.

These are smaller Fabric implementations of the original launcher's structural
roles. There is no shared energy/inventory pool, original support accuracy bonus,
frame animation, rotating GUI launcher selection, or original multi-part bounding
geometry. Tier constraints and cruise steering use the explicit rules above.

## Held-item delivery

All launcher inventories and hand-loading accept a loaded held-item missile via
its actual cargo validation; an empty casing is rejected. The launcher calls the
held-item delivery entity helper, copying the complete cargo and action-mode NBT.
The regular muzzle, frame, EMP, tier range and energy constraints apply. Targeted
bases use its bounded arc; cruise bases use direct target velocity. The missile
and launch charge are consumed only after a successful spawn. No explosive
payload type is fabricated for this transport.

A fixed block launcher has no live player owner. Held-item operations that require
player ownership (block placement or tool mining) therefore recover the intact
missile/cargo under the delivery subsystem's automation fallback. A block launcher
does not impersonate the player who last opened its screen.
