/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Reads and writes one Forge ingredient type. Fabric API 0.77 has no custom ingredient API, so the port keeps the
 * registry itself.
 */
public interface IIngredientSerializer<T extends Ingredient> {

    T parse(FriendlyByteBuf buffer);

    T parse(JsonObject json);

    void write(FriendlyByteBuf buffer, T ingredient);
}
