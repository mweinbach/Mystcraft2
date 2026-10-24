package ports.gridwright.recipe;

import com.google.gson.JsonObject;
import ports.gridwright.registry.ModRecipes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * A resonator recipe: {@code amount} of {@code input} become {@code output} after {@code time} ticks,
 * drawing {@code gp} Grid Power for as long as it runs.
 *
 * <pre>{"type": "gridwright:resonating", "input": {"ingredient": {...}, "amount": 1},
 *  "output": {"item": "...", "count": 1}, "gp": 4.0, "time": 100}</pre>
 */
public record ResonatingRecipe(ResourceLocation id, Ingredient ingredient, int amount, ItemStack output, double gp, int time)
        implements Recipe<Container> {

    public boolean accepts(ItemStack stack) {
        return ingredient.test(stack);
    }

    @Override
    public boolean matches(Container container, Level level) {
        ItemStack input = container.getItem(0);
        return ingredient.test(input) && input.getCount() >= amount;
    }

    @Override
    public ItemStack assemble(Container container) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem() {
        return output;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RESONATING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.RESONATING.get();
    }

    /** Machine recipes stay out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<ResonatingRecipe> {

        @Override
        public ResonatingRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonObject input = GsonHelper.getAsJsonObject(json, "input");
            Ingredient ingredient = Ingredient.fromJson(input.get("ingredient"));
            int amount = GsonHelper.getAsInt(input, "amount", 1);
            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "output"));
            double gp = GsonHelper.getAsDouble(json, "gp");
            int time = GsonHelper.getAsInt(json, "time");
            if (amount < 1 || amount > 64 || !Double.isFinite(gp) || gp < 0 || time < 1) {
                throw new IllegalArgumentException(id + ": amount must be 1-64, gp a finite number at least 0 and time at least 1");
            }
            return new ResonatingRecipe(id, ingredient, amount, output, gp, time);
        }

        @Override
        public ResonatingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int amount = buffer.readVarInt();
            ItemStack output = buffer.readItem();
            double gp = buffer.readDouble();
            int time = buffer.readVarInt();
            return new ResonatingRecipe(id, ingredient, amount, output, gp, time);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, ResonatingRecipe recipe) {
            recipe.ingredient.toNetwork(buffer);
            buffer.writeVarInt(recipe.amount);
            buffer.writeItem(recipe.output);
            buffer.writeDouble(recipe.gp);
            buffer.writeVarInt(recipe.time);
        }
    }
}
