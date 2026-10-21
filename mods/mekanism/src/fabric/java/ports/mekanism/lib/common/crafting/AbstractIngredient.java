/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

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
 * Base of the ingredient types Forge adds. Vanilla's network format only carries the matching stacks, so on a Fabric
 * client these arrive as plain item ingredients; matching always runs on the server.
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
    public boolean isSimple() {
        return false;
    }

    @NotNull
    @Override
    public abstract JsonElement toJson();
}
