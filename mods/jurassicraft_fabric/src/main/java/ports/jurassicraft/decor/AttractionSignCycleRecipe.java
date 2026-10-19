package ports.jurassicraft.decor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** The original shapeless recipes cycle a single attraction sign through its artwork. */
public final class AttractionSignCycleRecipe extends CustomRecipe {
    public AttractionSignCycleRecipe(ResourceLocation id) { super(id); }
    private ItemStack input(CraftingContainer grid) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (!found.isEmpty() || !stack.is(DecorRegistry.ATTRACTION_SIGN.get())) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }
    @Override public boolean matches(CraftingContainer grid, Level level) { return !input(grid).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer grid) {
        ItemStack source = input(grid);
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = source.copy();
        result.setCount(1);
        String current = source.hasTag() ? source.getTag().getString("Artwork") : "";
        int index = ParkSigns.ATTRACTIONS.indexOf(ParkSigns.validate(true, current));
        result.getOrCreateTag().putString("Artwork", ParkSigns.ATTRACTIONS.get((index + 1) % ParkSigns.ATTRACTIONS.size()));
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return DecorRegistry.SIGN_CYCLE.get(); }
}
