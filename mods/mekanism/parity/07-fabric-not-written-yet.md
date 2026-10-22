## Fabric: not written yet

- Forge events that are not fired yet: chunk data load and save, `AddReloadListenerEvent`, client level load and
  unload, and recipes updated. Entity leave is posted on the server; the client copy Forge also posts is unwired.
- One key mapping bound to more than one key, and custom ingredient types. Modifiers on a mapping are not saved.
- Not yet checked in the client: chemical machine screens other than the chemical infuser and the pigment extractor, the other multiblocks (evaporation and SPS), QIO, the digital miner, Robit, gear,
  the energy cube's core renderer, the simple generators' block-entity renderers, and the fusion renderer. The
  fission burn glow and coolant body were not seen: the client reactor was not fueled, and its screen was not opened.
  Factory screens other than the basic smelting factory were not opened. The wind generator item is
  registered with Fabric's builtin item renderer; that registration has not been watched in a client. A formed
  dynamic tank holding water was checked: the fluid body renders inside the structural glass. A formed induction
  matrix was checked: the casing and port models draw. Upstream 10.3.9 ships no separate matrix renderer. A formed
  industrial turbine was checked: the rotor and its blades render inside the structural glass. A formed thermoelectric
  boiler was checked: the casing and the input, steam and coolant valves render, and the boiler screen opens. The water
  and steam bodies are drawn inside the opaque casing, so they are not visible from outside. A formed fission
  reactor was checked: `/mek build fission` made an 18×18×18 shell, and reactor glass shows the casing frame and a
  checkerboard of fuel assemblies and control rods.
