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

import javax.annotation.Nullable;

public abstract class AbstractInventoryBlockEntity extends AbstractProcessingBlockEntity implements ImplementedContainer {

    private final NonNullList<ItemStack> inventory;

    public AbstractInventoryBlockEntity(NonNullList<ItemStack> inventory, BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxProgress, int maxOverclock) {
        super(type, pos, state, energyCapacity, maxProgress, maxOverclock);
        this.inventory = inventory;
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
}
