/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.registries.ForgeRegistries;

/**
 * Matches one item whose share tag equals the given stack's tag exactly ({@code forge:nbt}). The share tag is the
 * stack's own tag; ProjectE does not replace it.
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
        if (input == null) {
            return false;
        }
        return stack.getItem() == input.getItem()
              && stack.getDamageValue() == input.getDamageValue()
              && shareTagsEqual(stack, input);
    }

    /**
     * Forge compares share tags, and a missing tag only equals another missing tag.
     */
    static boolean shareTagsEqual(ItemStack left, ItemStack right) {
        CompoundTag shareLeft = left.getTag();
        CompoundTag shareRight = right.getTag();
        if (shareLeft == null) {
            return shareRight == null;
        }
        return shareRight != null && shareLeft.equals(shareRight);
    }

    @NotNull
    @Override
    public ItemStack[] getItems() {
        return new ItemStack[]{stack};
    }

    @NotNull
    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return Serializer.INSTANCE;
    }

    @NotNull
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", CraftingHelper.getID(Serializer.INSTANCE).toString());
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.hasTag()) {
            json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    }

    public static final class Serializer implements IIngredientSerializer<StrictNBTIngredient> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public StrictNBTIngredient parse(FriendlyByteBuf buffer) {
            return new StrictNBTIngredient(buffer.readItem());
        }

        @Override
        public StrictNBTIngredient parse(JsonObject json) {
            return new StrictNBTIngredient(CraftingHelper.getItemStack(json, true));
        }

        @Override
        public void write(FriendlyByteBuf buffer, StrictNBTIngredient ingredient) {
            buffer.writeItem(ingredient.stack);
        }
    }
}
