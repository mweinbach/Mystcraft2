# JurassiCraft Fabric for Minecraft 1.19.2

Status: **in progress**. This Fabric port targets [Tekxit 4 (Official)](https://www.technicpack.net/modpack/tekxit-4-official.1921233), Minecraft 1.19.2 and Java 17. It requires Fabric Loader 0.16.14 or later and Fabric API 0.77.0 or later. The pack supplies Jade and EMI, which are optional integrations.

The port includes the ten species enabled by the imported 1.12.2 version, fossils and nests, plaster wrapping, cleaning and grinding, DNA processing, incubation and coelacanth cultivation. It also includes prehistoric plants and trees, dinosaur feeding, breeding and carcasses, skeletal displays, electric fencing, vehicles, park decorations, insect cultures, villagers, village buildings, a visitor centre and a raptor paddock. Original art, sounds and model components are reused.

The JAR has the in-game mod ID and namespace `jurassicraft`. The separate [Forge build](../jurassicraft/) cannot be installed in the same instance. Original 1.12.2 worlds and items are not migrated.

Natural dinosaur and goat spawning are disabled by default. Enable `naturalDinosaurSpawning` and `naturalGoatSpawning` in `config/jurassicraft-fabric.json` and restart. That file also controls fossil, plant and structure generation; generation changes affect new chunks. Vehicle foliage destruction is controlled by `destroyBlocks` in `config/jurassicraft-vehicles.toml`. Tekxit's Open Parties and Claims can intercept this destruction, but treats the vehicle as the actor rather than its driver; claimed-world behavior has not yet been tested.

Right-click an imprinted dinosaur with an empty hand to choose wander, follow or sit. Sneak-right-click it once it is over 75% grown to access its inventory. Sneak-right-click an owned, sitting microraptor to carry it on your shoulder; C puts it down. Sneak-use a dinosaur spawn egg in the air to change its sex, or sneak-use it on a block to spawn a baby. The field guide opens as a manual when used in the air, or examines a dinosaur when used on one; reopen it to refresh its measurements.

While seated in a vehicle, keypad 1–9 select seats and keypad 0 controls its record player. For helicopters, Home toggles autopilot, End toggles rotation lock, and the arrow keys rotate. Movement uses the usual movement, jump and sprint keys. All these keys are configurable in Minecraft's controls menu.

Jade provides dinosaur and machine information. EMI shows previews for machine processes, supports transfer into machine input slots, and opens the matching recipes from the progress areas. The cultivator transfer fills its syringe slot; water and nutrients are supplied separately.

World-generation distributions and some machine, animal and vehicle behaviors are approximations. Dinosaur fossil attempts match each species' original count and height distributions, but species' counts are independent within a chunk instead of sharing the same 32 attempts. Some animations are simplified and differ from upstream. Plant fossils replace tagged coal ores generated during the usual ore stage, not untagged or later ores. Village buildings use weighted jigsaw pools without the original per-village limits, and custom villages that replace vanilla house pools may need their own entries. Paddock rarity is approximate. The missing upstream cryonics loot table uses laboratory loot. The hybridizer has no recipes because the imported version registers no hybrid species. Third-party structure search APIs may skip the visitor centre's custom placement; the vanilla `/locate structure jurassicraft:visitor_centre` command is supported. Dinosaur carcass death-event timing differs from the Forge port. Multiplayer and interactions with other mods need broader testing.

Build from the workspace root:

```sh
./scripts/gradle.sh :jurassicraft_fabric:build
./scripts/gradle.sh :jurassicraft_fabric:runGametest -PwithJade -PwithEmi
```

The output is `mods/jurassicraft_fabric/build/libs/jurassicraft-fabric-1.19.2-0.1.0-alpha.1.jar`. The Fabric build reads shared artwork from the Forge reference port at build time; it does not bundle Forge or either optional integration. The test run creates a disposable world under `build/gametest` and writes `build/gametest-results.xml`.

The [upstream snapshot and attribution](../jurassicraft/UPSTREAM.md) are in the Forge reference port. The imported revision has no license file; the historical license recorded there does not establish terms for this revision or its assets.
