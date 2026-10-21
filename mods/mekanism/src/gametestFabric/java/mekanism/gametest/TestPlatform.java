/*
 * Mekanism 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.items.ItemHandlerHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * What the shared tests do through the loader's APIs: Team Reborn Energy and the Transfer API.
 */
public final class TestPlatform {

    private TestPlatform() {
    }

    public static long insertEnergy(BlockEntity blockEntity, Direction side, long amount) {
        EnergyStorage storage = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), side);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    public static long insertItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), side);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    public static int insertThroughItemCapability(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .map(handler -> stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount())
              .orElse(0);
    }
}
