## Flux Networks, Recipe Stages and OpenComputers 2

Upstream Mekanism 10.3.9 has three optional integrations beyond ComputerCraft: Flux Networks energy, Recipe Stages in the QIO
crafting window, and OpenComputers 2 computer access. What exists for Minecraft 1.19.2 decides what the port runs.

| Mod | Forge 1.19.2 | Fabric 1.19.2 | Coordinates used |
| --- | --- | --- | --- |
| Flux Networks | Yes. 7.1.3.12 is the build upstream compiled against; 7.1.4.13 is the last 1.19.2 build | Yes, shurik204's MIT port `flux-network-fabric` 1.19.2-7.2.1.16 (also 7.2.1.15). It moves energy through Team Reborn Energy and nests Porting Lib 2.1.1177 (below) | Forge `curse.maven:flux-networks-248020:4353161`; Fabric `maven.modrinth:flux-network-fabric:1HFd7hEa` |
| Recipe Stages | Yes, 4.0.0.4 to 4.0.0.7. Needs Game Stages (11.1.4, which needs Bookshelf 16); CraftTweaker is optional | No. Recipe Stages and Darkhax's Game Stages are Forge-only. `sdm-gamestages` is an unrelated Fabric stage API with no recipe staging, so nothing to integrate | `com.blamejared.recipestages:RecipeStages:4.0.0.4`, `net.darkhax.gamestages:GameStages-Forge-1.19.2:11.1.4`, `net.darkhax.bookshelf:Bookshelf-Forge-1.19.2:16.3.20` (`https://maven.blamejared.com`) |
| OpenComputers 2 | No. OC2 has 1.18.1/1.18.2 builds and a 1.21.1 line; OC2: Reimagined and OpenComputers CE are 1.20.1 | No | None |

### Flux Networks

Forge: upstream's `FNEnergyCompat`, `FNIntegration` and `FNStrictEnergyHandler` are in `src/forge`, unchanged.
`EnergyCompatUtils` loads the compat by name in upstream's position, after Mekanism's own capability and before Forge
Energy, so Mekanism blocks offer Flux's long-based `IFNEnergyStorage` and read it from Flux blocks. A Flux Plug or Point
then moves more than `Integer.MAX_VALUE` FE per operation. `blacklistFluxNetworks` and `blacklistForge` turn it off as
upstream.

Fabric: Flux Networks for Fabric reads and writes Team Reborn Energy, which the Fabric JAR already offers for every
Mekanism energy block, so no Flux code is needed in the JAR. Flux nests Porting Lib 2.1.1177, which adds its own
Forge-style methods to vanilla classes. Several had the same names and descriptors as methods of this port's Forge API
layer, and the server stopped at start with `IncompatibleClassChangeError: Conflicting default methods:
IForgeBlockEntity.onLoad vs BlockEntityExtensions.onLoad`. The port's methods now have `mekanism$` names on Fabric, so
the two mods run together; the clashes and their resolution are in [20-porting-lib.md](20-porting-lib.md).

`FluxNetworksGameTests` (both loaders) links a Flux Plug beside a charged energy cube and a Flux Point beside an empty
one on a public network, and waits for energy to arrive. `ForgeFluxCapabilityGameTests` (Forge only) extracts
5,000,000,000 FE in one call through the Flux capability of a creative energy cube; Flux for Fabric has no such
capability. Without Flux Networks both check that the integration is off. `-PwithFluxNetworks` installs the mod on
either loader, on Fabric with its nested libraries, and CI's optional-integration runs use it on both.

### Recipe Stages

Recipe Stages stages crafting-table recipes only (`IStagedRecipe` extends `CraftingRecipe`). It cannot stage Mekanism's
own machine recipe types, so the Mekanism blocks it affects are the ones that run crafting recipes:

- QIO crafting windows (QIO dashboard and portable dashboard): upstream behaviour. The result is assembled for anyone
  and only a player with the recipe's stage can take it.
- Formulaic Assemblicator: port addition. A staged recipe is crafted when the block's owner (the player who placed it)
  has the stage. An online owner goes through Game Stages' own check; an offline owner uses the stage data Game Stages
  keeps for every player who joined since the server started. Without an owner, or for an owner without the stage, Recipe
  Stages' own rules decide, so `setContainerStage`/`setPackageStage` for Mekanism's crafting container still work. Upstream
  left the assemblicator to those rules alone; Recipe Stages finds no player in its grid and never crafted staged recipes
  there.

The Recipe Stages and Game Stages types are in `src/forge` behind `RecipeStagesCompat`. The Fabric JAR has no bridge, and
every call behaves as if no recipe were staged.

`ForgeRecipeStagesGameTests` adds a staged recipe to the recipe manager, as Recipe Stages' `recipestages:stage` serializer
would from a data pack, and checks the assemblicator with no owner, an owner without the stage and an owner with it, and
the QIO window's assemble and stage checks. These tests are Forge-only and listed in `gametest-loader-only.txt`.
`-PwithRecipeStages` installs Recipe Stages, Game Stages and Bookshelf on Forge.

### OpenComputers 2

Not ported. No OpenComputers 2 build, or maintained fork, exists for 1.19.2, so there is no API to compile against.
Upstream's OC2 classes were compiled against the 1.18.2 jar and could not load on 1.19.2 either. `OC2Loaded` stays false.
Machines are scriptable through ComputerCraft (CC: Tweaked on Forge, CC: Restitched on Fabric), which uses upstream's
generic computer layer; see [13-computercraft.md](13-computercraft.md).
