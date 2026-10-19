package com.technovision.advancedgenetics.gametest;

import com.technovision.advancedgenetics.AdvancedGenetics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;

public class EmcGameTests {

    @GameTest(template = TEMPLATE)
    public static void everyItemHasAnEmcDecision(GameTestHelper helper) {
        List<String> failures = EmcAudit.run(helper.getLevel().getServer(), AdvancedGenetics.MOD_ID);
        if (!failures.isEmpty()) helper.fail(EmcAudit.summary(failures));
        helper.succeed();
    }
}
