package ports.jurassicraft.entity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import ports.jurassicraft.gametest.DinosaurAnimationChecks;

public final class DinosaurProceduralFabricGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void proceduralMotionAndAttackPose(GameTestHelper helper) {
        DinosaurAnimationChecks.verify(helper);
        helper.succeed();
    }
}
