package net.mystcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.mystcraft.fabric.FabricAgeBackend;

/** Forwards the shared {@link MixinCompatibilityTests} on Fabric. */
public final class MixinCompatibilityGameTests implements FabricGameTest {
    public MixinCompatibilityGameTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void allLevelsTracksAges(GameTestHelper helper) {
        MixinCompatibilityTests.allLevelsTracksAges(helper, new FabricAgeBackend());
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void weatherWrapsKeepVanillaOutsideAges(GameTestHelper helper) {
        MixinCompatibilityTests.weatherWrapsKeepVanillaOutsideAges(helper, new FabricAgeBackend());
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void legacyTreasureLootMatchesJsonHook(GameTestHelper helper) {
        MixinCompatibilityTests.legacyTreasureLootMatchesJsonHook(helper);
    }
}
