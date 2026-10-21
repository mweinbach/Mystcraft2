/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches one item whose tag equals the given stack's tag exactly ({@code forge:nbt}).
 */
public class StrictNBTIngredient extends AbstractIngredient {

    private final ItemStack stack;

    protected StrictNBTIngredient(ItemStack stack) {
        this.stack = stack;
    }

    public static StrictNBTIngredient of(ItemStack stack) {
        return new StrictNBTIngredient(stack.copy());
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null || input.getItem() != stack.getItem() || input.getDamageValue() != stack.getDamageValue()) {
            return false;
        }
        return ItemStack.tagMatches(input, stack);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        return new ItemStack[]{stack};
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forge:nbt");
        json.addProperty("item", Registry.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.hasTag()) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }
}
