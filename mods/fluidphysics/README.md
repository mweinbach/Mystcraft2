# Fluid Physics

A small **Fabric-only** library for Minecraft 1.19.2 that gives modded fluids the part of Forge's
`FluidType` behavior vanilla only gives to water and lava: swimming, drowning, current pushing, the
fall distance modifier, extinguishing, boat support and path types.

Forge already has `FluidType`; this library is for Fabric ports. It is an original MIT implementation
written from the Forge 1.19.2 `FluidType` contract and the vanilla, Forge and Fabric APIs. No Forge code
or assets are copied. The Forge repository and the `1.19.2-43.5.0` tag are recorded in
`port.properties` as the behavioral reference.

## Use

Register the still and flowing fluids with the same properties, during mod initialization:

```java
FluidPhysics.register(stillFluid, flowingFluid, FluidProperties.create()
        .canSwim(true)
        .canDrown(true)
        .canPushEntity(true)
        .supportsBoating(true)
        .fallDistanceModifier(0.4F)
        .canExtinguish(true)
        .pathType(BlockPathTypes.WATER));
```

The library mirrors a subset of Forge 1.19.2 `FluidType.Properties`. For the properties below, the names,
builder and defaults follow Forge; the properties Forge has that are not listed here are not mirrored (see
[Limits](#limits)).

| Property | Default | Effect |
| --- | --- | --- |
| `motionScale` | `0.014` | Strength of the current push. |
| `canPushEntity` | `true` | Whether the current pushes entities. |
| `canSwim` | `true` | Whether entities can take the swimming pose. |
| `canDrown` | `true` | Whether entities with their eyes in the fluid lose air. |
| `fallDistanceModifier` | `0.5` | Fall distance multiplier each tick in the fluid. |
| `canExtinguish` | `false` | Whether burning entities in the fluid are put out, as in water. |
| `supportsBoating` | `false` | Whether boats float on the fluid. |
| `pathType` | `BlockPathTypes.WATER` | Path type of the fluid block; `null` leaves it to vanilla. |
| `adjacentPathType` | `BlockPathTypes.WATER_BORDER` | Path type of walkable blocks next to the fluid; `null` leaves them to vanilla. |

Every registered fluid, whatever its properties, also gives living entities water-like travel and lets them
swim up when jumping, as Forge does for any non-lava fluid type.

Registration copies the properties, so mutating the builder afterwards does not change the registered
fluid. Register both a still and a flowing fluid; a `FluidState` lookup uses its `Fluid` instance.

The library's four mixins apply the behavior on both client and dedicated server. Registering only a still
fluid covers worlds where only that instance exists, but registering the flowing form too is required for
normal fluid spread and for states whose `getType()` is the flowing fluid.

The mixins use MixinExtras `@WrapOperation` and `@ModifyExpressionValue` instead of redirects: vanilla's
check still runs and the registered-fluid result is combined with it, so other mods that wrap the same calls
can load beside this one. Fabric Loader 0.15 and later bundle and initialise MixinExtras; the library
requires Loader 0.16.14 and Fabric API 0.77.0. When no fluid is registered every hook returns at once, and
the per-entity fluid scan skips entities whose bounding box touches an unloaded chunk, as vanilla does.

## GameTests

`./scripts/gradle.sh -p mods/fluidphysics runGametest` runs a development-only test mod with two test
fluids: one fully swimmable fluid that also extinguishes, and one with all motion behavior, extinguishing and
the adjacent path type disabled. The tests cover drown/no-drown, swim pose, water-like travel and swimming
up, push/no-push for mobs and item entities, boats and boat passengers, extinguishing, path types, adjacent
path types and fall distance modifiers, plus vanilla water regressions.

## Limits

- No Forge `FluidType` registry or query API is exposed under the Forge name; this is a Fabric API.
- Vanilla water and lava cannot be registered; they keep their vanilla physics.
- It only changes entity, boat and pathfinding behavior for registered fluids. Fluid flow, rendering,
  bucket items, transfer, fog and underwater overlays remain the owning mod's responsibility.
- Item entities are pushed and get the fall distance modifier, but do not bob because Forge's
  `FluidType#setItemMovement` hook is not reproduced.
- These `FluidType.Properties` are not mirrored: `canHydrate` (farmland and other moisture checks),
  `canConvertToSource` (use your `FlowingFluid#canConvertToSource`), the bucket fill/empty and other
  `sound(...)` entries (use your bucket and `Fluid#getPickupSound`), `density`, `temperature`, `viscosity`,
  `lightLevel`, `rarity` and `descriptionId`.
- `canExtinguish` only puts out entities. Forge's block-context overload, `FluidType#canExtinguish(FluidState,
  BlockGetter, BlockPos)`, is not reproduced.
- Properties are constants per fluid. Forge's context-sensitive `FluidType` overrides (per entity, boat or
  position) and its custom movement hooks (`move`, `jump` and the like) are not reproduced.
- No claims of visual or pixel parity with Forge.

## Build

```sh
./scripts/gradle.sh -p mods/fluidphysics build
./scripts/gradle.sh -p mods/fluidphysics test
./scripts/gradle.sh -p mods/fluidphysics runGametest
```

Install the built `fluidphysics-fabric-1.19.2-*.jar` on clients and servers; there is no Forge variant.
