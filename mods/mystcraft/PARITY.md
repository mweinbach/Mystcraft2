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
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Development-only GameTests. Shared bodies (`EmcAudit`, `MixinCompatibilityTests`, `InkGameplayTests`) and the `mystcraft:gametest/floor` template (`structures/gametest/floor.nbt` for Forge, `gametest/structures/gametest/floor.snbt` for Fabric's GameTest loader) live in `src/gametestCommon`; each loader forwards them with its own adapter (`InkGameplayGameTests` implements the Forge capability or Fabric Transfer/Fluid Physics side). Forge runs them through `forge.enabledGameTestNamespaces=mystcraft`; Fabric uses the separate `mystcraft_gametest` mod. Neither the classes, the template nor the Fabric descriptor enter the production JARs. |
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
| Capabilities and transfer | `AttachCapabilitiesEvent` supplies `ForgeCapabilities.FLUID_HANDLER` for the ink vial and inkwell and `ForgeCapabilities.ITEM_HANDLER` (`InvWrapper`) for the lectern; the ink bucket uses `FluidBucketWrapper`, and `ForgeInkFluidType` provides the Forge render/fog extensions. | `FluidStorage.ITEM` for the ink vial (Fabric API's bucket storage covers the ink bucket), `FluidStorage.SIDED` for the writing-desk tank and `ItemStorage.SIDED` for the lectern. Transactions snapshot shared tank state and roll back on abort; one millibucket is 81 droplets. |
| Events | `MinecraftForge.EVENT_BUS` and `@Mod.EventBusSubscriber` cover server start/stop, command registration, player login/logout/dimension change/respawn, datapack sync, symbol and village configuration startup, villager trades and entity interaction, ink capability attachment and water-bottle denial. Exploration features use the `mystcraft:exploration` biome modifier, which selects the shared `#mystcraft:has_exploration` biome tag. | The equivalent Fabric API callbacks: `ServerLifecycleEvents`, `ServerPlayConnectionEvents`, `ServerEntityWorldChangeEvents`, `ServerPlayerEvents`, `ServerTickEvents`, `END_DATA_PACK_RELOAD`, `CommandRegistrationCallback`, `TradeOfferHelper`, `UseEntityCallback` and `UseItemCallback`; `BiomeModifications` adds the library and crystal features to `BiomeSelectors.tag(#mystcraft:has_exploration)`. |
| Ink entity physics | `ForgeInkFluidType` (`canSwim`, `canDrown`, `supportsBoating`; every other `FluidType.Properties` value at Forge's default: motion scale 0.014, pushes entities, fall distance ×0.5, does not extinguish, path type `WATER`, adjacent path type `WATER_BORDER`). | `FabricInkPhysics` registers still and flowing black ink with the bundled [Fluid Physics](../fluidphysics/README.md) library (`include`d jar-in-jar) using the same values; there are no Mystcraft entity or boat physics mixins. The `inkPhysicsMatchesForgeFluidType` GameTest compares both loaders' values with one shared record. |
| Client | `@Mod.EventBusSubscriber(value = Dist.CLIENT)` and `FMLClientSetupEvent` register screens, entity and block-entity renderers, particles, block colors, translucent portal layers, the page model predicate and the `mystcraft:age` dimension effects; `DistExecutor` guards client packet handlers. A loader-only `IClientItemExtensions` renderer draws the lectern item, and `ForgeAgeDimensionEffects` overrides the Age sky. | `ClientModInitializer` registers the same screens, renderers, particles, block colors, render layers, page model predicate and dimension effects through Fabric APIs. Client-only mixins handle Age color/lightmap/sky/weather, dynamic pages and the ink fog/camera hooks. |

## Known gaps against 1.12.2

The repository does not claim complete classic parity. These are the specific
limits it records; absence from this list is not evidence that a subsystem
matches 1.12.2.

- This is a development port (`port_status=in-progress`); README and
  [docs/classic-parity.md](docs/classic-parity.md) state that source
  implementation and passing tests do not establish every combination's
  parity.
- The craftable [Agewriter's Guide](docs/in-game-guide.md) is a port-specific
  addition, using shared text, original art and the same custom reader on both loaders.
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

### Exploration biome tag

`data/mystcraft/tags/worldgen/biome/has_exploration.json` selects the biomes
that receive the small library and crystal formation features on both loaders.
Its default contents (`#minecraft:is_overworld`, `#minecraft:is_nether`,
`#minecraft:is_end`, `minecraft:the_void`, and the optional `c:in_overworld`,
`c:in_nether`, `c:in_the_end` convention tags) cover all 63 vanilla 1.19.2
biomes, so vanilla worldgen output is unchanged from the earlier every-biome
selection. The features still filter by dimension themselves (overworld and
Mystcraft Ages only). A modded biome that is in none of those tags no longer
receives the features until a datapack adds it; a datapack can also replace
the tag to retarget or disable them. The `explorationFeaturesFollowTheBiomeTag`
GameTest checks on both loaders that every registered biome is tagged and
carries both features.

### Forge-only veto checks

The Forge dedicated-server smoke suite reports 150 checks and Fabric 145
([docs/validation.md](docs/validation.md)). The five extra Forge checks come
from three Forge-only helpers: `ForgeTravelVetoSmoke` (1),
`ForgeEntityVetoSmoke` (3) and `ForgeBlackDecayVetoSmoke` (1). They cancel real
`EntityTravelToDimensionEvent` and `EntityJoinLevelEvent` instances and verify
that Mystcraft rolls back linking-book jumps, item/mob travel and black-decay
falling blocks without losing or duplicating anything. Fabric API 0.77 has no
cancellable dimension-travel or entity-join event, so protection and claim mods
cannot veto Mystcraft linking or decay spawns through an event on Fabric. This
is a known Fabric gap, not a skipped test.

### Other loader differences

- Fluid Physics does not reproduce Forge's `FluidType#setItemMovement`, so item
  entities in black ink are pushed and get the fall distance modifier on Fabric
  but do not bob as they do in Forge's default fluid movement. The earlier
  hand-rolled Fabric mixins made ink count as water everywhere (so it also
  extinguished entities and set `isInWater`); Forge never did, and Fabric now
  matches Forge there.

## Verification

Run after any change to either adapter or the shared code:

```
./scripts/gradle.sh :mystcraft:build
./scripts/gradle.sh -p mods/mystcraft/fabric build
python3 mods/mystcraft/scripts/check_loader_parity.py
./scripts/gradle.sh :mystcraft:runGameTestServer -PwithProjectE
./scripts/gradle.sh -p mods/mystcraft/fabric runGametest -PwithItemAlchemy
```

The parity script also requires the nested MixinExtras and Fluid Physics jars
and the `fluidphysics` dependency in the Fabric descriptor, forbids the removed
ink physics mixins, and checks the shared biome tag, the Forge modifier's tag
selector, the icon and the contact links.
