package ports.gridwright.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import ports.gridwright.Gridwright;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity.Status;
import ports.gridwright.recipe.ResonatingRecipe;
import ports.gridwright.registry.ModItems;
import ports.gridwright.registry.ModRecipes;

import java.util.List;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.platform;
import static ports.gridwright.gametest.GameTestSupport.resonator;

/** The resonator's recipes, face rules and blocking behaviour (Grid Power itself is in GridGameTests). */
public class ResonatorGameTests {

    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static ResonatingRecipe recipe(GameTestHelper helper, String path) {
        ResourceLocation id = new ResourceLocation(Gridwright.MOD_ID, path);
        return helper.getLevel().getRecipeManager().byKey(id)
                .filter(ResonatingRecipe.class::isInstance).map(ResonatingRecipe.class::cast)
                .orElseThrow(() -> new IllegalStateException("Missing resonating recipe " + id));
    }

    @GameTest(template = TEMPLATE)
    public static void resonatingRecipesLoad(GameTestHelper helper) {
        List<ResonatingRecipe> all = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.RESONATING.get());
        check(all.size() >= 3, "Expected the shipped resonating recipes, found " + all.size());
        ResonatingRecipe crystal = recipe(helper, "resonating/grid_crystal");
        check(crystal.accepts(new ItemStack(Items.REDSTONE)) && crystal.amount() == 1, "Crystal input");
        check(crystal.output().is(ModItems.GRID_CRYSTAL.get()) && crystal.output().getCount() == 1, "Crystal output");
        check(crystal.gp() == 4.0 && crystal.time() == 100, "Crystal cost " + crystal.gp() + " GP for " + crystal.time());
        check(recipe(helper, "resonating/leather").amount() == 3, "Leather takes three rotten flesh");

        // The recipe survives the trip to clients unchanged.
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ResonatingRecipe.Serializer serializer = (ResonatingRecipe.Serializer) crystal.getSerializer();
        serializer.toNetwork(buffer, crystal);
        ResonatingRecipe copy = serializer.fromNetwork(crystal.getId(), buffer);
        check(copy.amount() == crystal.amount() && copy.gp() == crystal.gp() && copy.time() == crystal.time()
                && ItemStack.matches(copy.output(), crystal.output()) && copy.accepts(new ItemStack(Items.REDSTONE))
                && !copy.accepts(new ItemStack(Items.DIRT)), "Network copy differs");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void resonatorFacesFollowRecipes(GameTestHelper helper) {
        ResonatorBlockEntity resonator = resonator(helper, POS, null);
        TestPlatform.ItemAccess items = platform().items(resonator, Direction.UP);
        check(!items.insert(new ItemStack(Items.DIRT)).isEmpty(), "Accepted an item no recipe uses");
        check(items.insert(new ItemStack(Items.REDSTONE, 3)).isEmpty(), "Refused a recipe input");
        check(resonator.getItem(0).is(Items.REDSTONE) && resonator.getItem(0).getCount() == 3, "Input slot " + resonator.getItem(0));
        check(items.extract(0, 1, true).isEmpty(), "Inputs must not be pulled back out");
        resonator.setItem(1, new ItemStack(ModItems.GRID_CRYSTAL.get(), 2));
        ItemStack out = platform().items(resonator, Direction.DOWN).extract(1, 64, false);
        check(out.is(ModItems.GRID_CRYSTAL.get()) && out.getCount() == 2, "Output not extractable: " + out);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void resonatorWaitsForOutputSpace(GameTestHelper helper) {
        ResonatorBlockEntity resonator = resonator(helper, POS, Owner.random("gwM"));
        resonator.setItem(0, new ItemStack(Items.REDSTONE));
        resonator.setItem(1, new ItemStack(Items.FLINT));
        helper.startSequence()
                .thenIdle(2)
                .thenExecuteFor(10, () -> check(resonator.status() == Status.BLOCKED && resonator.progress() == 0,
                        "Expected BLOCKED, got " + resonator.status()))
                .thenExecute(() -> resonator.setItem(1, ItemStack.EMPTY))
                .thenWaitUntil(() -> check(resonator.status() != Status.BLOCKED, "Still blocked: " + resonator.status()))
                .thenSucceed();
    }

    /** Saved progress and the recipe it belongs to survive a reload; a different recipe starts over. */
    @GameTest(template = TEMPLATE)
    public static void progressSurvivesReload(GameTestHelper helper) {
        Owner owner = Owner.random("gwO");
        ResonatorBlockEntity source = resonator(helper, POS, owner);
        source.setItem(0, new ItemStack(Items.REDSTONE));
        CompoundTag saved = source.saveWithoutMetadata();
        saved.putInt("Progress", 50);
        saved.putString("Recipe", Gridwright.MOD_ID + ":resonating/grid_crystal");
        // Owned but with no producers, so the grid is overloaded and progress must neither move nor reset.
        ResonatorBlockEntity same = resonator(helper, new BlockPos(4, 2, 2), null);
        same.load(saved.copy());
        CompoundTag other = saved.copy();
        other.putString("Recipe", Gridwright.MOD_ID + ":resonating/flint");
        ResonatorBlockEntity changed = resonator(helper, new BlockPos(6, 2, 2), null);
        changed.load(other);
        helper.startSequence()
                .thenIdle(3)
                .thenExecuteFor(10, () -> {
                    check(same.recipe() != null && same.recipe().output().is(ModItems.GRID_CRYSTAL.get()), "Recipe not resolved");
                    check(same.status() == Status.OVERLOADED && same.progress() == 50,
                            "Restored progress lost: " + same.progress() + " (" + same.status() + ")");
                    check(changed.progress() == 0, "Progress from another recipe kept: " + changed.progress());
                })
                .thenExecute(() -> check(same.saveWithoutMetadata().getString("Recipe").equals(Gridwright.MOD_ID + ":resonating/grid_crystal"),
                        "Recipe id not saved"))
                .thenSucceed();
    }

    /** A cached recipe the recipe manager no longer holds (a datapack reload) is looked up again. */
    @GameTest(template = TEMPLATE)
    public static void staleCachedRecipeIsReplaced(GameTestHelper helper) {
        ResonatorBlockEntity resonator = resonator(helper, POS, Owner.random("gwP"));
        resonator.setItem(0, new ItemStack(Items.REDSTONE));
        ResonatingRecipe live = recipe(helper, "resonating/grid_crystal");
        ResonatingRecipe stale = new ResonatingRecipe(live.getId(), Ingredient.of(Items.REDSTONE), 1,
                new ItemStack(Items.DIAMOND), 0.0, 1);
        helper.startSequence()
                .thenWaitUntil(() -> check(resonator.recipe() == live, "Recipe not resolved"))
                .thenExecute(() -> GameTestSupport.setField(ResonatorBlockEntity.class, "recipe", resonator, stale))
                .thenIdle(2)
                .thenExecute(() -> {
                    check(resonator.recipe() == live, "Kept a recipe the manager no longer holds: " + resonator.recipe());
                    check(!resonator.getItem(1).is(Items.DIAMOND), "The stale recipe ran");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void idleWithoutInput(GameTestHelper helper) {
        Owner owner = Owner.random("gwN");
        ResonatorBlockEntity resonator = resonator(helper, POS, owner);
        helper.startSequence()
                .thenIdle(2)
                .thenExecuteFor(10, () -> {
                    check(resonator.status() == Status.IDLE && resonator.recipe() == null, "Expected IDLE, got " + resonator.status());
                    check(GameTestSupport.snapshot(helper, owner.gridKey()).consumers() == 0, "An idle resonator asked for power");
                })
                .thenSucceed();
    }
}
