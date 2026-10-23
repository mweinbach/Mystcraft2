# Loader parity and classic gaps

Mystcraft 1.19.2 ships one gameplay implementation with two thin loader
adapters. Shared code reaches loader services only through `GameplayPlatform`,
`RegistryBridge`, `AgeRuntime` and the network transport interfaces under
`net.mystcraft.integration`, so gameplay, registries, recipes, assets and Age
data remain common. This file records how those services are provided on Forge
and Fabric, and which gaps against classic Mystcraft 1.12.2 the repository
currently documents. The classic reference is Mystcraft-Legacy revision
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae` (0.13.7.06); this is a development
port, not a claim of complete classic parity. The full feature inventory is
[docs/classic-parity.md](docs/classic-parity.md). Install the matching loader's
JAR on the server and every client: cross-loader multiplayer and world migration
are not supported.

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | All gameplay, registry definitions, recipes, loot, worldgen, assets, lang, Age NBT and generation plans. Also the shared `integration` contracts (`GameplayPlatform`, `RegistryBridge`, `AgeRuntime`, network payloads), the `META-INF/mods.toml` template and `pack.mcmeta`. |
| `src/forge` | `Mystcraft` Forge entrypoint, `ForgeRegistryBridge`, `ForgeGameplayPlatform`, `ForgeAgeBackend`, Forge network transports, ink capabilities/fluid type, event subscribers, client subscribers, mixins and `mystcraft.forge.mixins.json`. |
| `src/fabric` | `FabricMystcraft` and `FabricMystcraftClient` entrypoints, `FabricRegistryBridge`, `FabricGameplayBackend`, `FabricAgeBackend`, Fabric network transports, Transfer API wiring, mixins, `mystcraft.fabric.mixins.json` and `fabric.mod.json`. There is no access widener; private server state is reached through accessor mixins. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Development-only EMC GameTests and their `EmcAudit` body. Forge runs them through `forge.enabledGameTestNamespaces=mystcraft`; Fabric uses the separate `mystcraft_gametest` mod. Neither the classes nor the Fabric descriptor enter the production JARs. |
| `src/test`, `src/smoke*` | Shared unit tests and the optional server smoke helper. The smoke helper is a separate artifact enabled by `-PenableSmokeTests` and is not packaged. |
| `fabric/` | The isolated Loom build that produces the Fabric JAR; the root Gradle build produces the Forge JAR. |

`scripts/check_loader_parity.py` checks both built JARs for descriptor
isolation, mod ID and version, wrong-loader class references, byte-identical
shared assets and data, both mixin refmaps, and GameTest class and fixture
exclusion.

## Loader services

| Service | Forge | Fabric |
| --- | --- | --- |
| Registration | `ForgeRegistryBridge` wraps a `DeferredRegister` per registry (items, blocks, entity and block-entity types, fluids and fluid types, features, loot functions, POIs, professions, biome sources, chunk generators, particles, structure pool element types, menus, sounds and recipe serializers) and attaches everything to the mod event bus. Forge separately registers the exploration biome-modifier codec. `MystcraftBootstrap` declares the content once for both loaders. | `FabricRegistryBridge` registers the same shared declarations directly with `Registry.register`; menus use `ExtendedScreenHandlerType`, POIs use `PointOfInterestHelper`, and the creative tab uses `FabricItemGroupBuilder`. |
| Menu opening and entity transfer | `NetworkHooks.openScreen`, `NetworkHooks.getEntitySpawningPacket`, and `Entity.changeDimension` with an `ITeleporter` that confirms destination insertion before the source entity is removed. | `ExtendedScreenHandlerFactory`/`player.openMenu`, `ClientboundAddEntityPacket`, and `FabricDimensions.teleport`. Shared `GameplayPlatform` handles players and same-level movement before either backend. |
| Networking | Three `SimpleChannel`s: `mystcraft:linking` (protocol 3), `mystcraft:menus` (protocol 1) and `mystcraft:age_visuals` (protocol 3). Payloads and session state stay in shared `LinkingNetwork`, `MenuActions` and `AgeVisualNetwork`; client handlers are reached through `DistExecutor`. | Identifier channels `mystcraft:open_book_v3`, `mystcraft:link_v3`, `mystcraft:book_presentation_v3`, `mystcraft:menu_action_v1` and `mystcraft:age_visuals_v3`. Same payload records and server-side handling, with `ServerPlayNetworking`/`ClientPlayNetworking`. |
| Configuration | Shared JSON files loaded by shared code, generated on first start: `config/mystcraft-crafting.json`, `config/mystcraft-linking.json`, `config/mystcraft-symbols.json`, `config/mystcraft-instability.json` and `config/mystcraft-village.json` on the server, plus `config/mystcraft-presentation.json` on the client. There is no Forge `ModConfigSpec` file. | Same files and parsers. The player-facing `config/mystcraft-presentation.json` is read by the client entrypoint, and the server-owned rules are read from the world's `config/` directory. |
| Capabilities and transfer | `AttachCapabilitiesEvent` supplies `ForgeCapabilities.FLUID_HANDLER` for the ink vial and inkwell and `ForgeCapabilities.ITEM_HANDLER` (`InvWrapper`) for the lectern; the ink bucket uses `FluidBucketWrapper`, and `ForgeInkFluidType` provides the Forge render/fog extensions. | `FluidStorage.ITEM` for the ink vial, `FluidStorage.SIDED` for the writing-desk tank and `ItemStorage.SIDED` for the lectern. Transactions snapshot shared tank state and roll back on abort; one millibucket is 81 droplets. |
| Events | `MinecraftForge.EVENT_BUS` and `@Mod.EventBusSubscriber` cover server start/stop, command registration, player login/logout/dimension change/respawn, datapack sync, symbol and village configuration startup, villager trades and entity interaction, ink capability attachment and water-bottle denial. Exploration features use the `mystcraft:exploration` biome modifier. | The equivalent Fabric API callbacks: `ServerLifecycleEvents`, `ServerPlayConnectionEvents`, `ServerEntityWorldChangeEvents`, `ServerPlayerEvents`, `ServerTickEvents`, `END_DATA_PACK_RELOAD`, `CommandRegistrationCallback`, `TradeOfferHelper`, `UseEntityCallback` and `UseItemCallback`; `BiomeModifications` adds the library and crystal features. |
| Client | `@Mod.EventBusSubscriber(value = Dist.CLIENT)` and `FMLClientSetupEvent` register screens, entity and block-entity renderers, particles, block colors, translucent portal layers, the page model predicate and the `mystcraft:age` dimension effects; `DistExecutor` guards client packet handlers. A loader-only `IClientItemExtensions` renderer draws the lectern item, and `ForgeAgeDimensionEffects` overrides the Age sky. | `ClientModInitializer` registers the same screens, renderers, particles, block colors, render layers, page model predicate and dimension effects through Fabric APIs. Client-only mixins handle Age color/lightmap/sky/weather, dynamic pages and the ink fog/camera hooks. |

## Known gaps against 1.12.2

The repository does not claim complete classic parity. These are the specific
limits it records; absence from this list is not evidence that a subsystem
matches 1.12.2.

- This is a development port (`port_status=in-progress`); README and
  [docs/classic-parity.md](docs/classic-parity.md) state that source
  implementation and passing tests do not establish every combination's
  parity.
- 1.12 save migration is not provided, and 1.12 save and config migration are
  not promised. Saves are not binary or save-format compatible with 1.12
  (README, [NOTICE.md](NOTICE.md), [docs/classic-parity.md](docs/classic-parity.md)).
- Original addon/IMC contracts and optional LookingGlass live destination
  cameras are not reproduced (README).
- Modern biome resolution and surfaces, native structure templates, fluid APIs,
  entity physics and rendering retain explicit adapter boundaries. The port
  does not claim pixel-identical 1.12 biome wrapping; natural grass/foliage
  sampling and neighborhood tint blending are modern adapters
  (README, [docs/age-visual-parity.md](docs/age-visual-parity.md)).
- Weather-strength interpolation, lightning positioning and clouds use modern
  native behavior, and the radial sky/fog blend replaces fixed-function fog and
  needs graphical comparison (docs/age-visual-parity.md).
- Shaders and third-party render pipelines can bypass the native hooks;
  Tekxit/Iris compatibility is not established by builds or dedicated-server
  tests (docs/age-visual-parity.md).
- The fluid APIs are loader-native by design: Forge capabilities on Forge,
  Fabric's Transaction-based Transfer API on Fabric. Quantities and accepted
  variants are equivalent, but this is not the 1.12 universal-bucket API
  ([docs/classic-ink-fluid.md](docs/classic-ink-fluid.md)).

## Verification

Run after any change to either adapter or the shared code:

```
./scripts/gradle.sh :mystcraft:build
./scripts/gradle.sh -p mods/mystcraft/fabric build
python3 mods/mystcraft/scripts/check_loader_parity.py
```
