package ports.gridwright.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.gridwright.Gridwright;

import java.util.List;

import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;

public class EmcGameTests {

    @GameTest(template = TEMPLATE)
    public static void everyItemHasAnEmcDecision(GameTestHelper helper) {
        List<String> failures = EmcAudit.run(helper.getLevel().getServer(), Gridwright.MOD_ID);
        if (!failures.isEmpty()) helper.fail(EmcAudit.summary(failures));
        helper.succeed();
    }
}
