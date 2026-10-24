## Porting Lib (Create, Flux Networks and other Fabric mods)

Porting Lib is the Forge-to-Fabric layer Create for Fabric, Flux Networks for Fabric and many other Fabric mods nest.
Like this port's Forge API layer (`ports.mekanism.lib`), it adds Forge-shaped methods to vanilla classes and patches
vanilla code with mixins. Until this change the two layers added methods with the same names and descriptors to the
same classes, and Mekanism Fabric could not run in a pack with any Porting Lib mod: the server stopped with
`IncompatibleClassChangeError: Conflicting default methods: IForgeBlockEntity.onLoad vs BlockEntityExtensions.onLoad`.
Where only one side's method was an interface default, the JVM picked the other side's method silently, so one layer's
hooks ran the other layer's code.

On Fabric the port now names those methods `mekanism$<name>`: in the API layer, in its mixins and, through the build's
source rewrite (`portingLibClashes` in `fabric/build.gradle`), in the shared upstream sources. Each layer's mixins call
only their own methods, so every hook has one caller per layer: Porting Lib calls Porting Lib's `onLoad` on every block
entity and the port calls `mekanism$onLoad`, which Mekanism's block entities override. Without Porting Lib the port
behaves as before; only the names in the Fabric jar changed. The Forge jar and the shared sources are unchanged.

| Vanilla class | Method | What clashed with Porting Lib 2.1.1177 and 2.1.1453 | Port's method on Fabric |
| --- | --- | --- | --- |
| `BlockEntity` | `onLoad()`, `invalidateCaps()` | Both interface defaults; the JVM refused the call | `mekanism$onLoad`, `mekanism$invalidateCaps` |
| `BlockEntity`, `ItemStack`, `Entity` (and `AbstractMinecart`) | `serializeNBT()`, `deserializeNBT(CompoundTag)` and their `Tag` bridges | Bridges: both interface defaults. `CompoundTag` forms: Porting Lib merges its own into `BlockEntity` and `ItemStack` | `mekanism$serializeNBT`, `mekanism$deserializeNBT`, on every `INBTSerializable` |
| `Item` (and `ElytraItem`) | `getCreatorModId(ItemStack)`, `getDefaultTooltipHideFlags(ItemStack)`, `onBlockStartBreak(ItemStack, BlockPos, Player)` | Both interface defaults | `mekanism$` + name |
| `Block` | `getExpDrop(BlockState, LevelReader, RandomSource, BlockPos, int, int)` | Porting Lib merges its own into `DropExperienceBlock`, `RedStoneOreBlock`, the sculk blocks and `SpawnerBlock`, overriding the port's default, which returns 0 for vanilla blocks; the port's break hook would then have dropped experience for vanilla ores on top of what vanilla and Porting Lib drop | `mekanism$getExpDrop` (also `BlockState`'s) |
| `Level`, `ServerLevel` | `addFreshBlockEntities(Collection)` | The port's mixin method replaced Porting Lib's default, so Porting Lib's chunk-load path ran the port's queue | `mekanism$addFreshBlockEntities` |
| `Tier`, `Tiers` | `getTag()` | Both interface defaults; Porting Lib also merges one into `Tiers` | `mekanism$getTag` (Mekanism Tools' materials) |
| `Slot` | `getSlotIndex()`, `setBackground(ResourceLocation, ResourceLocation)` | `getSlotIndex`: both interface defaults. `setBackground`: both mixins merge one; one replaced the other | `mekanism$getSlotIndex`, `mekanism$setBackground` |
| `MobEffectInstance` | `isCurativeItem(ItemStack)` | Both interface defaults | `mekanism$isCurativeItem` |
| `Sheep`, `SnowGolem`, `MushroomCow` | `isShearable(...)`, `onSheared(...)` (`IForgeShearable`) | Both mixins merge them; one replaced the other | `mekanism$isShearable`, `mekanism$onSheared` |
| `Vector3f` (client) | `setX`, `setY`, `setZ` | Both interface defaults | `mekanism$setX` and so on |
| `Transformation` (client) | `isIdentity()`, `rotateTransform(Direction)` | `isIdentity`: both interface defaults. `rotateTransform`: Porting Lib merges its own | `mekanism$isIdentity`, `mekanism$rotateTransform` |
| `VertexConsumer` (client) | `putBulkData(Pose, BakedQuad, float, float, float, float, int, int, boolean)` | Both interface defaults | `mekanism$putBulkData`; vanilla's overloads keep their names |
| `TerrainParticle` (client) | `updateSprite(BlockState, BlockPos)` | Porting Lib merges its own | `mekanism$updateSprite` |

`Entity.invalidateCaps` and `BlockState.getExpDrop` do not clash but are renamed with the methods that do, so the rewrite
renames every occurrence of a name. `setBackground`, `getTag`, `setX`/`setY`/`setZ` and `putBulkData` also name other
methods in the shared sources (Mekanism's text fields, `ItemStack.getTag`, `MutableBlockPos.setX`, vanilla's
`putBulkData`), so those rules apply only to the slot classes, Mekanism Tools' materials, `RenderResizableCuboid` and
calls ending in the `readExistingColor` argument. A missed or extra rename fails compilation.

Both layers also patch `ClipContext`'s constructor so a null entity gets `CollisionContext.empty()`. Mixin allows one
`@Redirect` per call, and Porting Lib's was applied first, so the port's `ClipContextMixin` failed and the server
crashed. It now uses MixinExtras' `@WrapOperation`, which chains with a redirect.

### What changes when Porting Lib is installed

Each layer runs its own hooks, so Porting Lib's callers no longer reach Mekanism's overrides under Forge's names. A
Porting Lib mod that calls `onBlockStartBreak` on a Meka-Tool, or `onLoad` on a Mekanism block entity, gets Porting
Lib's default; the port still calls Mekanism's own methods from its mixins. Mekanism's API types on Fabric
(`IChemicalTank`, `IEnergyContainer`, inventory slots and so on) name their NBT methods `mekanism$serializeNBT` and
`mekanism$deserializeNBT`. A Fabric addon compiled against the Mekanism Fabric jar uses those names; Extreme Reactors'
Mekanism integration does not call them.

### Checks

- `checkPortingLibClashes`, part of `check` and so of `build` in `fabric/`, runs `scripts/fabric_clashes.py` on the
  built core and module jars against Flux Networks 1.19.2-7.2.1.16 (Porting Lib 2.1.1177) and Create for Fabric 0.5.1-i
  build 1630 (Porting Lib 2.1.1453, the newest 1.19.2 Create), nested jars included. It fails on a method both sides add
  to related vanilla classes, on two redirects of one call and on an overwrite of a method the other side patches at a
  call. The GameTest server loads no client classes, so this is the only check of the client rows above.
- `-PwithPortingLib` runs the Fabric GameTests with Porting Lib 2.1.1453 as Create nests it, without Create, which
  registers GameTests of its own. `-PwithFluxNetworks` runs them with Flux Networks and its Porting Lib 2.1.1177, so
  `FluxNetworksGameTests` takes its Flux-present branch on Fabric too: a Flux Plug and Point carry energy between two
  Mekanism energy cubes over Team Reborn Energy. CI runs both, in the optional-integration step and a Porting Lib step;
  the Porting Lib step adds `-PwithCraftTweaker`, so the CraftTweaker GameTests also run beside Porting Lib.

Porting Lib's serialization_hooks reads a string at the head of every `Ingredient.fromNetwork` to look for its own
ingredient ids and rewinds otherwise, but it catches only `DecoderException`. CraftTweaker's IngredientExtensionAPI
starts each ingredient with the int `601817315`, whose first byte reads as a 35-byte string, so a buffer that ends less
than 35 bytes after such an ingredient throws `IndexOutOfBoundsException`. This is between those two libraries, not
Mekanism, and a recipe packet normally has more data after an ingredient. `CraftTweakerGameTests` round-trips
ingredients with data after them and checks that reading stops at the ingredient's end.

Other ports' Fabric variants (BuildCraft, Extreme Reactors, JurassiCraft, Advanced Genetics, Mystcraft, ICBM) add no
Forge-shaped methods to vanilla classes; `fabric_clashes.py` finds no clashes between their jars and either Porting
Lib version.
