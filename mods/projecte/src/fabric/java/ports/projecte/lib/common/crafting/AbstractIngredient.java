/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.stream.Stream;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the ingredient types Forge adds. {@link Ingredient#toNetwork} writes the serializer id before the payload,
 * so a Fabric client rebuilds the same type instead of a plain item list.
 */
public abstract class AbstractIngredient extends Ingredient {

    @Nullable
    private IntList stackingIds;

    protected AbstractIngredient() {
        super(Stream.empty());
    }

    @Override
    public abstract boolean test(@Nullable ItemStack stack);

    @NotNull
    @Override
    public abstract ItemStack[] getItems();

    @Override
    public boolean isEmpty() {
        return getItems().length == 0;
    }

    @NotNull
    @Override
    public IntList getStackingIds() {
        if (stackingIds == null) {
            ItemStack[] items = getItems();
            stackingIds = new IntArrayList(items.length);
            for (ItemStack stack : items) {
                stackingIds.add(StackedContents.getStackingIndex(stack));
            }
            stackingIds.sort(null);
        }
        return stackingIds;
    }

    @Override
    public boolean projecte$isSimple() {
        return false;
    }

    /**
     * Fabric's flag for the same property, which every mod on Fabric can read. The Mekanism port carries its own copy
     * of Forge's ingredient types, and whichever copy reads a recipe's JSON first owns the ingredient, so Mekanism's
     * recipe cache learns from this flag that a ProjectE-read {@code forge:nbt} input must be tested by stack.
     */
    @Override
    public boolean requiresTesting() {
        return !projecte$isSimple();
    }

    @NotNull
    @Override
    public abstract IIngredientSerializer<? extends Ingredient> getSerializer();

    @NotNull
    @Override
    public abstract JsonElement toJson();
}
