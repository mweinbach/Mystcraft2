package ports.jurassicraft.playtest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

/** Two mock players sharing vehicles, a feeder, a fence and a breeding result. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultiplayerSyncGameTests {
    private MultiplayerSyncGameTests() {}

    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void twoPlayersShareVehiclesMenusFencesAndBreeding(GameTestHelper helper) {
        List<String> failures = MultiplayerSyncAudit.failures(helper);
        if (!failures.isEmpty()) helper.fail(String.join(" | ", failures));
        helper.succeed();
    }
}
