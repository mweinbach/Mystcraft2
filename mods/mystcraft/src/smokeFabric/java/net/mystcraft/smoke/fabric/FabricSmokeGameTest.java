package net.mystcraft.smoke.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.mystcraft.smoke.SmokeGameTestRun;

/** The full Fabric smoke suite as one GameTest, for {@code runSmokeGametest}. Fabric only loads it when GameTests are enabled. */
public final class FabricSmokeGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = SmokeGameTestRun.TIMEOUT_TICKS)
    public void fullSuite(GameTestHelper helper) {
        SmokeGameTestRun.start(helper);
    }
}
