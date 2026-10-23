package ports.jurassicraft.gametest;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;

public final class CoreRecipeGameTests {
    private static ResourceLocation id(String path) { return new ResourceLocation("jurassicraft", path); }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public static void coreRecipesAreLoadedAndPlasterCanBeCrafted(GameTestHelper helper) {
        Map<String, String> outputs = Map.of(
                "gypsum_bricks", "gypsum_bricks",
                "gypsum_stone", "gypsum_stone",
                "gypsum_stone_from_smelting", "gypsum_stone",
                "plaster_and_bandage", "plaster_and_bandage",
                "reinforced_bricks", "reinforced_bricks",
                "reinforced_bricks_from_reinforced_stone", "reinforced_bricks",
                "reinforced_stone", "reinforced_stone",
                "unfinished_car", "unfinished_car");
        var recipes = helper.getLevel().getRecipeManager();
        for (var entry : outputs.entrySet()) {
            var recipe = recipes.byKey(id(entry.getKey()));
            var item = Registry.ITEM.getOptional(id(entry.getValue()));
            if (recipe.isEmpty() || item.isEmpty() || !recipe.get().getResultItem().is(item.get())) {
                helper.fail("missing recipe or incorrect result: " + entry.getKey());
            }
        }

        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        };
        CraftingContainer grid = new CraftingContainer(menu, 3, 3);
        var gypsum = Registry.ITEM.get(id("gypsum_powder"));
        for (int slot : new int[] {0, 2, 6, 8}) grid.setItem(slot, new ItemStack(Items.PAPER));
        for (int slot : new int[] {1, 3, 5, 7}) grid.setItem(slot, new ItemStack(gypsum));
        grid.setItem(4, new ItemStack(Items.WHITE_WOOL));
        var plaster = recipes.byKey(id("plaster_and_bandage")).orElseThrow();
        if (!(plaster instanceof CraftingRecipe crafting) || !crafting.matches(grid, helper.getLevel())
                || crafting.assemble(grid).getCount() != 9) {
            helper.fail("paper, gypsum and wool must craft nine plaster bandages");
        }
        helper.succeed();
    }
}
