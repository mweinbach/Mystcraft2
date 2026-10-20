/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.function.Supplier;

/**
 * A liquid block that can be declared before its fluid exists. Both loaders populate the fluid registry before the
 * block registry, so the fluid is resolved when the block is created.
 */
public class ModLiquidBlock
        extends LiquidBlock {

    public ModLiquidBlock(final Supplier<? extends FlowingFluid> fluid, final Properties properties) {
        super(fluid.get(), properties);
    }
}
