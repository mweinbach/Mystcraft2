package ports.jurassicraft.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinoScannerGameTests {
    private DinoScannerGameTests() {}

    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void normalScanAndSneakDrain(GameTestHelper helper) {
        var dinosaur = helper.spawnWithNoFreeWill(DinosaurRegistry.type(Species.VELOCIRAPTOR), new BlockPos(1, 1, 1));
        var player = helper.makeMockPlayer();
        ItemStack scanner = new ItemStack(DinoScannerRegistry.SCANNER.get());
        int original = dinosaur.energy();
        InteractionResult result = scanner.interactLivingEntity(player, dinosaur, InteractionHand.MAIN_HAND);
        if (!result.consumesAction() || dinosaur.energy() != original) helper.fail("Normal scanning must not change energy");
        player.setShiftKeyDown(true);
        result = scanner.interactLivingEntity(player, dinosaur, InteractionHand.MAIN_HAND);
        if (!result.consumesAction() || dinosaur.energy() != Math.max(0, original - 5000)) helper.fail("Sneak scanning must drain at most 5000 energy");
        helper.succeed();
    }
}
