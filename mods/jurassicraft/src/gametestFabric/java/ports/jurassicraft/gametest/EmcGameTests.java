package ports.jurassicraft.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.jurassicraft.PortMod;

public final class EmcGameTests implements FabricGameTest {
    public EmcGameTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyItemHasAnEmcDecision(GameTestHelper h) {
        List<String> failures = EmcAudit.run(h.getLevel().getServer(), PortMod.MOD_ID);
        if (!failures.isEmpty()) h.fail(EmcAudit.summary(failures));
        h.succeed();
    }
}
