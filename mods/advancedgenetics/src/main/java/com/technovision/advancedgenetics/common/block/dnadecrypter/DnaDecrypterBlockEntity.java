package com.technovision.advancedgenetics.common.block.dnadecrypter;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.common.item.DnaItem;
import com.technovision.advancedgenetics.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class DnaDecrypterBlockEntity extends AbstractInventoryBlockEntity {

    public static final int SLOT_COUNT = 2;
    public static final int INPUT_SLOT_INDEX = 0;
    public static final int OUTPUT_SLOT_INDEX = 1;

    public DnaDecrypterBlockEntity(BlockPos pos, BlockState state) {
        super(NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY),
                BlockEntityRegistry.DNA_DECRYPTER_BLOCK_ENTITY.get(),
                pos, state,
                Config.Common.dnaDecrypterEnergyCapacity.get(),
                Config.Common.dnaDecrypterTicksPerOperation.get(),
                Config.Common.dnaDecrypterMaxOverclock.get()
        );
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == INPUT_SLOT_INDEX && stack.getItem() instanceof DnaItem;
    }

    @Override
    public void updateRecipe() { }

    @Override
    public boolean canProcessRecipe() {
        ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
        ItemStack output = getStackInSlot(OUTPUT_SLOT_INDEX);
        CompoundTag inputTag = input.getTag();
        CompoundTag outputTag = output.getTag();
        return !input.isEmpty() && inputTag != null
                && getEnergyStorage().getEnergyStored() >= getEnergyRequirement()
                && !inputTag.getBoolean("decoded")
                && 1 + output.getCount() <= output.getMaxStackSize()
                && (output.isEmpty() || (outputTag != null && outputTag.getString("gene").equals(inputTag.getString("gene"))));
    }

    @Override
    public void processRecipe() {
        if (getProgress() < getMaxProgress()) {
            incrementProgress();
        } else {
            setProgress(0);
            ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
            if (ThreadLocalRandom.current().nextDouble() <= Config.Common.dnaDecrypterSuccessRate.get()) {
                ItemStack output = input.copy();
                output.setCount(1);
                DnaItem.decode(output);
                setOrIncrement(OUTPUT_SLOT_INDEX, output);
            }
            decrementSlot(INPUT_SLOT_INDEX, 1);
        }
        extractEnergy(getEnergyRequirement());
        setChanged();
    }

    @Override
    public <T extends Recipe<SimpleContainer>> void setRecipe(@Nullable T recipe) { }

    @Override
    public Recipe<SimpleContainer> getRecipe() {
        return null;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DnaDecrypterScreenHandler(syncId, inv, this, getPropertyDelegate());
    }

    private int getEnergyRequirement() {
        return Config.Common.dnaDecrypterEnergyPerTick.get() + (Config.Common.overclockEnergy.get() * getOverclock());
    }
}
