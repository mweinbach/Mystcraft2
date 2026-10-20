/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.extremereactors.platform.Platform;

import java.util.List;

/**
 * The Forge entry point of the shared game tests in {@link ExtremeReactorsGameTests}.
 */
@GameTestHolder(Platform.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtremeReactorsForgeGameTests {

    @GameTest(template = ExtremeReactorsGameTests.EMPTY_TEMPLATE, templateNamespace = Platform.MOD_ID)
    public static void everyItemHasAnEmcDecision(final GameTestHelper helper) {

        final List<String> failures = EmcAudit.run(helper.getLevel().getServer(), Platform.MOD_ID);

        if (!failures.isEmpty()) {
            helper.fail(EmcAudit.summary(failures));
        }

        helper.succeed();
    }

    private ExtremeReactorsForgeGameTests() {
    }
}
