package com.technovision.advancedgenetics.api.blockentity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.crafting.Recipe;

import javax.annotation.Nullable;

public interface ProcessingBlockEntity {

    void tick();

    void updateRecipe();

    boolean canProcessRecipe();

    void processRecipe();

    <T extends Recipe<SimpleContainer>> void setRecipe(@Nullable T pRecipe);

    Recipe<SimpleContainer> getRecipe();

    int getProgress();

    void setProgress(int pProgress);

    void incrementProgress();

    void dropContents();
}
