package ports.jurassicraft.figure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;

public final class ActionFigureGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    public ActionFigureGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void craftAndCycleEveryFigure(GameTestHelper h) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer grid = new CraftingContainer(menu, 3, 3);
        grid.setItem(1, new ItemStack(Items.CLAY_BALL));
        for (int i = 3; i <= 5; i++) grid.setItem(i, new ItemStack(Items.OAK_SLAB));
        var starter = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel());
        check(h, starter.isPresent() && starter.get().getId().equals(new ResourceLocation(PortMod.MOD_ID, "action_figure")),
                "clay and three wooden slabs must craft the starting figure");
        ItemStack figure = starter.get().assemble(grid);
        check(h, figure.is(ActionFigureRegistry.ITEM.get()) && ActionFigureItem.species(figure) == Species.BRACHIOSAURUS,
                "starter figure must be brachiosaurus");
        grid.clearContent();
        ActionFigureCycleRecipe recipe = new ActionFigureCycleRecipe(new ResourceLocation(PortMod.MOD_ID, "test_cycle"));
        for (int i = 0; i < ActionFigureCycleRecipe.ORDER.size(); i++) {
            check(h, ActionFigureItem.species(figure) == ActionFigureCycleRecipe.ORDER.get(i), "figure cycle order must match upstream");
            grid.setItem(0, figure);
            check(h, recipe.matches(grid, h.getLevel()), "one figure must match the cycle recipe");
            figure = recipe.assemble(grid);
        }
        check(h, ActionFigureItem.species(figure) == Species.BRACHIOSAURUS, "figure cycle must wrap to the starter");
        grid.setItem(1, new ItemStack(Items.DIRT));
        check(h, !recipe.matches(grid, h.getLevel()), "extra items cannot match the cycle recipe");
        for (Species species : Species.values()) {
            ItemStack loot = ActionFigureItem.create(species);
            check(h, loot.is(ActionFigureRegistry.ITEM.get()) && ActionFigureItem.species(loot) == species,
                    "loot factory must retain the requested species");
        }
        h.succeed();
    }

    @GameTest(template = "jurassicraft:cleaning_station_test")
    public void placementGenderPersistenceAndSupport(GameTestHelper h) {
        BlockPos support = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos pos = support.above();
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        Player player = h.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(support.getX() + 1.5, support.getY() + 1, support.getZ() + 1.5);
        player.setYHeadRot(33);
        ItemStack item = ActionFigureItem.create(Species.TYRANNOSAURUS);
        item.getOrCreateTagElement("BlockEntityTag").putInt("Gender", 2);
        player.setItemInHand(InteractionHand.OFF_HAND, item);
        var click = new UseOnContext(player, InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        check(h, item.useOn(click).consumesAction() && item.isEmpty(), "figure placement must consume one from the used hand");
        check(h, h.getLevel().getBlockEntity(pos) instanceof ActionFigureBlockEntity, "placement must create a display block entity");
        ActionFigureBlockEntity figure = (ActionFigureBlockEntity) h.getLevel().getBlockEntity(pos);
        check(h, figure.species() == Species.TYRANNOSAURUS && figure.gender() == 2 && figure.rotation() == 147,
                "chosen species, gender and head rotation must be applied");
        CompoundTag saved = figure.saveWithoutMetadata();
        ActionFigureBlockEntity restored = new ActionFigureBlockEntity(pos, figure.getBlockState()); restored.load(saved);
        check(h, restored.species() == figure.species() && restored.gender() == figure.gender() && restored.rotation() == figure.rotation(),
                "figure appearance and rotation must persist");
        var dropped = Block.getDrops(figure.getBlockState(), h.getLevel(), pos, figure);
        check(h, dropped.size() == 1 && ActionFigureItem.species(dropped.get(0)) == Species.TYRANNOSAURUS
                && ActionFigureItem.gender(dropped.get(0)) == 2, "drops must retain species and gender");
        ItemStack picked = figure.getBlockState().getBlock().getCloneItemStack(h.getLevel(), pos, figure.getBlockState());
        check(h, ActionFigureItem.species(picked) == Species.TYRANNOSAURUS && ActionFigureItem.gender(picked) == 2,
                "pick block must retain species and gender");
        h.getLevel().setBlock(support, Blocks.AIR.defaultBlockState(), 3);
        check(h, h.getLevel().getBlockState(pos).isAir(), "figure must detach when its support is removed");
        check(h, !ActionFigureRegistry.BLOCK.get().defaultBlockState().canSurvive(h.getLevel(), pos), "figure must require solid support");
        h.succeed();
    }
}
