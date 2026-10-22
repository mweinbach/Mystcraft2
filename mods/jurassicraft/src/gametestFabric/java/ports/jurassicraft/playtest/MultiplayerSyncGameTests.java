package ports.jurassicraft.playtest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Two mock players sharing vehicles, a feeder, a fence and a breeding result. */
public final class MultiplayerSyncGameTests implements FabricGameTest {
    public MultiplayerSyncGameTests() {}

    @GameTest(template = "jurassicraft_gametest:vehicle_test")
    public void twoPlayersShareVehiclesMenusFencesAndBreeding(GameTestHelper helper) {
        List<String> failures = MultiplayerSyncAudit.failures(helper);
        if (!failures.isEmpty()) helper.fail(String.join(" | ", failures));
        helper.succeed();
    }
}
