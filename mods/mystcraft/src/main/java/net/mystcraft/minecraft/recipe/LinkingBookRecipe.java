package net.mystcraft.minecraft.recipe;

import net.mystcraft.integration.MystcraftItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Classic leather-cover recipe, retaining the panel's data as the 1.12 recipe did. */
public final class LinkingBookRecipe extends CustomRecipe {
    public LinkingBookRecipe(ResourceLocation id) { super(id); }

    @Override
    public boolean matches(CraftingContainer inventory, Level level) {
        if (!ClassicCraftingConfig.linkingBookEnabled()) return false;
        int panels = 0;
        int covers = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(MystcraftItems.LINKING_PANEL.get())) panels++;
            else if (stack.is(Items.LEATHER)) covers++;
            else return false;
        }
        return panels == 1 && covers == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory) {
        if (!matches(inventory, null)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(MystcraftItems.UNLINKED_LINK_BOOK.get());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack panel = inventory.getItem(slot);
            if (panel.is(MystcraftItems.LINKING_PANEL.get()) && panel.hasTag()) result.setTag(panel.getTag().copy());
        }
        return result;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return MystcraftItems.LINKING_BOOK_RECIPE.get(); }
}
