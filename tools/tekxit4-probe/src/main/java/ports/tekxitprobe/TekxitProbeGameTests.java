package ports.tekxitprobe;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Fails the headless GameTest run when the server checks found a problem, so the harness exit
 * code reflects port boot health. Select with {@code -Dtekxitprobe.tests=TekxitProbeGameTests}.
 */
public final class TekxitProbeGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void portsBooted(GameTestHelper helper) {
        JsonObject report = ServerChecks.run(helper.getLevel().getServer());
        TekxitProbe.write("server-report-gametest.json", TekxitProbe.GSON.toJson(report));
        if (report.getAsJsonArray("failures").size() > 0) {
            helper.fail("Tekxit probe: " + report.getAsJsonArray("failures"));
        }
        helper.succeed();
    }
}
