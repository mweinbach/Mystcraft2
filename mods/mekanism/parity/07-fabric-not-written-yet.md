## Fabric: not written yet

- Forge events that are not fired yet: client level load and unload, and recipes updated. Entity leave is posted on
  the server; the client copy Forge also posts is unwired.
- One key mapping bound to more than one key, and custom ingredient types. Modifiers on a mapping are not saved.
- Not yet checked in the client: chemical machine screens other than the chemical infuser and the pigment extractor, QIO, the digital miner, Robit, gear,
  the energy cube's core renderer, the simple generators' block-entity renderers. The
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
  port and no coil, so the orb and the coil bolts were not drawn. A formed fusion reactor was checked:
  `/mek build fusion` made a 5×5×5 shell. The frame and its holes are visible, and the controller is the top centre.
  The plasma renderer draws only while the reactor is burning; this reactor was not fueled, so the plasma was not
  drawn, and the controller screen was not opened. A formed teleporter was checked: the frame and the purple portal
  are visible. The quantum entangloporter screen opened beside it, with the frequency selector, the energy tab, the
  heat tab and the inventory.
