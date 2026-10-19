package ports.jurassicraft.content;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.plants.PlantRegistry;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MossRecipeGameTests {
    private MossRecipeGameTests() {}

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void mossMakesMossyStone(GameTestHelper helper) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer grid = new CraftingContainer(menu, 3, 3);
        checkRecipe(helper, grid, Items.STONE_BRICKS, Items.MOSSY_STONE_BRICKS, "mossy_stone_bricks_from_moss");
        checkRecipe(helper, grid, Items.COBBLESTONE, Items.MOSSY_COBBLESTONE, "mossy_cobblestone_from_moss");
        helper.succeed();
    }

    private static void checkRecipe(GameTestHelper helper, CraftingContainer grid, Item stone, Item result, String id) {
        for (int slot = 0; slot < 9; slot++)
            grid.setItem(slot, new ItemStack((slot % 2 == 0) ? PlantRegistry.MOSS.get().asItem() : stone));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
        if (recipe.isEmpty() || !recipe.get().getId().equals(new ResourceLocation(PortMod.MOD_ID, id))) {
            helper.fail("expected JurassiCraft moss recipe: " + id);
            return;
        }
        ItemStack output = recipe.get().assemble(grid);
        if (!output.is(result) || output.getCount() != 4) helper.fail("recipe must produce four mossy blocks: " + id);
        grid.setItem(4, new ItemStack(Items.DIRT));
        if (recipe.get().matches(grid, helper.getLevel())) helper.fail("recipe must require all five moss items: " + id);
    }
}
