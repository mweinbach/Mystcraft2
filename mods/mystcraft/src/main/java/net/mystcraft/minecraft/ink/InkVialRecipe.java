package net.mystcraft.minecraft.ink;

import net.mystcraft.integration.registry.InkMixerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Classic two black dyes and either a water bottle, or a bottle plus water bucket. */
public final class InkVialRecipe extends CustomRecipe {
    private static final TagKey<Item> FORGE_BLACK_DYES = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "dyes/black"));
    private static final TagKey<Item> COMMON_BLACK_DYES = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("c", "black_dyes"));
    public InkVialRecipe(ResourceLocation id) { super(id); }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        int dye = 0, water = 0, buckets = 0, bottles = 0;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) continue;
            if ((stack.is(Items.BLACK_DYE) || stack.is(Items.INK_SAC) || stack.is(FORGE_BLACK_DYES) || stack.is(COMMON_BLACK_DYES))) dye++;
            else if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER && PotionUtils.getCustomEffects(stack).isEmpty()) water++;
            else if (stack.is(Items.WATER_BUCKET)) buckets++;
            else if (stack.is(Items.GLASS_BOTTLE)) bottles++;
            else return false;
        }
        return dye == 2 && ((water == 1 && buckets == 0 && bottles == 0) || (water == 0 && buckets == 1 && bottles == 1));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        // The water bottle becomes the vial. Global potion-remainder mixins must not duplicate it.
        NonNullList<ItemStack> remaining = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (grid.getItem(i).is(Items.WATER_BUCKET)) remaining.set(i, new ItemStack(Items.BUCKET));
        }
        return remaining;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid) { return new ItemStack(InkMixerRegistry.INK_VIAL.get()); }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 3; }

    @Override
    public RecipeSerializer<?> getSerializer() { return InkMixerRegistry.INK_VIAL_RECIPE.get(); }
}
