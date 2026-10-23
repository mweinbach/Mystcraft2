package net.mystcraft.fabric.fluid;

import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.mystcraft.integration.registry.InkRegistry;
import ports.fluidphysics.FluidPhysics;
import ports.fluidphysics.FluidProperties;

/**
 * Black ink's entity physics on Fabric through the bundled Fluid Physics library. The values are those of
 * {@code ForgeInkFluidType}: swimmable, drownable and boatable, with every other property at Forge's
 * {@code FluidType.Properties} default (spelled out so a change to either side is visible here).
 */
public final class FabricInkPhysics {
    public static FluidProperties properties() {
        return FluidProperties.create()
                .canSwim(true)
                .canDrown(true)
                .supportsBoating(true)
                .canPushEntity(true)
                .motionScale(0.014D)
                .fallDistanceModifier(0.5F)
                .canExtinguish(false)
                .pathType(BlockPathTypes.WATER)
                .adjacentPathType(BlockPathTypes.WATER_BORDER);
    }
    public static void initialize() {
        FluidPhysics.register(InkRegistry.BLACK_INK.get(), InkRegistry.FLOWING_BLACK_INK.get(), properties());
    }
    private FabricInkPhysics() {}
}
