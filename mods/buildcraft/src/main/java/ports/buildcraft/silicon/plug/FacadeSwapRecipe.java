package ports.buildcraft.silicon.plug;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import ports.buildcraft.silicon.BCSilicon;

/**
 * Upstream {@code FacadeSwapRecipe}: one facade in a crafting grid comes back with hollow flipped, and nothing
 * else in the grid. Full and hollow are the same item, so the swap does not change its EMC.
 */
public final class FacadeSwapRecipe implements CraftingRecipe {
    private final ResourceLocation id;

    public FacadeSwapRecipe(ResourceLocation id) {
        this.id = id;
    }

    private static ItemStack only(CraftingContainer inventory) {
        ItemStack found = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }

    @Override
    public boolean matches(CraftingContainer inventory, Level level) {
        return !assemble(inventory).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory) {
        ItemStack stack = only(inventory);
        if (!stack.is(BCSilicon.FACADE_ITEM.get())) return ItemStack.EMPTY;
        FacadeInstance states = FacadeItem.get(stack);
        if (!states.phasedStates[0].info.visible && states.phasedStates[0].info.state.isAir()) return ItemStack.EMPTY;
        ItemStack result = FacadeItem.create(states.withSwappedHollow());
        result.setCount(1);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 1 && height >= 1;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inventory) {
        return NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BCSilicon.FACADE_SWAP_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    public static final class Serializer implements RecipeSerializer<FacadeSwapRecipe> {
        @Override
        public FacadeSwapRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new FacadeSwapRecipe(id);
        }

        @Override
        public FacadeSwapRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new FacadeSwapRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, FacadeSwapRecipe recipe) {}
    }
}
