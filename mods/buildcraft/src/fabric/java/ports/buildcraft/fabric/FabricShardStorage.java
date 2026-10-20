package ports.buildcraft.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ExtractionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.lib.FluidAmount;

/**
 * Upstream {@code FragileFluidHandler} on the Transfer API: drain-only, in whole millibuckets so both
 * loaders agree, and the shard is used up once empty.
 */
final class FabricShardStorage implements ExtractionOnlyStorage<FluidVariant>, SingleSlotStorage<FluidVariant> {
    private final ContainerItemContext context;

    private FabricShardStorage(ContainerItemContext context) {
        this.context = context;
    }

    static void register() {
        FluidStorage.ITEM.registerForItems((stack, context) -> new FabricShardStorage(context), BCCore.FRAGILE_FLUID_SHARD.get());
    }

    private FluidAmount held() {
        ItemVariant variant = context.getItemVariant();
        return variant.isOf(BCCore.FRAGILE_FLUID_SHARD.get()) ? FragileFluidShardItem.getFluid(variant.toStack()) : FluidAmount.EMPTY;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        FluidAmount held = held();
        long mb = maxAmount / FabricPorts.DROPLETS_PER_MB;
        if (held.isEmpty() || resource.hasNbt() || !resource.isOf(held.fluid()) || mb <= 0) return 0;
        ItemStack stack = context.getItemVariant().toStack();
        long drained = FragileFluidShardItem.drain(stack, held.fluid(), mb, false).amount();
        boolean empty = FragileFluidShardItem.getFluid(stack).isEmpty();
        long moved = empty ? context.extract(context.getItemVariant(), 1, transaction) : context.exchange(ItemVariant.of(stack), 1, transaction);
        return moved == 1 ? drained * FabricPorts.DROPLETS_PER_MB : 0;
    }

    @Override public boolean isResourceBlank() { return held().isEmpty(); }
    @Override public FluidVariant getResource() { return held().isEmpty() ? FluidVariant.blank() : FluidVariant.of(held().fluid()); }
    @Override public long getAmount() { return held().amount() * FabricPorts.DROPLETS_PER_MB; }
    @Override public long getCapacity() { return FragileFluidShardItem.MAX_FLUID_HELD * FabricPorts.DROPLETS_PER_MB; }
}
