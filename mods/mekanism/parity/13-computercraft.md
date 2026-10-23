## ComputerCraft

Machines, multiblocks and the QIO and transporter blocks that carry `AttributeComputerIntegration` expose the same
computer methods upstream binds: energy, configuration, and the formed multiblock's own methods. The shared classes
are `mekanism.common.integration.computer` and `mekanism.common.integration.computer.computercraft` in `src/main`.
OpenComputers 2 has no 1.19.2 build and is not ported; `OC2Loaded` stays false. See
[18-flux-recipe-stages-oc2.md](18-flux-recipe-stages-oc2.md).

CC: Tweaked is optional. The JARs load without it, and the GameTests do not install it. A development run includes
it with `-PwithComputerCraft`.

| Mod | Loader | Version | Coordinate |
| --- | --- | --- | --- |
| CC: Tweaked | Forge | 1.101.2, the API upstream Mekanism 10.3.9 compiled against. 1.101.3 and 1.101.4 are later Forge-only 1.19.2 builds | `org.squiddev:cc-tweaked-1.19.2:1.101.2` (`:api` at compile time), `https://maven.squiddev.cc` |
| CC: Restitched | Fabric | 1.101.2 (Modrinth `3S6HU7nl`, file `cc-restitched-1.101.2.jar`). CC: Tweaked published no Fabric jar for 1.19.2; the first official Fabric builds are 1.19.4. Restitched is that line's Fabric distribution and uses the mod id `computercraft` | `maven.modrinth:cc-restitched:3S6HU7nl` |

Forge reads the peripheral from the `IPeripheral` capability, the same token CC: Tweaked registers, so no extra
provider is needed. Restitched has no Forge capability and its `IPeripheralProvider` returns the peripheral
directly. `FabricPeripheralProvider` (`src/fabric`) is loaded by name from `MekanismHooks` only when
`computercraft` is present, and it returns the peripheral the shared capability resolver already caches.

Method names are scanned at startup. Parameter names come from `data/<modid>/parameter_names/computer.json`, copied
from the Mekanism and Mekanism Generators 10.3.9.13 release jars. The annotation processor that writes those files
was not imported. A missing file leaves the parameter names empty; the methods still bind. GameTest servers set
`mekanism.gametest.computer` so the scan runs with CC: Tweaked absent. The capability is attached only when the mod
is loaded.

`ComputerGameTests` asks an electric pump and a formed induction matrix for their method lists, calls `getEnergy`,
and checks a logistical sorter and a QIO importer. The CC argument-wrapper JUnit tests run against the API without
a world.

Loom's remapped development copy of Restitched drops the `jars` array, so the screenshot client adds the nested
Cobalt and Netty HTTP codec jars itself. Night Config is already on the classpath. An installed Modrinth jar still
nests Cobalt, Night Config and the Netty HTTP codec.
