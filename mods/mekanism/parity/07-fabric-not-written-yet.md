## Fabric: not written yet

- Entity leave is posted on the server. The client copy Forge posts when an entity stops being tracked is unwired.
- Not yet checked in the client: chemical machine screens other than the chemical infuser and the pigment extractor, QIO, Robit,
  the simple generators' block-entity renderers. The energy cube core, the industrial alarm, the seismic vibrator and the
  pigment mixer were checked in `11-block-entity-renderers.md`. The
  fission burn glow and coolant body were not seen: the client reactor was not fueled, and its screen was not opened.
  Factory screens other than the basic smelting factory were not opened. The wind generator item is
  registered with Fabric's builtin item renderer; that registration has not been watched in a client. A formed
  dynamic tank holding water was checked: the fluid body renders inside the structural glass. A formed induction
  matrix was checked: the casing and port models draw. Upstream 10.3.9 ships no separate matrix renderer. A formed
  industrial turbine was checked: the rotor and its blades render inside the structural glass. A formed thermoelectric
  boiler was checked: the casing and the input, steam and coolant valves render, and the boiler screen opens. The water
  and steam bodies are drawn inside the opaque casing, so they are not visible from outside. A formed thermal
  evaporation plant was checked: the controller face is lit, and the input fluid renders through the structural
  glass. An advanced solar panel on a top corner was not placed. A formed fission reactor was checked:
  `/mek build fission` made an 18×18×18 shell, and reactor glass shows the casing frame and a checkerboard of fuel
  assemblies and control rods. A formed supercritical phase shifter was checked: `/mek build sps` made a 7×7×7
  shell, and structural glass shows the casing frame and the empty corner cells of each face. That build has no
  port and no coil, so the orb and the coil bolts were not drawn. A placed digital miner was checked: its screen opens and shows
  Idle, the search state, the blocks left to mine, the eject, pull and silk switches, and the config, start and
  stop buttons. The area-preview cuboid was not toggled in that client. A formed fusion reactor was checked:
  `/mek build fusion` made a 5×5×5 shell. The frame and its holes are visible, and the controller is the top centre.
  The plasma renderer draws only while the reactor is burning; this reactor was not fueled, so the plasma was not
  drawn, and the controller screen was not opened. A red basic logistical transporter was checked.
  On Fabric the moving stack is a depth-tested cutout sprite. The cutout shell is already in the depth
  buffer before the block entity, so the sprite is shifted just past the near face of its block and flushed
  before the colored glass. A solid block in front of the tube hides it. Forge keeps the upstream item
  renderer. The sorter, diversion
  and restrictive screens were not opened, and mechanical
  pipes and pressurized tubes were not watched in a client. A formed teleporter was checked: the frame and the purple portal
  are visible. The quantum entangloporter screen opened beside it, with the frequency selector, the energy tab, the
  heat tab and the inventory. A survival player flying with a hydrogen jetpack was checked: close third person, HUD
  hidden, and flame particles visible behind the jetpack.
