/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import it.zerono.mods.extremereactors.api.turbine.CoilMaterial;
import it.zerono.mods.extremereactors.api.turbine.CoilMaterialRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.extremereactors.corelib.tag.TagsHelper;
import ports.extremereactors.fabric.api.ExtremeReactorsApiEntrypoint;
import ports.extremereactors.platform.Platform;

import java.util.Optional;

/**
 * A test add-on on the {@value ExtremeReactorsApiEntrypoint#KEY} entrypoint, and the Fabric-only test that its
 * messages arrived the way Forge delivers inter-mod messages.
 * <p>
 * It sends the removal of one coil before registering it and another one: Extreme Reactors must still run every
 * registration before any removal, and the API calls the messages make must apply.
 */
public final class FabricApiEntrypointGameTest implements ExtremeReactorsApiEntrypoint, FabricGameTest {

    private static final String EMPTY = Platform.MOD_ID + ":" + ExtremeReactorsGameTests.EMPTY_TEMPLATE;
    private static final String KEPT_COIL = "bigreactors_gametest:api_entrypoint_kept";
    private static final String REMOVED_COIL = "bigreactors_gametest:api_entrypoint_removed";

    @Override
    public void sendApiMessages(final Messages messages) {

        messages.send("coilmaterial-remove", () -> CoilMaterialRegistry.remove(REMOVED_COIL));
        messages.send("coilmaterial-register", () -> {

            CoilMaterialRegistry.register(KEPT_COIL, 3.5f, 1.25f, 2.0f);
            CoilMaterialRegistry.register(REMOVED_COIL, 1.0f, 1.0f, 1.0f);
        });
        messages.send("not-an-extreme-reactors-method", () -> {
            throw new AssertionError("A message for an unknown method was run");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = ExtremeReactorsGameTests.SHORT_TIMEOUT)
    public void apiEntrypointMessagesApplyLikeImc(final GameTestHelper helper) {

        final Optional<CoilMaterial> kept = CoilMaterialRegistry.get(TagsHelper.BLOCKS.createKey(KEPT_COIL));

        check(kept.isPresent(), "the coil registered through bigreactors:api is missing");
        check(3.5f == kept.get().getEfficiency() && 1.25f == kept.get().getBonus() &&
                2.0f == kept.get().getEnergyExtractionRate(), "the coil registered through bigreactors:api has other values");
        check(!CoilMaterialRegistry.contains(TagsHelper.BLOCKS.createKey(REMOVED_COIL)),
                "a bigreactors:api removal did not run after the registrations");
        helper.succeed();
    }

    private static void check(final boolean condition, final String message) {

        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
