/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import java.util.function.Supplier;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Forge's {@code LiquidBlock(Supplier, Properties)}. The port registers fluids before blocks, so the fluid exists when
 * the block is created.
 */
public class ForgeLiquidBlock extends LiquidBlock {

    public ForgeLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }
}
