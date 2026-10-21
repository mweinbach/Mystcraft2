/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Forge's {@code LiquidBlock.getFluid}, injected into {@link LiquidBlock} on Fabric.
 */
public interface IForgeLiquidBlock {

    default FlowingFluid getFluid() {
        return ((LiquidBlock) this).fluid;
    }
}
