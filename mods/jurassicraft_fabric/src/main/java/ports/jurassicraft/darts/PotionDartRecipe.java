package ports.jurassicraft.darts;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Eight tranquilizer darts surround a potion, retaining its effects and custom color. */
public final class PotionDartRecipe extends CustomRecipe {
    public PotionDartRecipe(ResourceLocation id) { super(id); }
    @Override public boolean matches(CraftingContainer grid, Level level) {
        if (grid.getWidth() != 3 || grid.getHeight() != 3) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            if (i == 4 ? !stack.is(Items.POTION) : !stack.is(DartRegistry.TRANQUILIZER.get())) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(CraftingContainer grid) {
        if (grid.getWidth() != 3 || grid.getHeight() != 3 || !grid.getItem(4).is(Items.POTION)) return ItemStack.EMPTY;
        ItemStack potion = grid.getItem(4);
        ItemStack result = PotionUtils.setPotion(new ItemStack(DartRegistry.POTION.get(), 8), PotionUtils.getPotion(potion));
        PotionUtils.setCustomEffects(result, PotionUtils.getCustomEffects(potion));
        if (potion.hasTag() && potion.getTag().contains(PotionUtils.TAG_CUSTOM_POTION_COLOR, Tag.TAG_INT))
            result.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, potion.getTag().getInt(PotionUtils.TAG_CUSTOM_POTION_COLOR));
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width >= 3 && height >= 3; }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        return NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
    }
    @Override public RecipeSerializer<?> getSerializer() { return DartRegistry.POTION_RECIPE.get(); }
}
