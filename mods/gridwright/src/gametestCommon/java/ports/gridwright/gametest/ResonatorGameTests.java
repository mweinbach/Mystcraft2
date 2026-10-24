package ports.gridwright.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
