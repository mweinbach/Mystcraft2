package net.mystcraft.smoke.forge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.mystcraft.smoke.SmokeGameTestRun;

/** The full Forge smoke suite as one GameTest, for {@code runSmokeGameTestServer}. Registered only when GameTests are enabled. */
@GameTestHolder("mystcraft_smoke")
@PrefixGameTestTemplate(false)
public final class ForgeSmokeGameTest {
    private ForgeSmokeGameTest() {}

    @GameTest(template = "floor", timeoutTicks = SmokeGameTestRun.TIMEOUT_TICKS)
    public static void fullSuite(GameTestHelper helper) {
        SmokeGameTestRun.start(helper);
    }
}
