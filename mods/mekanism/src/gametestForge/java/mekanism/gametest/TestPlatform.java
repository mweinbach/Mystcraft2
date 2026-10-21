/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * What the shared tests do through the loader's APIs: Forge Energy and Forge's item handler capability.
 */
public final class TestPlatform {

    private TestPlatform() {
    }

    public static long insertEnergy(BlockEntity blockEntity, Direction side, long amount) {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY, side).map(storage -> storage.receiveEnergy((int) amount, false)).orElse(0);
    }

    public static long insertItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return insertThroughItemCapability(blockEntity, side, stack);
    }

    public static int insertThroughItemCapability(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .map(handler -> stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount())
              .orElse(0);
    }
}
