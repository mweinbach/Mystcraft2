/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * What another mod sees of our blocks, through each loader's own item, fluid and energy APIs: Forge capabilities on
 * Forge, the Transfer API and Team Reborn Energy on Fabric. Amounts are in items, millibuckets and FE (1 E per FE).
 */
public interface LoaderApis {

    /** Insert the stack and return how many items were accepted. */
    long insertItems(ServerLevel level, BlockPos position, Direction side, ItemStack stack);

    /** Insert the fluid and return how many millibuckets were accepted. */
    long insertFluid(ServerLevel level, BlockPos position, Direction side, Fluid fluid, int millibuckets);

    /** Extract up to the given energy and return how much was extracted. */
    long extractEnergy(ServerLevel level, BlockPos position, Direction side, long maxAmount);
}
