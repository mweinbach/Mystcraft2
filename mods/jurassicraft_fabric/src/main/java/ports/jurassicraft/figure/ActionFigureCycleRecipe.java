package ports.jurassicraft.figure;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import ports.jurassicraft.content.Species;

/** One figure advances to the next registered species, wrapping back to Brachiosaurus. */
public final class ActionFigureCycleRecipe extends CustomRecipe {
    public static final List<Species> ORDER = Arrays.stream(Species.values()).sorted(Comparator.comparing(Species::id)).toList();
    public ActionFigureCycleRecipe(ResourceLocation id) { super(id); }
    private ItemStack input(CraftingContainer grid) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) continue;
            if (!found.isEmpty() || !stack.is(ActionFigureRegistry.ITEM.get())) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }
    @Override public boolean matches(CraftingContainer grid, Level level) { return !input(grid).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer grid) {
        ItemStack source = input(grid);
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = source.copy(); result.setCount(1);
        int index = ORDER.indexOf(ActionFigureItem.species(source));
        CompoundTag data = getData(result);
        data.putString("Species", ORDER.get((index + 1) % ORDER.size()).id());
        net.minecraft.world.item.BlockItem.setBlockEntityData(result, ActionFigureRegistry.ENTITY.get(), data);
        return result;
    }
    private CompoundTag getData(ItemStack stack) {
        CompoundTag data = net.minecraft.world.item.BlockItem.getBlockEntityData(stack);
        return data == null ? new CompoundTag() : data.copy();
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0; }
    @Override public RecipeSerializer<?> getSerializer() { return ActionFigureRegistry.CYCLE.get(); }
}
