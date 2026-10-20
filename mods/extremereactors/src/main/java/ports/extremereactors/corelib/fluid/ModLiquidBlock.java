/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import net.minecraft.world.level.material.FlowingFluid;
import ports.extremereactors.platform.PlatformLiquidBlock;

import java.util.function.Supplier;

/**
 * A liquid block that can be declared before its fluid exists.
 */
public class ModLiquidBlock
        extends PlatformLiquidBlock {

    public ModLiquidBlock(final Supplier<? extends FlowingFluid> fluid, final Properties properties) {
        super(fluid, properties);
    }
}
