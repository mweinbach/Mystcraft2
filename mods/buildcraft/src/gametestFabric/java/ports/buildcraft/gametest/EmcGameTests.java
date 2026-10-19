package ports.buildcraft.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.buildcraft.BuildCraft;

public final class EmcGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTests.TEMPLATE)
    public void everyItemHasAnEmcDecision(GameTestHelper h) {
        List<String> failures = EmcAudit.run(h.getLevel().getServer(), BuildCraft.MOD_ID);
        if (!failures.isEmpty()) h.fail(EmcAudit.summary(failures));
        h.succeed();
    }
}
