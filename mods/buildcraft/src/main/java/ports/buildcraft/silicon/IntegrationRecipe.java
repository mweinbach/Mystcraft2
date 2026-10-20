package ports.buildcraft.silicon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
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
 * Upstream {@code IntegrationRecipeBasic}: a centre stack and the stacks around it integrated into one output for
 * a fixed amount of laser power. Every surrounding requirement takes a different stack and no other stacks may be
 * there. Upstream only registered these from code and shipped none; here they are datapack recipes of type
 * {@code buildcraft:integration}.
 */
public final class IntegrationRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final long requiredMicroJoules;
    private final IngredientStack target;
    private final List<IngredientStack> toIntegrate;
    private final ItemStack output;

    public IntegrationRecipe(ResourceLocation id, long requiredMicroJoules, IngredientStack target, List<IngredientStack> toIntegrate, ItemStack output) {
        this.id = id;
        this.requiredMicroJoules = requiredMicroJoules;
        this.target = target;
        this.toIntegrate = List.copyOf(toIntegrate);
        this.output = output;
    }

    private boolean matches(ItemStack target, List<ItemStack> toIntegrate) {
        if (!this.target.contains(target)) return false;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : toIntegrate) {
            if (!stack.isEmpty()) remaining.add(stack);
        }
        for (IngredientStack definition : this.toIntegrate) {
            boolean matched = false;
            for (Iterator<ItemStack> iterator = remaining.iterator(); iterator.hasNext(); ) {
                if (definition.contains(iterator.next())) {
                    iterator.remove();
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return remaining.isEmpty();
    }

    public ItemStack getOutput(ItemStack target, List<ItemStack> toIntegrate) {
        return matches(target, toIntegrate) ? output : ItemStack.EMPTY;
    }

    public List<IngredientStack> getRequirements(ItemStack output) {
        return toIntegrate;
    }

    public long getRequiredMicroJoules(ItemStack output) {
        return requiredMicroJoules;
    }

    public IngredientStack getCenterStack() {
        return target;
    }

    /** Slot 0 is the centre and the rest surround it, as the integration table holds them. */
    @Override
    public boolean matches(Container container, Level level) {
        List<ItemStack> around = new ArrayList<>();
        for (int i = 1; i < container.getContainerSize(); i++) around.add(container.getItem(i));
        return container.getContainerSize() > 0 && matches(container.getItem(0), around);
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
        ingredients.add(target.ingredient());
        for (IngredientStack stack : toIntegrate) ingredients.add(stack.ingredient());
        return ingredients;
    }

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
        return BCSilicon.INTEGRATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return BCSilicon.INTEGRATION_TYPE.get();
    }

    /** The assembly recipe's JSON with the centre stack as {@code target}: {@code result}, {@code MJ}, {@code target} and {@code components}. */
    public static final class Serializer implements RecipeSerializer<IntegrationRecipe> {
        @Override
        public IntegrationRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<IngredientStack> components = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "components")) {
                components.add(IngredientStack.fromJson(GsonHelper.convertToJsonObject(element, "component")));
            }
            return new IntegrationRecipe(id, GsonHelper.getAsLong(json, "MJ") * Power.MJ,
                    IngredientStack.fromJson(GsonHelper.getAsJsonObject(json, "target")), components,
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public IntegrationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            long cost = buffer.readVarLong();
            IngredientStack target = IngredientStack.fromNetwork(buffer);
            int count = buffer.readVarInt();
            List<IngredientStack> components = new ArrayList<>(count);
            for (int i = 0; i < count; i++) components.add(IngredientStack.fromNetwork(buffer));
            return new IntegrationRecipe(id, cost, target, components, buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, IntegrationRecipe recipe) {
            buffer.writeVarLong(recipe.requiredMicroJoules);
            recipe.target.toNetwork(buffer);
            buffer.writeVarInt(recipe.toIntegrate.size());
            for (IngredientStack stack : recipe.toIntegrate) stack.toNetwork(buffer);
            buffer.writeItem(recipe.output);
        }
    }
}
