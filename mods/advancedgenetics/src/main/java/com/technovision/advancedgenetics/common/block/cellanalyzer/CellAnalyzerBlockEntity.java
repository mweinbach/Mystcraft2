package com.technovision.advancedgenetics.common.block.cellanalyzer;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.common.item.OrganicMatterItem;
import com.technovision.advancedgenetics.common.recipe.cellanalyzer.CellAnalyzerRecipe;
import com.technovision.advancedgenetics.registry.BlockEntityRegistry;
import com.technovision.advancedgenetics.registry.RecipeRegistry;
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

public class CellAnalyzerBlockEntity extends AbstractInventoryBlockEntity {

    public static final int SLOT_COUNT = 2;
    public static final int INPUT_SLOT_INDEX = 0;
    public static final int OUTPUT_SLOT_INDEX = 1;

    private CellAnalyzerRecipe recipe;

    public CellAnalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY),
                BlockEntityRegistry.CELL_ANALYZER_BLOCK_ENTITY.get(),
                pos, state,
                Config.Common.cellAnalyzerEnergyCapacity.get(),
                Config.Common.cellAnalyzerTicksPerOperation.get(),
                Config.Common.cellAnalyzerMaxOverclock.get()
        );
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return slot == INPUT_SLOT_INDEX && stack.getItem() instanceof OrganicMatterItem;
    }

    @Override
    public void updateRecipe() {
        if (level == null || level.isClientSide()) return;
        if (!getStackInSlot(INPUT_SLOT_INDEX).isEmpty()) {
            level.getRecipeManager().getAllRecipesFor(RecipeRegistry.CELL_ANALYZER_TYPE.get())
                    .stream()
                    .filter(recipe -> ItemStack.isSameItemSameTags(getStackInSlot(INPUT_SLOT_INDEX), recipe.getInput()))
                    .findFirst()
                    .ifPresent(recipe -> this.recipe = recipe);
        }
    }

    @Override
    public boolean canProcessRecipe() {
        if (recipe != null) {
            ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
            ItemStack output = getStackInSlot(OUTPUT_SLOT_INDEX);
            return getEnergyStorage().getEnergyStored() >= getEnergyRequirement()
                    && (ItemStack.isSameItemSameTags(input, recipe.getInput()) && input.getCount() >= recipe.getInput().getCount())
                    && (recipe.getOutput().getCount() + output.getCount()) <= recipe.getOutput().getMaxStackSize()
                    && (ItemStack.isSameItemSameTags(output, recipe.getOutput()) || output.isEmpty());
        }
        return false;
    }

    @Override
    public void processRecipe() {
        if (getProgress() < getMaxProgress()) {
            incrementProgress();
        } else {
            setProgress(0);
            decrementSlot(INPUT_SLOT_INDEX, recipe.getInput().getCount());
            if (ThreadLocalRandom.current().nextDouble() <= Config.Common.cellAnalyzerSuccessRate.get()) {
                setOrIncrement(OUTPUT_SLOT_INDEX, recipe.getOutput().copy());
            }
        }
        extractEnergy(getEnergyRequirement());
        setChanged();
    }

    @Override
    public <T extends Recipe<SimpleContainer>> void setRecipe(@Nullable T recipe) {
        this.recipe = (CellAnalyzerRecipe) recipe;
    }

    @Override
    public Recipe<SimpleContainer> getRecipe() {
        return recipe;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new CellAnalyzerScreenHandler(syncId, inv, this, getPropertyDelegate());
    }

    private int getEnergyRequirement() {
        return Config.Common.cellAnalyzerEnergyPerTick.get() + (Config.Common.overclockEnergy.get() * getOverclock());
    }
}
