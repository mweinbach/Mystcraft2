/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches what {@code base} matches unless {@code subtracted} also matches ({@code forge:difference}).
 */
public class DifferenceIngredient extends AbstractIngredient {

    private final Ingredient base;
    private final Ingredient subtracted;
    @Nullable
    private ItemStack[] stacks;

    protected DifferenceIngredient(Ingredient base, Ingredient subtracted) {
        this.base = base;
        this.subtracted = subtracted;
    }

    public static DifferenceIngredient of(Ingredient base, Ingredient subtracted) {
        return new DifferenceIngredient(base, subtracted);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && base.test(stack) && !subtracted.test(stack);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        if (stacks == null) {
            stacks = Arrays.stream(base.getItems()).filter(stack -> !subtracted.test(stack)).toArray(ItemStack[]::new);
        }
        return stacks;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forge:difference");
        json.add("base", base.toJson());
        json.add("subtracted", subtracted.toJson());
        return json;
    }
}
