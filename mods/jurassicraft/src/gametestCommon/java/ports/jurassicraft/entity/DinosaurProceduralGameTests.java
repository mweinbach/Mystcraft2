package ports.jurassicraft.entity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.jurassicraft.gametest.DinosaurAnimationChecks;

public final class DinosaurProceduralGameTests {
    private DinosaurProceduralGameTests() {}

    @GameTest(template = "jurassicraft:feeder_test")
    public static void proceduralMotionAndAttackPose(GameTestHelper helper) {
        DinosaurAnimationChecks.verify(helper);
        helper.succeed();
    }
}
