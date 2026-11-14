package net.mystcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.mystcraft.integration.MystcraftItems;

/** Loaded recipe and server-side use, exercised unchanged by both loaders. */
public final class GuideGameplayTests {
    private GuideGameplayTests() {}

    public static void craftAndReadGuide(GameTestHelper helper) {
        var level = helper.getLevel();
        CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        }, 3, 3);
        grid.setItem(0, new ItemStack(Items.BOOK));
        grid.setItem(4, new ItemStack(Items.FEATHER));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .orElseThrow(() -> new AssertionError("Guide recipe was not loaded"));
        if (!recipe.getId().equals(new ResourceLocation("mystcraft", "guide_book")))
            helper.fail("Book and feather matched the wrong recipe: " + recipe.getId());
        ItemStack guide = recipe.assemble(grid);
        if (!guide.is(MystcraftItems.GUIDE_BOOK.get()) || guide.getCount() != 1)
            helper.fail("Guide recipe produced the wrong item");

        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.fromString("b94c8286-9def-49fb-a479-5cf22daeb06c"), "GuideTest"), null);
        for (InteractionHand hand : InteractionHand.values()) {
            player.setItemInHand(hand, guide);
            var result = guide.use(level, player, hand);
            if (result.getResult() != InteractionResult.CONSUME || result.getObject() != guide
                    || player.getItemInHand(hand) != guide || guide.getCount() != 1)
                helper.fail("Reading the guide changed the held item in " + hand);
        }
        helper.succeed();
    }
}
