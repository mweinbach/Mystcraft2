/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.fluids.capability.IFluidHandler;

/**
 * A fluid block that is not a vanilla {@link net.minecraft.world.level.block.LiquidBlock}. No Fabric mod implements
 * it, so on Fabric only Mekanism's own checks see it.
 */
public interface IFluidBlock {

    Fluid getFluid();

    int place(Level level, BlockPos pos, @NotNull FluidStack fluidStack, IFluidHandler.FluidAction action);

    boolean canDrain(Level level, BlockPos pos);

    @NotNull
    FluidStack drain(Level level, BlockPos pos, IFluidHandler.FluidAction action);

    float getFilledPercentage(Level level, BlockPos pos);
}
