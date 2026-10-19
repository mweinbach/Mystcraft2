package ports.jurassicraft.egg;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationBlockEntity;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.grinder.FossilGrinderRecipes;

/** Integration checks for nest wrapping, cleaning, and fossil egg grinding. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FossilNestGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private FossilNestGameTests() {}

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void nestWrappingAndCleaning(GameTestHelper helper) {
        RandomSource random = RandomSource.create(73420L);
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        for (int variant = 0; variant < 3; variant++) {
            var raw = FossilNestRegistry.nest(variant, false).get();
            var encased = FossilNestRegistry.nest(variant, true).get();
            helper.setBlock(POS, raw);
            ItemStack plaster = new ItemStack(JurassicContent.PLASTER_AND_BANDAGE.get(), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, plaster);
            BlockPos absolute = helper.absolutePos(POS);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            InteractionResult use = plaster.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            require(helper, use.consumesAction(), "plaster must wrap the nest");
            require(helper, helper.getBlockState(POS).is(encased), "wrapping must preserve the nest variant");
            require(helper, plaster.getCount() == 1, "wrapping must consume one plaster");
            require(helper, !CleaningStationBlockEntity.isCleanable(new ItemStack(raw)), "raw nest must not be cleanable");
            ItemStack input = new ItemStack(encased);
            require(helper, CleaningStationBlockEntity.isCleanable(input), "encased nest must be cleanable");
            var nest = (FossilNestBlock) encased;
            int eggs = 0, flint = 0, boneMeal = 0;
            for (int i = 0; i < 6000; i++) {
                ItemStack output = nest.clean(input, random);
                if (output.is(FossilizedEggRegistry.variant(variant).get())) {
                    eggs++;
                    require(helper, output.getCount() >= 1 && output.getCount() <= 7, "nest must yield 1-7 matching eggs");
                } else if (output.is(Items.FLINT)) flint++;
                else if (output.is(Items.BONE_MEAL)) boneMeal++;
                else helper.fail("unexpected cleaning result");
            }
            require(helper, eggs > 2700 && eggs < 3300 && flint > 1250 && flint < 1750
                    && boneMeal > 1250 && boneMeal < 1750, "nest outcomes must be close to 50% / 25% / 25%");
        }
        helper.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void eggGrinderOutputs(GameTestHelper helper) {
        RandomSource random = RandomSource.create(4321L);
        for (int variant = 0; variant < 3; variant++) {
            ItemStack input = new ItemStack(FossilizedEggRegistry.variant(variant).get());
            input.getOrCreateTag().putString("SampleMarker", "fossilized egg");
            require(helper, FossilGrinderRecipes.accepts(input), "grinder must accept each fossilized egg variant");
            Set<Item> tissues = new HashSet<>();
            int boneMeal = 0, flint = 0, tissueCount = 0;
            for (int i = 0; i < 9000; i++) {
                ItemStack output = FossilGrinderRecipes.grind(input, random);
                if (output.is(Items.BONE_MEAL)) {
                    boneMeal++;
                    require(helper, !output.hasTag(), "bone meal must not receive egg NBT");
                } else if (output.is(Items.FLINT)) {
                    flint++;
                    require(helper, !output.hasTag(), "flint must not receive egg NBT");
                } else {
                    tissueCount++;
                    tissues.add(output.getItem());
                    require(helper, output.getTag() != null && "fossilized egg".equals(output.getTag().getString("SampleMarker")), "tissue must keep egg NBT");
                    output.getTag().putString("SampleMarker", "changed");
                    require(helper, "fossilized egg".equals(input.getTag().getString("SampleMarker")), "tissue NBT must be copied");
                }
            }
            Set<Item> expectedTissues = new HashSet<>();
            for (Species species : Species.values()) expectedTissues.add(GeneticsRegistry.softTissue(species).get());
            require(helper, tissues.equals(expectedTissues), "egg tissue must select among all prehistoric species");
            require(helper, boneMeal > 2700 && boneMeal < 3300 && flint > 2700 && flint < 3300
                    && tissueCount > 2700 && tissueCount < 3300, "grinding outcomes must be close to one-third each");
        }
        helper.succeed();
    }
}
