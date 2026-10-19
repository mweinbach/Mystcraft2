package ports.jurassicraft.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;

public final class DinoScannerGameTests implements FabricGameTest {
    public DinoScannerGameTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void normalScanAndSneakDrain(GameTestHelper helper) {
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
