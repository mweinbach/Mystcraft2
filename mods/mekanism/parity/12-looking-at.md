## Look-at tooltips

Jade, WTHIT and The One Probe read energy, fluids, chemicals and a formed multiblock's tanks. The shared data is
`mekanism.common.integration.lookingat` in `src/main`. Jade's plugin is there too, because Forge Jade 8.9.2 and
Fabric Jade 8.7.3 expose the same `snownee.jade.api` methods this port calls. The One Probe is Forge-only
(`src/forge`). WTHIT is Fabric-only (`src/fabric`). None of the three is bundled, and none is required to load or to
run the GameTests.

| Mod | Loader | Version | Coordinate |
| --- | --- | --- | --- |
| Jade | Forge | 8.9.2, game versions 1.19–1.19.2 (Modrinth `kp0HjPre`, 2023-11-30) | `maven.modrinth:jade:8.9.2` |
| Jade | Fabric | 8.7.3, the latest Fabric build for 1.19.2 (Modrinth `ZASePpsm`, 2023-01-12) | `maven.modrinth:jade:ZASePpsm` |
| The One Probe | Forge | 1.19-6.2.2 (Modrinth `KCnKC3d7`, 2023-01-26). Upstream compiled against 1.19-6.2.0-6; this API matches that source | `maven.modrinth:the-one-probe:1.19-6.2.2` |
| WTHIT | Fabric | 5.36.1 (Modrinth `APJpZnsA`). A development run also needs Bad Packets `fabric-0.2.3` (Modrinth `7Y5NcPRW`) | `maven.modrinth:wthit:APJpZnsA` |

Forge discovers Jade from the `@WailaPlugin` annotation. Fabric lists the same class on the `jade` entrypoint. The
One Probe is registered with the upstream IMC message; the shared hook loads `TOPProvider` by name so Fabric does not
compile against that mod. WTHIT reads `custom.waila:plugins` in `fabric.mod.json`. Its registration is cast to
`IServerDataProvider` because WTHIT 5.36 also overloads `addBlockData` with `IDataProvider`. Upstream's Forge WTHIT
plugin is not in the Forge JAR. There is no 1.19.2 Fabric build of The One Probe.

`LookingAtGameTests` fills an electric pump (energy and water) and a chemical tank (hydrogen), and reads the NBT
Jade and WTHIT write. A formed dynamic tank's structural glass reports the tank's water; a lone structural-glass
block writes nothing. The test runs with none of the tooltip mods installed.
