## Fabric: entity physics in Mekanism fluids

Forge applies each fluid's `FluidType` to entities: swimming, drowning, current pushing, the fall distance modifier,
boating and the path type. Vanilla Fabric only does this for water and lava. The Fabric build's `FluidType` mirror
(`ports.mekanism.lib.fluids`) carries the same values, but no mixin in `ports.mekanism.fabric.mixin` applies them to
swimming, drowning, pushing, falling, boats or path types, and `IForgeEntity`'s `isInFluidType`, `isEyeInFluidType`
and `getMaxHeightFluidType` used to answer only for water and lava. On its own, an entity walked through brine, heavy
water or any other Mekanism fluid as if it were air.

The Fabric JAR now bundles the workspace's [Fluid Physics](../../fluidphysics/README.md) library (mod id
`fluidphysics`, MIT) jar-in-jar and depends on it. After registration, `ports.mekanism.fabric.FabricFluidPhysics`
registers every `ForgeFlowingFluid` source of the loaded Mekanism modules, with its flowing form, using the values of
its `FluidType`: the properties the shared `FluidDeferredRegister` passes to Forge on the Forge build. That covers the
core fluids and Generators' bioethanol, deuterium, fusion fuel and tritium; Tools and Additions register no fluids.

| Property | Every Mekanism fluid (Forge `FluidType.Properties` defaults) |
| --- | --- |
| `canSwim` | true |
| `canDrown` | true |
| `canPushEntity` | true |
| `motionScale` | 0.014 |
| `fallDistanceModifier` | 0.5 |
| `supportsBoating` | false |
| `pathType` | `WATER` |

The mirror's `FluidType.Properties` defaulted `canDrown` to false. Forge 1.19.2 defaults it to true, so the mirror now
does too; lava and the empty type still set it to false. `IForgeEntity`'s per-type queries measure non-vanilla fluids
in the entity's bounding box, so shared checks such as the hydrostatic repulsor's swim boost see Mekanism fluids.

Tests: `FluidEntityGameTests` runs on both loaders. A cow under brine loses air and, sprinting, takes the swimming
pose; its tallest fluid type, in-type and eye-in-type answers are brine's; flowing brine pushes a zombie; brine scales
the fall distance by 0.5; and brine has the `WATER` path type. `FabricFluidPhysicsGameTests` is Fabric only: every
core and Generators fluid, still and flowing, is registered with its type's values, and a development-only fluid whose
type turns swimming, drowning and pushing off does not drown, swim or push and has the path type its type gives.

Limits, from the library: Forge's `adjacentPathType`, `canExtinguish`, `canHydrate` and item bobbing are not
applied on Fabric, and the path type does not depend on whether the block can be fluid-logged.
