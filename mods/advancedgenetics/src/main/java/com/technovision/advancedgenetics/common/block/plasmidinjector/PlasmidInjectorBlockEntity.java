package com.technovision.advancedgenetics.common.block.plasmidinjector;

import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.common.item.AntiPlasmidItem;
import com.technovision.advancedgenetics.common.item.PlasmidItem;
import com.technovision.advancedgenetics.common.item.SyringeItem;
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

public class PlasmidInjectorBlockEntity extends AbstractInventoryBlockEntity {

    public static final int SLOT_COUNT = 2;
    public static final int INPUT_SLOT_INDEX = 0;
    public static final int OUTPUT_SLOT_INDEX = 1;

    public PlasmidInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY),
                BlockEntityRegistry.PLASMID_INJECTOR_BLOCK_ENTITY.get(),
                pos, state,
                Config.Common.plasmidInjectorEnergyCapacity.get(),
                Config.Common.plasmidInjectorTicksPerOperation.get(),
                Config.Common.plasmidInjectorMaxOverclock.get()
        );
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot == INPUT_SLOT_INDEX) return stack.getItem() instanceof PlasmidItem;
        return slot == OUTPUT_SLOT_INDEX && stack.getItem() instanceof SyringeItem;
    }

    @Override
    public void updateRecipe() { }

    @Override
    public boolean canProcessRecipe() {
        ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
        ItemStack output = getStackInSlot(OUTPUT_SLOT_INDEX);
        CompoundTag inputTag = input.getTag();
        return !input.isEmpty() && !output.isEmpty() && inputTag != null
                && input.getItem() instanceof PlasmidItem
                && output.getItem() instanceof SyringeItem
                && getEnergyStorage().getEnergyStored() >= getEnergyRequirement()
                && inputTag.getInt("count") >= PlasmidItem.maxGenes()
                && SyringeItem.isFilled(output)
                && SyringeItem.isPurified(output);
    }

    @Override
    public void processRecipe() {
        if (getProgress() < getMaxProgress()) {
            incrementProgress();
        } else {
            setProgress(0);
            ItemStack input = getStackInSlot(INPUT_SLOT_INDEX);
            if (ThreadLocalRandom.current().nextDouble() <= Config.Common.plasmidInjectorSuccessRate.get()) {
                ItemStack output = getStackInSlot(OUTPUT_SLOT_INDEX);
                if (input.getItem() instanceof AntiPlasmidItem) {
                    SyringeItem.addAntiGene(input, output);
                } else {
                    SyringeItem.addGene(input, output);
                }
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
        return new PlasmidInjectorScreenHandler(syncId, inv, this, getPropertyDelegate());
    }

    private int getEnergyRequirement() {
        return Config.Common.plasmidInjectorEnergyPerTick.get() + (Config.Common.overclockEnergy.get() * getOverclock());
    }
}
