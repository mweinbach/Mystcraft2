# Fluid Physics

A small **Fabric-only** library for Minecraft 1.19.2 that gives modded fluids the part of Forge's
`FluidType` behavior vanilla only gives to water and lava: swimming, drowning, current pushing, the
fall distance modifier, boat support and path types.

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
        .pathType(BlockPathTypes.WATER));
```

The property names, builder and defaults follow Forge 1.19.2 `FluidType.Properties`:

| Property | Default |
| --- | --- |
| `motionScale` | `0.014` |
| `canPushEntity` | `true` |
| `canSwim` | `true` |
| `canDrown` | `true` |
| `fallDistanceModifier` | `0.5` |
| `supportsBoating` | `false` |
| `pathType` | `BlockPathTypes.WATER` |

Registration copies the properties, so mutating the builder afterwards does not change the registered
fluid. Register both a still and a flowing fluid; a `FluidState` lookup uses its `Fluid` instance.

The library's four mixins apply the behavior on both client and dedicated server. Registering only a still
fluid covers worlds where only that instance exists, but registering the flowing form too is required for
normal fluid spread and for states whose `getType()` is the flowing fluid.

## GameTests

`./scripts/gradle.sh -p mods/fluidphysics runGametest` runs a development-only test mod with two test
fluids: one fully swimmable and one with all motion behavior disabled. The tests cover drown/no-drown,
swim pose, push/no-push, boats, path types and fall distance modifiers.

## Limits

- No Forge `FluidType` registry or query API is exposed under the Forge name; this is a Fabric API.
- Vanilla water and lava cannot be registered; they keep their vanilla physics.
- It only changes entity, boat and pathfinding behavior for registered fluids. Fluid flow, rendering,
  bucket items, transfer, fog and underwater overlays remain the owning mod's responsibility.
- Item entities are pushed and get the fall distance modifier, but do not bob because Forge's
  `FluidType#setItemMovement` hook is not reproduced.
- No claims of visual or pixel parity with Forge.

## Build

```sh
./scripts/gradle.sh -p mods/fluidphysics build
./scripts/gradle.sh -p mods/fluidphysics test
./scripts/gradle.sh -p mods/fluidphysics runGametest
```

Install the built `fluidphysics-fabric-1.19.2-*.jar` on clients and servers; there is no Forge variant.
