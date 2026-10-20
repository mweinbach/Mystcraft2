/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.extremereactors.platform.Platform;

import java.util.List;

/**
 * The Fabric entry point of the shared game tests in {@link ExtremeReactorsGameTests}.
 */
public final class ExtremeReactorsFabricGameTests implements FabricGameTest {

    private static final String EMPTY = Platform.MOD_ID + ":" + ExtremeReactorsGameTests.EMPTY_TEMPLATE;

    @GameTest(template = EMPTY)
    public void everyItemHasAnEmcDecision(final GameTestHelper helper) {

        final List<String> failures = EmcAudit.run(helper.getLevel().getServer(), Platform.MOD_ID);

        if (!failures.isEmpty()) {
            helper.fail(EmcAudit.summary(failures));
        }

        helper.succeed();
    }
}
