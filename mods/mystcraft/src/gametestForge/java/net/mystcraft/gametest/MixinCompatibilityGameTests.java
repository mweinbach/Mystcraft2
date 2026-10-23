package net.mystcraft.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.mystcraft.integration.MystcraftBootstrap;
import net.mystcraft.integration.platform.ForgeAgeBackend;

/** Forwards the shared {@link MixinCompatibilityTests} on Forge. */
@GameTestHolder(MystcraftBootstrap.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MixinCompatibilityGameTests {
    private static final String TEMPLATE = "village/archivist_house";

    private MixinCompatibilityGameTests() {}

    @GameTest(template = TEMPLATE, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void allLevelsTracksAges(GameTestHelper helper) {
        MixinCompatibilityTests.allLevelsTracksAges(helper, new ForgeAgeBackend());
    }

    @GameTest(template = TEMPLATE, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void weatherWrapsKeepVanillaOutsideAges(GameTestHelper helper) {
        MixinCompatibilityTests.weatherWrapsKeepVanillaOutsideAges(helper, new ForgeAgeBackend());
    }

    @GameTest(template = TEMPLATE, templateNamespace = MystcraftBootstrap.MOD_ID)
    public static void legacyTreasureLootMatchesJsonHook(GameTestHelper helper) {
        MixinCompatibilityTests.legacyTreasureLootMatchesJsonHook(helper);
    }
}
