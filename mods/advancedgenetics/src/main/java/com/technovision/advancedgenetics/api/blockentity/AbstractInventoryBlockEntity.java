package com.technovision.advancedgenetics.api.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public abstract class AbstractInventoryBlockEntity extends AbstractProcessingBlockEntity implements ImplementedContainer {

    private final NonNullList<ItemStack> inventory;
    private LazyOptional<?>[] itemHandlers;

    public AbstractInventoryBlockEntity(NonNullList<ItemStack> inventory, BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxProgress, int maxOverclock) {
        super(type, pos, state, energyCapacity, maxProgress, maxOverclock);
        this.inventory = inventory;
        this.itemHandlers = createItemHandlers();
    }

    /**
     * Whether a stack belongs in a slot, mirroring the machine's GUI slot filters.
     */
    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /**
     * Whether automation may pull the stack in the output slot. Machines that modify that
     * slot in place override this so unfinished work stays put.
     */
    protected boolean isOutputReady(ItemStack stack) {
        return true;
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        ContainerHelper.saveAllItems(nbt, inventory);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        inventory.clear();
        ContainerHelper.loadAllItems(nbt, inventory);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void dropContents() {
        if (level != null) {
            Containers.dropContents(level, worldPosition, this);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isItemValid(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == 1 && isOutputReady(stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 0 && isItemValid(slot, stack);
    }

    public ItemStack getStackInSlot(int slot) {
        return inventory.get(slot);
    }

    public ItemStack setStackInSlot(int slot, ItemStack stack) {
        return inventory.set(slot, stack);
    }

    public void incrementSlot(int pSlot, int pAmount) {
        ItemStack temp = this.getStackInSlot(pSlot);
        if (temp.getCount() + pAmount <= temp.getMaxStackSize()) {
            temp.setCount(temp.getCount() + pAmount);
        }
        this.setStackInSlot(pSlot, temp);
    }

    public void setOrIncrement(int slot, ItemStack stackToSet) {
        if (!stackToSet.isEmpty()) {
            if (getStackInSlot(slot).isEmpty()) {
                setStackInSlot(slot, stackToSet);
            } else {
                incrementSlot(slot, stackToSet.getCount());
            }
        }
    }

    public void decrementSlot(int slot, int pAmount) {
        ItemStack temp = this.getStackInSlot(slot);
        if (temp.isEmpty()) return;
        if (temp.getCount() - pAmount < 0) return;

        temp.shrink(pAmount);
        if (temp.getCount() <= 0) {
            this.setStackInSlot(slot, ItemStack.EMPTY);
        } else {
            this.setStackInSlot(slot, temp);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove) {
            int index = side == null ? 0 : side.ordinal() + 1;
            return itemHandlers[index].cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<?> handler : itemHandlers) {
            handler.invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlers = createItemHandlers();
    }

    private LazyOptional<?>[] createItemHandlers() {
        Direction[] directions = Direction.values();
        LazyOptional<?>[] handlers = new LazyOptional<?>[directions.length + 1];
        handlers[0] = LazyOptional.of(() -> (IItemHandler) new InvWrapper(this));
        for (Direction direction : directions) {
            handlers[direction.ordinal() + 1] = LazyOptional.of(() -> (IItemHandler) new SidedInvWrapper(this, direction));
        }
        return handlers;
    }
}
