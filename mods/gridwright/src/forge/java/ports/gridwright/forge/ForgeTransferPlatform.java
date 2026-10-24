package ports.gridwright.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;
import ports.gridwright.transfer.FluidAmount;
import ports.gridwright.transfer.TransferPlatform;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Transfer services on Forge: item and fluid handler capabilities, Forge fake players and break events. */
final class ForgeTransferPlatform implements TransferPlatform.Backend {

    @Nullable
    private static <T> T capability(Level level, BlockPos pos, Direction face, Capability<T> capability) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.getCapability(capability, face).resolve().orElse(null);
    }

    @Override
    public boolean hasItems(Level level, BlockPos pos, Direction face) {
        return capability(level, pos, face, ForgeCapabilities.ITEM_HANDLER) != null;
    }

    @Override
    public boolean hasFluids(Level level, BlockPos pos, Direction face) {
        return capability(level, pos, face, ForgeCapabilities.FLUID_HANDLER) != null;
    }

    @Override
    public ItemStack extractItems(ServerLevel level, BlockPos pos, Direction face, Predicate<ItemStack> filter, int max, boolean simulate) {
        IItemHandler items = capability(level, pos, face, ForgeCapabilities.ITEM_HANDLER);
        if (items == null) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        for (int slot = 0; slot < items.getSlots() && result.getCount() < max; slot++) {
            ItemStack stored = items.getStackInSlot(slot);
            if (stored.isEmpty()) continue;
            if (result.isEmpty() ? !filter.test(stored) : !ItemHandlerHelper.canItemStacksStack(result, stored)) continue;
            int limit = result.isEmpty() ? max : Math.min(max, result.getMaxStackSize()) - result.getCount();
            ItemStack taken = items.extractItem(slot, limit, simulate);
            if (taken.isEmpty()) continue;
            if (result.isEmpty()) result = taken.copy();
            else result.grow(taken.getCount());
        }
        return result;
    }

    @Override
    public ItemStack insertItems(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, boolean simulate) {
        IItemHandler items = capability(level, pos, face, ForgeCapabilities.ITEM_HANDLER);
        return items == null ? stack : ItemHandlerHelper.insertItemStacked(items, stack.copy(), simulate);
    }

    @Override
    public FluidAmount extractFluid(ServerLevel level, BlockPos pos, Direction face, Predicate<Fluid> filter, long millibuckets, boolean simulate) {
        IFluidHandler fluids = capability(level, pos, face, ForgeCapabilities.FLUID_HANDLER);
        if (fluids == null) return FluidAmount.EMPTY;
        int amount = (int) Math.min(Integer.MAX_VALUE, millibuckets);
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            FluidStack stored = fluids.getFluidInTank(tank);
            if (stored.isEmpty() || stored.hasTag() || !filter.test(stored.getFluid())) continue;
            FluidStack drained = fluids.drain(new FluidStack(stored.getFluid(), amount), action(simulate));
            if (!drained.isEmpty()) return new FluidAmount(drained.getFluid(), drained.getAmount());
        }
        return FluidAmount.EMPTY;
    }

    @Override
    public long insertFluid(ServerLevel level, BlockPos pos, Direction face, Fluid fluid, long millibuckets, boolean simulate) {
        IFluidHandler fluids = capability(level, pos, face, ForgeCapabilities.FLUID_HANDLER);
        if (fluids == null) return 0;
        return fluids.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, millibuckets)), action(simulate));
    }

    @Nullable
    @Override
    public Fluid fluidIn(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).filter(fluid -> !fluid.isEmpty()).map(FluidStack::getFluid).orElse(null);
    }

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, GameProfile owner) {
        FakePlayer player = FakePlayerFactory.get(level, owner);
        // Building a player for the owner's UUID re-points an online owner's advancements at it.
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner.getId());
        if (online != null) level.getServer().getPlayerList().getPlayerAdvancements(online);
        if (!level.mayInteract(player, pos)) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> data) {
        NetworkHooks.openScreen(player, provider, data);
    }

    private static IFluidHandler.FluidAction action(boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }
}
