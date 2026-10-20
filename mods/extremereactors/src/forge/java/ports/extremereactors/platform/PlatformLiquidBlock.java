/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * Forge side of the liquid block base. Forge registers blocks before fluids, so the fluid is resolved lazily.
 * The Fabric source set has a class with the same name and API.
 */
public class PlatformLiquidBlock extends LiquidBlock {

    public PlatformLiquidBlock(final Supplier<? extends FlowingFluid> fluid, final Properties properties) {
        super(fluid, properties);
    }
}
