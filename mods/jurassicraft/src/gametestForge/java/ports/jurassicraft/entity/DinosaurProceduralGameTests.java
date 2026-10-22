package ports.jurassicraft.entity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.gametest.DinosaurAnimationChecks;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DinosaurProceduralGameTests {
    private DinosaurProceduralGameTests() {}

    @GameTest(template = "feeder_test", templateNamespace = PortMod.MOD_ID)
    public static void proceduralMotionAndAttackPose(GameTestHelper helper) {
        DinosaurAnimationChecks.verify(helper);
        helper.succeed();
    }
}
