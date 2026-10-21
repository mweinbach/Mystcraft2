/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.energy.IEnergyStorage;
import team.reborn.energy.api.EnergyStorage;

/**
 * Bridges Forge Energy with Team Reborn Energy at 1 E per FE, so Mekanism's configured Joule to FE rate applies to both.
 * Block entities are registered directly. Items that expose an energy capability are registered as {@link EnergyStorage#ITEM}.
 */
final class FabricEnergy {

    private FabricEnergy() {
    }

    static void registerBlockEntityLookups(BlockEntityType<?>... types) {
        EnergyStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
              .<EnergyStorage>map(HandlerEnergyStorage::new).orElse(null), types);
    }

    /**
     * Exposes the port's own energy items. Other mods' items are left to their own providers; looking them up here would
     * call back into {@link #foreignEnergy(ContainerItemContext)}.
     */
    static void registerItemLookups(Set<String> namespaces) {
        Set<String> mods = Set.copyOf(namespaces);
        EnergyStorage.ITEM.registerFallback((stack, context) -> owned(mods, stack)
              && stack.getCapability(ForgeCapabilities.ENERGY).isPresent() ? new ItemEnergyStorage(context) : null);
    }

    private static boolean owned(Set<String> namespaces, ItemStack stack) {
        return !stack.isEmpty() && namespaces.contains(Registry.ITEM.getKey(stack.getItem()).getNamespace());
    }

    static LazyOptional<IEnergyStorage> foreignEnergy(BlockEntity blockEntity, Direction side) {
        EnergyStorage storage = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        return storage == null ? LazyOptional.empty() : LazyOptional.of(() -> new StorageEnergyHandler(storage));
    }

    static LazyOptional<IEnergyStorage> foreignEnergy(ContainerItemContext context) {
        EnergyStorage storage = context.find(EnergyStorage.ITEM);
        return storage == null ? LazyOptional.empty() : LazyOptional.of(() -> new StorageEnergyHandler(storage));
    }

    /**
     * A Forge Energy storage as a Team Reborn Energy storage. What a transaction moved is applied when the outermost
     * transaction commits.
     */
    private static final class HandlerEnergyStorage extends SnapshotParticipant<Long> implements EnergyStorage {

        private final IEnergyStorage handler;
        private long pending;

        HandlerEnergyStorage(IEnergyStorage handler) {
            this.handler = handler;
        }

        @Override
        public boolean supportsInsertion() {
            return handler.canReceive();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (pending < 0 || maxAmount <= 0 || !handler.canReceive()) {
                return 0;
            }
            long accepted = Math.min(maxAmount, handler.receiveEnergy(FabricTransfer.clampToInt(pending + maxAmount), true) - pending);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending += accepted;
            return accepted;
        }

        @Override
        public boolean supportsExtraction() {
            return handler.canExtract();
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (pending > 0 || maxAmount <= 0 || !handler.canExtract()) {
                return 0;
            }
            long already = -pending;
            long accepted = Math.min(maxAmount, handler.extractEnergy(FabricTransfer.clampToInt(already + maxAmount), true) - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending -= accepted;
            return accepted;
        }

        @Override
        public long getAmount() {
            return Math.max(0, handler.getEnergyStored() + pending);
        }

        @Override
        public long getCapacity() {
            return handler.getMaxEnergyStored();
        }

        @Override
        protected Long createSnapshot() {
            return pending;
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            long committed = pending;
            pending = 0;
            if (committed > 0) {
                handler.receiveEnergy(FabricTransfer.clampToInt(committed), false);
            } else if (committed < 0) {
                handler.extractEnergy(FabricTransfer.clampToInt(-committed), false);
            }
        }
    }

    /**
     * One of the port's energy items as a Team Reborn Energy storage. Forge applies the change immediately, so it is
     * applied to a copy of the stack and exchanged into the context; aborting the transaction drops the copy.
     */
    private static final class ItemEnergyStorage implements EnergyStorage {

        private final ContainerItemContext context;

        private ItemEnergyStorage(ContainerItemContext context) {
            this.context = context;
        }

        @Override
        public boolean supportsInsertion() {
            Opened opened = open();
            return opened != null && opened.handler().canReceive();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            Opened opened = open();
            if (opened == null || maxAmount <= 0 || !opened.handler().canReceive()) {
                return 0;
            }
            int accepted = opened.handler().receiveEnergy(FabricTransfer.clampToInt(maxAmount), false);
            return accepted > 0 && FabricTransfer.commitItem(context, opened.stack(), transaction) ? accepted : 0;
        }

        @Override
        public boolean supportsExtraction() {
            Opened opened = open();
            return opened != null && opened.handler().canExtract();
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            Opened opened = open();
            if (opened == null || maxAmount <= 0 || !opened.handler().canExtract()) {
                return 0;
            }
            int accepted = opened.handler().extractEnergy(FabricTransfer.clampToInt(maxAmount), false);
            return accepted > 0 && FabricTransfer.commitItem(context, opened.stack(), transaction) ? accepted : 0;
        }

        @Override
        public long getAmount() {
            Opened opened = open();
            return opened == null ? 0 : opened.handler().getEnergyStored();
        }

        @Override
        public long getCapacity() {
            Opened opened = open();
            return opened == null ? 0 : opened.handler().getMaxEnergyStored();
        }

        private Opened open() {
            if (context.getItemVariant().isBlank()) {
                return null;
            }
            int count = FabricTransfer.clampToInt(context.getAmount());
            if (count <= 0) {
                return null;
            }
            ItemStack stack = context.getItemVariant().toStack(count);
            IEnergyStorage handler = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            return handler == null ? null : new Opened(stack, handler);
        }

        private record Opened(ItemStack stack, IEnergyStorage handler) {
        }
    }

    private record StorageEnergyHandler(EnergyStorage storage) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return maxReceive <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> storage.insert(maxReceive, transaction));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> storage.extract(maxExtract, transaction));
        }

        @Override
        public int getEnergyStored() {
            return FabricTransfer.clampToInt(storage.getAmount());
        }

        @Override
        public int getMaxEnergyStored() {
            return FabricTransfer.clampToInt(storage.getCapacity());
        }

        @Override
        public boolean canExtract() {
            return storage.supportsExtraction();
        }

        @Override
        public boolean canReceive() {
            return storage.supportsInsertion();
        }
    }
}
