package com.technovision.advancedgenetics.common.block.dnaextractor;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.common.item.CellItem;
import com.technovision.advancedgenetics.common.item.DnaItem;
import com.technovision.advancedgenetics.registry.BlockEntityRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class DnaExtractorBlockEntity extends AbstractInventoryBlockEntity {

    public static final int SLOT_COUNT = 2;
    public static final int INPUT_SLOT_INDEX = 0;
    public static final int OUTPUT_SLOT_INDEX = 1;

    public DnaExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY),
                BlockEntityRegistry.DNA_EXTRACTOR_BLOCK_ENTITY.get(),
                pos, state,
                Config.Common.dnaExtractorEnergyCapacity.get(),
                Config.Common.dnaExtractorTicksPerOperation.get(),
                Config.Common.dnaExtractorMaxOverclock.get()
        );
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == INPUT_SLOT_INDEX && stack.getItem() instanceof CellItem;
    }

    @Override
    public void updateRecipe() { }

    @Override
    public boolean canProcessRecipe() {
        return !getStackInSlot(INPUT_SLOT_INDEX).isEmpty()
                && getStackInSlot(INPUT_SLOT_INDEX).getItem() instanceof CellItem
                && getStackInSlot(OUTPUT_SLOT_INDEX).isEmpty()
                && getEnergyStorage().getEnergyStored() >= getEnergyRequirement();
    }

    @Override
    public void processRecipe() {
        if (getProgress() < getMaxProgress()) {
            incrementProgress();
        } else {
            setProgress(0);
            ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
            if (ThreadLocalRandom.current().nextDouble() <= Config.Common.dnaExtractorSuccessRate.get()) {
                ItemStack output = new ItemStack(ItemRegistry.DNA_HELIX.get());
                DnaItem.setGene(input, output);
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
        return new DnaExtractorScreenHandler(syncId, inv, this, getPropertyDelegate());
    }

    private int getEnergyRequirement() {
        return Config.Common.dnaExtractorEnergyPerTick.get() + (Config.Common.overclockEnergy.get() * getOverclock());
    }
}
