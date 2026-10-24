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

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Team Reborn Energy and the Fabric Transfer API, plus a test-only energy sink on vanilla barrels. */
@SuppressWarnings("UnstableApiUsage")
final class FabricTestPlatform implements TestPlatform {

    private static final Map<BlockEntity, SimpleEnergyStorage> SINKS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Set<BlockPos> CLAIMED = Collections.synchronizedSet(new HashSet<>());

    /**
     * A stand-in claim mod: refuses breaking at claimed positions, and using a block whose clicked side
     * opens onto a claimed position (Fabric has no place event).
     */
    static void registerClaims() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !CLAIMED.contains(pos));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                CLAIMED.contains(hit.getBlockPos()) || CLAIMED.contains(hit.getBlockPos().relative(hit.getDirection()))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
    }

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

    @Override
    public FluidAccess fluids(BlockEntity blockEntity, Direction side) {
        Storage<FluidVariant> fluids = FluidStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (fluids == null) throw new GameTestAssertException("Missing fluid storage on " + side);
        return new FluidAccess() {
            @Override
            public long fill(Fluid fluid, long amount, boolean simulate) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = fluids.insert(FluidVariant.of(fluid), amount, transaction);
                    if (!simulate) transaction.commit();
                    return inserted;
                }
            }

            @Override
            public long drain(long amount, boolean simulate) {
                StorageView<FluidVariant> view = first();
                if (view == null) return 0;
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = fluids.extract(view.getResource(), amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted;
                }
            }

            @Override
            public long amount() {
                StorageView<FluidVariant> view = first();
                return view == null ? 0 : view.getAmount();
            }

            @Override
            public Fluid fluid() {
                StorageView<FluidVariant> view = first();
                return view == null ? Fluids.EMPTY : view.getResource().getFluid();
            }

            private StorageView<FluidVariant> first() {
                for (StorageView<FluidVariant> view : fluids) {
                    if (!view.isResourceBlank() && view.getAmount() > 0) return view;
                }
                return null;
            }
        };
    }

    @Override
    public long offerItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        Storage<ItemVariant> items = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (items == null) throw new GameTestAssertException("Missing item storage on " + side);
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = items.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Override
    public void protect(BlockPos pos, boolean claimed) {
        if (claimed) CLAIMED.add(pos.immutable());
        else CLAIMED.remove(pos);
    }

    @Override
    public boolean naturalSpawnAllowed(ServerLevel level, Mob mob) {
        try {
            java.lang.reflect.Method check = NaturalSpawner.class.getDeclaredMethod("isValidPositionForMob", ServerLevel.class, Mob.class, double.class);
            check.setAccessible(true);
            return (boolean) check.invoke(null, level, mob, 0.0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot call NaturalSpawner.isValidPositionForMob", e);
        }
    }
}
