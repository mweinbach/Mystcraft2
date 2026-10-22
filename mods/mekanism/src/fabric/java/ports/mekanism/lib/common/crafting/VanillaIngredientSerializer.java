/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Forge's serializer for a plain item or tag ingredient. Vanilla's own reader still handles those in JSON; this exists
 * so a vanilla ingredient has a serializer id if something asks for one.
 */
public final class VanillaIngredientSerializer implements IIngredientSerializer<Ingredient> {

    public static final VanillaIngredientSerializer INSTANCE = new VanillaIngredientSerializer();

    private VanillaIngredientSerializer() {
    }

    @Override
    public Ingredient parse(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        ItemStack[] stacks = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            stacks[i] = buffer.readItem();
        }
        return Ingredient.of(stacks);
    }

    @Override
    public Ingredient parse(JsonObject json) {
        return Ingredient.fromJson(json);
    }

    @Override
    public void write(FriendlyByteBuf buffer, Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        buffer.writeVarInt(items.length);
        for (ItemStack stack : items) {
            buffer.writeItem(stack);
        }
    }
}
