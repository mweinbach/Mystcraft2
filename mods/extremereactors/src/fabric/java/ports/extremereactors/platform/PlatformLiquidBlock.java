/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * Fabric side of the liquid block base. The Fabric entrypoint populates the fluid registry before the block
 * registry, so the fluid already exists when the block is created.
 * The Forge source set has a class with the same name and API.
 */
public class PlatformLiquidBlock extends LiquidBlock {

    public PlatformLiquidBlock(final Supplier<? extends FlowingFluid> fluid, final Properties properties) {
        super(fluid.get(), properties);
    }
}
