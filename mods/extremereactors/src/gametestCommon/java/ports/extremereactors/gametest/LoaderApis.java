/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * What another mod sees of our blocks and items, through each loader's own item, fluid and energy APIs: Forge
 * capabilities on Forge, the Transfer API and Team Reborn Energy on Fabric. Amounts are in items, millibuckets and FE
 * (1 E per FE).
 */
public interface LoaderApis {

    /** Insert the stack and return how many items were accepted. */
    long insertItems(ServerLevel level, BlockPos position, Direction side, ItemStack stack);

    /** Insert the fluid and return how many millibuckets were accepted. */
    long insertFluid(ServerLevel level, BlockPos position, Direction side, Fluid fluid, int millibuckets);

    /** Extract up to the given energy and return how much was extracted. */
    long extractEnergy(ServerLevel level, BlockPos position, Direction side, long maxAmount);

    /** The label players know the loader's energy unit by. */
    String energyUnit();

    /**
     * Drain up to the given millibuckets from a copy of the stack through the loader's item fluid API
     * ({@code IFluidHandlerItem} on Forge, {@code FluidStorage.ITEM} on Fabric).
     *
     * @throws net.minecraft.gametest.framework.GameTestAssertException if the item offers no fluid handler
     */
    ItemFluidTransfer drainItem(ItemStack stack, int millibuckets);

    /**
     * Fill a copy of the stack with up to the given millibuckets of the fluid through the loader's item fluid API.
     *
     * @throws net.minecraft.gametest.framework.GameTestAssertException if the item offers no fluid handler
     */
    ItemFluidTransfer fillItem(ItemStack stack, Fluid fluid, int millibuckets);

    /**
     * The first tank of the Mekanism gas handler exposed at the position, or empty if there is none. The handler is
     * looked up the way Mekanism's own blocks look up their neighbours.
     *
     * @param side the side being accessed, or null for the block's internal view
     */
    Optional<GasTank> mekanismGas(ServerLevel level, BlockPos position, @Nullable Direction side);

    /** Extract up to the given amount of gas through Mekanism and return how much was extracted. */
    long extractMekanismGas(ServerLevel level, BlockPos position, Direction side, long maxAmount);

    /**
     * @param gas the gas id, or null when the tank is empty
     */
    record GasTank(@Nullable ResourceLocation gas, long stored, long capacity) {
    }

    /**
     * The result of moving fluid in or out of an item.
     *
     * @param fluid        the fluid moved, or null if none was
     * @param millibuckets how much fluid was moved
     * @param container    the item left afterwards
     */
    record ItemFluidTransfer(@Nullable Fluid fluid, long millibuckets, ItemStack container) {
    }
}
