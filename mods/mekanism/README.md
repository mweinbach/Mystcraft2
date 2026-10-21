# Mekanism for Minecraft 1.19.2

An unofficial, in-progress port of [Mekanism](https://github.com/mekanism/Mekanism) 10.3.9 (the last 1.19.2
release, `v1.19.2-10.3.9.13`) to **Fabric and Forge** from one shared codebase. Tekxit 4 runs on Fabric, so the Fabric
build is the primary target. Upstream attribution and every edit to upstream files are listed in
[UPSTREAM.md](UPSTREAM.md); gaps are tracked in [PARITY.md](PARITY.md).

| Loader | Requirements | Energy | Items and fluids | Chemicals and heat |
| --- | --- | --- | --- | --- |
| Fabric | Loader 0.16.14+, Fabric API 0.77.0+1.19.2 | Team Reborn Energy (bundled) | Transfer API | `BlockApiLookup` / `ItemApiLookup` |
| Forge | Forge 43.5.0 | Forge Energy | Forge capabilities | Forge capabilities |

These are the planned interop targets. The two builds cannot be mixed in one game, and worlds do not migrate between
them.

## Status: core module, simple generators and Tools; many features still missing

The core module is in this port. Generators, Tools and Additions are extra mod ids
(`mekanismgenerators`, `mekanismtools`, `mekanismadditions`) built from the same tree, each as its own jar that
depends on the core jar. Generators' code, assets and generated data are imported. Heat, solar, advanced solar,
gas-burning, bio and wind generators produce energy on both loaders. The industrial turbine forms, takes steam
through the gas handler and outputs energy. Fission and fusion are not verified yet. Tools' paxels, tools, armor
and shields are imported, and both loaders register them, craft them and run their strip, flatten and till actions.
Additions is an empty scaffold.

- **Forge.** The upstream core sources build against Forge 43.5.0 unchanged except for the removed integrations
  listed in [UPSTREAM.md](UPSTREAM.md). The shared GameTests pass on a dedicated GameTest server. The Forge client
  has not been launched.
- **Fabric.** The same sources compile on Fabric. Their Forge API references are rewritten at build time to the
  port's Forge API mirror in `src/fabric/java/ports/mekanism/lib`, and the methods Forge adds to vanilla classes are
  supplied by injected interfaces and mixins. The JAR loads on a server and in a development client, where the
  enrichment chamber, crusher, energized smelter and metallurgic infuser run on energy delivered directly or through
  universal cables. Energy, items and fluids reach other mods through Team Reborn Energy and the Transfer API, and
  other mods can charge Mekanism's energy items and fill its fluid items the same way. Gas, infusion, pigment,
  slurry and heat are reachable the same way through the lookups below.
  Item and block hooks vanilla calls (break, fuel, enchanting, redstone, light, portals, elytra) are wired.
  Mekanism fluids render in the world, in buckets and in fluid tank gauges, tinted from their fluid types.
  Server player and living events are wired, so jetpacks, free runners, scuba tanks, radiation and MekaSuit mining
  modules run on a server. The client tick publishes the jump key those jetpacks read.
  Block entity renderers use Forge's render bounding box, and entities run `onRemovedFromWorld` when they leave tracking.
  A dynamic tank forms from casing, structural glass and a valve, the valve moves fluid through the Transfer API, and
  the development client draws that stored fluid through the glass. A formed industrial turbine in that client draws
  its rotor and blades through the glass.
  Osmium, tin, lead, uranium, fluorite and salt generate in overworld biomes with the same placed features Forge uses.
  Client ticks, the render tick, HUD overlays, fog, living-entity and first-person arm rendering, the MekaSuit armor
  layers, block highlighting and in-game key and scroll input are wired. An anchor upgrade keeps that machine's chunk
  loaded, including after a restart. Forced chunks do not random-tick.
  [PARITY.md](PARITY.md) lists every remaining gap.
- Upstream's 60 JUnit tests pass on both loaders, and the shared GameTests (core machines, loader interop, chemical
  lookups, ore generation, the item and block hooks, fluid placement, chunk loading, render bounds, the player and
  living events, a Generators registry smoke test, the simple generators, the dynamic tank, the industrial turbine,
  and Tools registration, crafting, tool actions and shield blocking) pass on both loaders.

Do not install either JAR in a world you care about.

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | Upstream Mekanism API, core code, assets and generated data, shared by both loaders |
| `src/generators` | Upstream Generators code, assets and generated data, plus each loader's descriptor. Its own jars |
| `src/tools` | Upstream Tools code, assets and generated data, plus each loader's descriptor. Its own jars |
| `src/additions` | Empty Additions scaffold. A later slice fills it in |
| `src/forge` | Forge `mods.toml` and upstream's access transformer |
| `src/fabric` | Fabric metadata, access widener, mixins and the Forge API mirror (`ports.mekanism.lib`) |
| `fabric/` | The isolated Loom build of the Fabric variant |
| `src/test` | Upstream JUnit tests, run on both loaders |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Shared GameTests and each loader's `TestPlatform`; development only |
| `scripts` | The Fabric tag bridge generator, the loader-parity check and its Forge tag baseline |
| `emc` | EMC policy and the generated ProjectE and Item Alchemy values |

## Build and test

```sh
./scripts/gradle.sh :mekanism:build
./scripts/gradle.sh -p mods/mekanism/fabric build
./scripts/gradle.sh :mekanism:runGameTestServer
./scripts/gradle.sh -p mods/mekanism/fabric runGametest
python3 mods/mekanism/scripts/generate_fabric_tags.py --check
python3 mods/mekanism/scripts/check_loader_parity.py
python3 scripts/emc.py check
```

## Chemical lookups (Fabric)

Other Fabric mods reach Mekanism chemicals and heat through Fabric API lookups. The handler is the same capability
Forge exposes (`mekanism.common.capabilities.Capabilities`): `IGasHandler`, `IInfusionHandler`, `IPigmentHandler`,
`ISlurryHandler` and `IHeatHandler`, in `mekanism.api`. Amounts are Mekanism's chemical units, not Transfer API
droplets. Mekanism blocks still move chemicals between themselves through those capabilities; these lookups only
publish them. A missing handler is null. Query it again when you need it, and do not keep the instance after the
block is removed or the stack is replaced.

The block lookup and the item lookup for one handler share an id and are different objects. Blocks take a
`Direction`, the side being accessed, matching Forge's `getCapability(cap, side)`. Null is the internal side: a
chemical tank still reports its contents, and its side config decides what that side may insert or extract. The
tank's front is the output; the other sides accept chemicals and do not give them back. Items take a `Void` context,
which callers pass as `null`. The handler reads and writes that stack. `Action.SIMULATE` leaves it unchanged.

`BlockApiLookup.get` and `ItemApiLookup.get` return these instances when given the id, the handler class and the
context class, so a mod can import `ports.mekanism.fabric.api.MekanismChemicalLookups` or rebuild the lookup. The
constants are `GAS`, `INFUSION`, `PIGMENT`, `SLURRY`, `HEAT` and the item forms `GAS_ITEM`, `INFUSION_ITEM`,
`PIGMENT_ITEM`, `SLURRY_ITEM`, `HEAT_ITEM`.

| Handler | Id | Block context | Item context |
| --- | --- | --- | --- |
| `IGasHandler` | `mekanism:gas_handler` | `Direction` | `Void` (pass null) |
| `IInfusionHandler` | `mekanism:infusion_handler` | `Direction` | `Void` (pass null) |
| `IPigmentHandler` | `mekanism:pigment_handler` | `Direction` | `Void` (pass null) |
| `ISlurryHandler` | `mekanism:slurry_handler` | `Direction` | `Void` (pass null) |
| `IHeatHandler` | `mekanism:heat_handler` | `Direction` | `Void` (pass null) |

Mekanism registers its own block entities and items. Other mods register theirs on the same lookup. A pressurized
tube reads a neighbor through the gas capability, and on Fabric that capability falls back to this lookup, so a
port registered here is what the tube pulls. The same fallback covers infusion, pigment, slurry and heat.
Mekanism's own blocks are not asked that way. To drain a neighbor:

```java
IGasHandler handler = MekanismChemicalLookups.GAS.find(level, pos, state, blockEntity, side);
if (handler != null) {
    GasStack drained = handler.extractChemical(amount, Action.EXECUTE);
}
```

The same lookup without a compile dependency on the class is
`BlockApiLookup.get(new ResourceLocation("mekanism", "gas_handler"), IGasHandler.class, Direction.class)`.

## License

Mekanism is MIT licensed, Copyright (c) 2017-2023 Aidan C. Brady; see [LICENSE](LICENSE). The port's own code is MIT
licensed; see [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt).
