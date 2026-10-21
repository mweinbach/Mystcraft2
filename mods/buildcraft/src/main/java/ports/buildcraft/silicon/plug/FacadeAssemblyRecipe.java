package ports.buildcraft.silicon.plug;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.AssemblyRecipe;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.IngredientStack;
import ports.buildcraft.transport.BCTransport;

/**
 * Upstream {@code FacadeAssemblyRecipes}: three structure pipes and one block become six facades of that block,
 * full and hollow, for 64 MJ. The block is whichever stack in the table has facade states.
 */
public final class FacadeAssemblyRecipe extends AssemblyRecipe {
    public static final int OUTPUT_COUNT = 6;
    public static final int PIPE_COUNT = 3;
    public static final long COST = 64 * Power.MJ;

    public FacadeAssemblyRecipe(ResourceLocation id) {
        super(id, COST, List.of(), ItemStack.EMPTY);
    }

    @Override
    public List<ItemStack> getOutputs(List<ItemStack> inputs) {
        if (inputs.stream().noneMatch(FacadeAssemblyRecipe::hasPipes)) return List.of();
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack stack : inputs) {
            if (stack.isEmpty() || hasPipes(stack) && stack.is(BCTransport.STRUCTURE.item())) continue;
            for (FacadeBlockStateInfo info : FacadeStateManager.infosFor(stack)) {
                outputs.add(FacadeItem.create(info, false));
                outputs.add(FacadeItem.create(info, true));
            }
        }
        return outputs;
    }

    @Override
    public List<ItemStack> getOutputPreviews() {
        return List.of();
    }

    @Override
    public ItemStack produced(ItemStack requested) {
        return validOutput(requested) ? requested : ItemStack.EMPTY;
    }

    @Override
    public List<IngredientStack> getInputsFor(ItemStack output) {
        if (!validOutput(output)) return List.of();
        FacadePhasedState state = FacadeItem.get(output).phasedStates[0];
        return List.of(new IngredientStack(Ingredient.of(BCTransport.STRUCTURE.item()), PIPE_COUNT),
                new IngredientStack(Ingredient.of(state.info.requiredStack.getItem()), 1, state.info.requiredStack));
    }

    @Override
    public long getRequiredMicroJoulesFor(ItemStack output) {
        return validOutput(output) ? COST : 0;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    private static boolean hasPipes(ItemStack stack) {
        return stack.is(BCTransport.STRUCTURE.item()) && stack.getCount() >= PIPE_COUNT;
    }

    private static boolean validOutput(ItemStack stack) {
        if (!stack.is(BCSilicon.FACADE_ITEM.get()) || stack.getCount() != OUTPUT_COUNT) return false;
        FacadeInstance states = FacadeItem.get(stack);
        if (states.phased) return false;
        FacadeBlockStateInfo info = states.phasedStates[0].info;
        return info.visible && FacadeStateManager.isValid(info.state);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BCSilicon.FACADE_ASSEMBLY_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<FacadeAssemblyRecipe> {
        @Override
        public FacadeAssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new FacadeAssemblyRecipe(id);
        }

        @Override
        public FacadeAssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FacadeAssemblyRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FacadeAssemblyRecipe recipe) {}
    }
}
