/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.util.Collection;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import ports.fluidphysics.FluidPhysics;
import ports.fluidphysics.FluidProperties;
import ports.mekanism.lib.fluids.FluidType;
import ports.mekanism.lib.fluids.ForgeFlowingFluid;

/**
 * Gives the port's fluids the entity physics Forge applies from their {@link FluidType}: swimming, drowning, current
 * pushing, the fall distance modifier, extinguishing, boating and the path types. Vanilla Fabric only does this for
 * water and lava; the bundled Fluid Physics library does it for registered fluids. The values are read from each fluid's type, the same
 * properties the Forge build hands to Forge.
 */
public final class FabricFluidPhysics {

    private FabricFluidPhysics() {
    }

    /**
     * Registers every {@link ForgeFlowingFluid} source the given mods put in the fluid registry, with its flowing form.
     * Runs after registration, once every fluid type exists.
     */
    static void registerAll(Collection<String> modIds) {
        for (Fluid fluid : Registry.FLUID) {
            if (fluid instanceof ForgeFlowingFluid.Source source && modIds.contains(Registry.FLUID.getKey(fluid).getNamespace())) {
                register(source);
            }
        }
    }

    public static void register(ForgeFlowingFluid.Source source) {
        FluidPhysics.register(source, source.getFlowing(), properties(source.getFluidType()));
    }

    /**
     * The library's view of a fluid type. Forge passes the entity, boat or path context to these methods so a type can
     * vary them; Mekanism's types do not override them, and the library only takes constants, so no context is given.
     */
    public static FluidProperties properties(FluidType type) {
        return FluidProperties.create()
              .motionScale(type.motionScale(null))
              .canPushEntity(type.canPushEntity(null))
              .canSwim(type.canSwim(null))
              .canDrown(type.canDrownIn(null))
              .fallDistanceModifier(type.getFallDistanceModifier(null))
              .supportsBoating(type.supportsBoating(null))
              .canExtinguish(type.canExtinguish((Entity) null))
              .pathType(type.getBlockPathType(null, null, null, null, false))
              .adjacentPathType(type.getAdjacentBlockPathType(null, null, null, null, BlockPathTypes.WALKABLE));
    }
}
