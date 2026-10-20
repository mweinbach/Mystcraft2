/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.result;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * An item result. JSON: {@code {"item": "ns:id", "count": N, "nbt": {...}}}; count defaults to 1, nbt is optional
 * and may be an object or an SNBT string.
 */
public final class ItemStackRecipeResult
        implements IRecipeResult<ItemStack> {

    public static ItemStackRecipeResult from(final ItemLike item) {
        return from(item, 1);
    }

    public static ItemStackRecipeResult from(final ItemLike item, final int count) {
        return new ItemStackRecipeResult(new ItemStack(item, count));
    }

    public static ItemStackRecipeResult from(final ItemStack stack) {
        return new ItemStackRecipeResult(stack.copy());
    }

    public static ItemStackRecipeResult from(final JsonElement json) {

        final JsonObject object = GsonHelper.convertToJsonObject(json, "item result");
        final ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(object, JSON_ITEM));
        final Item item = Registry.ITEM.getOptional(id).orElseThrow(() -> new JsonSyntaxException("Unknown item: " + id));
        final int count = GsonHelper.getAsInt(object, JSON_COUNT, 1);

        if (Items.AIR == item || count < 1) {
            throw new JsonSyntaxException("An item result cannot be empty");
        }

        final ItemStack stack = new ItemStack(item, count);

        if (object.has(JSON_NBT)) {

            final JsonElement nbt = object.get(JSON_NBT);

            try {
                stack.setTag(TagParser.parseTag(nbt.isJsonObject() ? nbt.toString() : GsonHelper.convertToString(nbt, JSON_NBT)));
            } catch (final CommandSyntaxException ex) {
                throw new JsonSyntaxException("Invalid result NBT: " + ex.getMessage());
            }
        }

        return new ItemStackRecipeResult(stack);
    }

    public static ItemStackRecipeResult from(final FriendlyByteBuf buffer) {
        return new ItemStackRecipeResult(buffer.readItem());
    }

    //region IRecipeResult

    @Override
    public ItemStack getResult() {
        return this._result.copy();
    }

    @Override
    public long getAmount() {
        return this._result.getCount();
    }

    @Override
    public boolean isEmpty() {
        return this._result.isEmpty();
    }

    @Override
    public JsonElement toJson() {

        final JsonObject json = new JsonObject();

        json.addProperty(JSON_ITEM, Registry.ITEM.getKey(this._result.getItem()).toString());

        if (this._result.getCount() > 1) {
            json.addProperty(JSON_COUNT, this._result.getCount());
        }

        final CompoundTag tag = this._result.getTag();

        if (null != tag) {
            json.addProperty(JSON_NBT, tag.toString());
        }

        return json;
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer) {
        buffer.writeItem(this._result);
    }

    //endregion
    //region internals

    private ItemStackRecipeResult(final ItemStack result) {
        this._result = result;
    }

    private static final String JSON_ITEM = "item";
    private static final String JSON_COUNT = "count";
    private static final String JSON_NBT = "nbt";

    private final ItemStack _result;

    //endregion
}
