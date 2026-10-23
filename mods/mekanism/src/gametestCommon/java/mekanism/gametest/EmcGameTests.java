/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;

public class EmcGameTests {

    @GameTest(template = TEMPLATE)
    public static void everyItemHasAnEmcDecision(GameTestHelper helper) {
        List<String> failures = EmcAudit.run(helper.getLevel().getServer());
        if (!failures.isEmpty()) helper.fail(EmcAudit.summary(failures));
        helper.succeed();
    }
}
