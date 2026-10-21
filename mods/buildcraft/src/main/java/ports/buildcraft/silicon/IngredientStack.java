package ports.buildcraft.silicon;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Upstream {@code IngredientStack}: an ingredient and how many items of it a recipe uses.
 * {@code exact}, when set, also requires the stack's NBT, which a plain ingredient ignores on Fabric.
 */
public record IngredientStack(Ingredient ingredient, int count, @Nullable ItemStack exact) {
    public IngredientStack(Ingredient ingredient, int count) {
        this(ingredient, count, null);
    }

    public IngredientStack {
        if (exact != null) {
            exact = exact.copy();
            exact.setCount(1);
            if (exact.isEmpty()) exact = null;
        }
    }

    /** The stack is this ingredient, and its NBT when this stack names one. */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ingredient.test(stack) && (exact == null || ItemStack.isSameItemSameTags(exact, stack));
    }

    /** Upstream {@code StackUtil.contains}: the stack matches and holds at least {@code count} items. */
    public boolean contains(ItemStack stack) {
        return matches(stack) && stack.getCount() >= count;
    }

    /** Upstream's assembly recipe component: {@code {"ingredient": ..., "amount": n}}, the amount defaulting to one. */
    static IngredientStack fromJson(JsonObject json) {
        return new IngredientStack(Ingredient.fromJson(json.get("ingredient")), GsonHelper.getAsInt(json, "amount", 1));
    }

    static IngredientStack fromNetwork(FriendlyByteBuf buffer) {
        return new IngredientStack(Ingredient.fromNetwork(buffer), buffer.readVarInt());
    }

    void toNetwork(FriendlyByteBuf buffer) {
        ingredient.toNetwork(buffer);
        buffer.writeVarInt(count);
    }
}
