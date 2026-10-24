package ports.gridwright.gametest.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.gridwright.gametest.TestPlatform;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Team Reborn Energy and the Fabric Transfer API, plus a test-only energy sink on vanilla barrels. */
@SuppressWarnings("UnstableApiUsage")
final class FabricTestPlatform implements TestPlatform {

    private static final Map<BlockEntity, SimpleEnergyStorage> SINKS = Collections.synchronizedMap(new WeakHashMap<>());

    static void registerEnergySink() {
        EnergyStorage.SIDED.registerForBlockEntity((barrel, side) -> SINKS.computeIfAbsent(barrel,
                key -> new SimpleEnergyStorage(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, 0)), BlockEntityType.BARREL);
    }

    @Override
    public EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        EnergyStorage energy = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (energy == null) throw new GameTestAssertException("Missing energy storage on " + side);
        return new EnergyAccess() {
            @Override public long stored() { return energy.getAmount(); }
            @Override public long capacity() { return energy.getCapacity(); }

            @Override
            public long extract(long amount, boolean simulate) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = energy.extract(amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted;
                }
            }

            @Override
            public long insert(long amount, boolean simulate) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = energy.insert(amount, transaction);
                    if (!simulate) transaction.commit();
                    return inserted;
                }
            }
        };
    }

    @Override
    public ItemAccess items(BlockEntity blockEntity, Direction side) {
        Storage<ItemVariant> found = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (!(found instanceof InventoryStorage items)) throw new GameTestAssertException("Missing item storage on " + side + ": " + found);
        return new ItemAccess() {
            @Override
            public ItemStack insert(ItemStack stack) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = items.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    transaction.commit();
                    ItemStack remainder = stack.copy();
                    remainder.shrink((int) inserted);
                    return remainder;
                }
            }

            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                SingleSlotStorage<ItemVariant> view = items.getSlots().get(slot);
                ItemVariant variant = view.getResource();
                if (variant.isBlank()) return ItemStack.EMPTY;
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = view.extract(variant, amount, transaction);
                    if (!simulate) transaction.commit();
                    return variant.toStack((int) extracted);
                }
            }
        };
    }

    @Override
    public long sunk(BlockEntity barrel) {
        SimpleEnergyStorage sink = SINKS.get(barrel);
        if (sink == null) throw new GameTestAssertException("No test energy sink on " + barrel);
        return sink.amount;
    }
}
