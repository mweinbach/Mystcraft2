package ports.jurassicraft.goat;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoatGameTests {
    private GoatGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void milkingRequiresAdultNannyAndGrassRestoresMilk(GameTestHelper helper) {
        JurassicGoat goat = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(1, 1, 1));
        goat.setBilly(false);
        goat.setBaby(false);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.MILK_BUCKET) && goat.isMilked(), "nanny must give milk");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "nanny must not give milk twice before eating");
        goat.ate();
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.MILK_BUCKET), "eating must restore milk");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.ate(); goat.setBilly(true);
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "billy must not give milk");
        goat.setBilly(false); goat.setBaby(true);
        goat.mobInteract(player, InteractionHand.MAIN_HAND);
        require(helper, player.getMainHandItem().is(Items.BUCKET), "kid must not give milk");
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void breedingAndSavedState(GameTestHelper helper) {
        JurassicGoat first = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(0, 1, 1));
        JurassicGoat second = helper.spawnWithNoFreeWill(GoatRegistry.GOAT.get(), new BlockPos(2, 1, 1));
        first.setBaby(false); second.setBaby(false); first.setBilly(true); second.setBilly(false);
        first.setInLove(null); second.setInLove(null);
        require(helper, first.canMate(second), "opposite-sex adults in love should breed");
        second.setBilly(true);
        require(helper, !first.canMate(second), "same-sex goats must not breed");
        require(helper, first.getBreedOffspring(helper.getLevel(), second) != null, "goat breeding must create a kid");
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Billy", true); tag.putInt("Variant", 2); tag.putBoolean("Milked", true);
        first.readAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        first.addAdditionalSaveData(saved);
        require(helper, saved.getBoolean("Billy") && saved.getInt("Variant") == 2 && saved.getBoolean("Milked"), "goat state did not persist");
        require(helper, first.variant().equals("jpog"), "variant is not preserved");
        helper.succeed();
    }
}
