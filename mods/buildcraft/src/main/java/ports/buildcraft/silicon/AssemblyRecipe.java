package ports.buildcraft.silicon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
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
import ports.buildcraft.lib.Power;

/**
 * Upstream {@code AssemblyRecipeBasic}: a fixed set of ingredient stacks assembled into one output for a fixed
 * amount of laser power. Upstream registered these in code and from {@code assembly_recipes_pre_mj} JSON files;
 * here they are datapack recipes of type {@code buildcraft:assembly} with that JSON's fields.
 */
public final class AssemblyRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final long requiredMicroJoules;
    private final List<IngredientStack> requiredStacks;
    private final ItemStack output;

    public AssemblyRecipe(ResourceLocation id, long requiredMicroJoules, List<IngredientStack> requiredStacks, ItemStack output) {
        this.id = id;
        this.requiredMicroJoules = requiredMicroJoules;
        this.requiredStacks = List.copyOf(requiredStacks);
        this.output = output;
    }

    /** The outputs that could be assembled from {@code inputs}: one stack holds enough of every ingredient. */
    public List<ItemStack> getOutputs(List<ItemStack> inputs) {
        for (IngredientStack definition : requiredStacks) {
            if (inputs.stream().noneMatch(definition::contains)) return List.of();
        }
        return List.of(output);
    }

    public List<ItemStack> getOutputPreviews() {
        return List.of(output);
    }

    public List<IngredientStack> getInputsFor(ItemStack output) {
        return requiredStacks;
    }

    public long getRequiredMicroJoulesFor(ItemStack output) {
        return requiredMicroJoules;
    }

    @Override
    public boolean matches(Container container, Level level) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) inputs.add(container.getItem(i));
        return !getOutputs(inputs).isEmpty();
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (IngredientStack stack : requiredStacks) ingredients.add(stack.ingredient());
        return ingredients;
    }

    /** Assembly recipes stay out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BCSilicon.ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return BCSilicon.ASSEMBLY_TYPE.get();
    }

    /** Upstream's JSON: {@code result}, the cost as {@code MJ} and {@code components} of ingredient and amount. */
    public static final class Serializer implements RecipeSerializer<AssemblyRecipe> {
        @Override
        public AssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<IngredientStack> components = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "components")) {
                components.add(IngredientStack.fromJson(GsonHelper.convertToJsonObject(element, "component")));
            }
            long cost = GsonHelper.getAsLong(json, "MJ") * Power.MJ;
            return new AssemblyRecipe(id, cost, components, ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public AssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            long cost = buffer.readVarLong();
            int count = buffer.readVarInt();
            List<IngredientStack> components = new ArrayList<>(count);
            for (int i = 0; i < count; i++) components.add(IngredientStack.fromNetwork(buffer));
            return new AssemblyRecipe(id, cost, components, buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AssemblyRecipe recipe) {
            buffer.writeVarLong(recipe.requiredMicroJoules);
            buffer.writeVarInt(recipe.requiredStacks.size());
            for (IngredientStack stack : recipe.requiredStacks) stack.toNetwork(buffer);
            buffer.writeItem(recipe.output);
        }
    }
}
