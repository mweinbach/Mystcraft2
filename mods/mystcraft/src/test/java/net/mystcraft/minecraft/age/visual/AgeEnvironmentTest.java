package net.mystcraft.minecraft.age.visual;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.age.AgeWeatherRules;
import net.mystcraft.minecraft.writing.PageData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeEnvironmentTest {
    private static CompoundTag page(String id) { CompoundTag tag = new CompoundTag(); PageData.setLegacySymbol(tag, "mystcraft:" + id); return tag; }
    private static AgeVisualSettings compile(String... ids) { return AgeVisualCompiler.parse(Arrays.stream(ids).map(AgeEnvironmentTest::page).toList()).settings(); }
    @Test void staticColorsPreserveOriginalFirstProviderBugAndIndependentChannels() {
        var s = compile("ModColorRed", "ColorGrass", "ModColorBlue", "ColorGrass", "ColorFoliageNat", "ModColorGreen", "ColorFoliage");
        assertEquals(0xFF0000, AgeVisualMath.staticColor(s.environment().grass(), 0x123456));
        assertEquals(0x123456, AgeVisualMath.staticColor(s.environment().foliage(), 0x123456));
    }
    @Test void cloudMathMatchesSourceDistinctNightAndWeatherWeights() {
        var s = compile("ModColorRed", "ColorCloud");
        assertEquals(new AgeVisualSettings.Rgb(1, 0, 0), AgeVisualMath.cloud(s, 0, 0, 0, 0, 0));
        assertEquals(new AgeVisualSettings.Rgb(.1F, 0, 0), AgeVisualMath.cloud(s, 0, 0, .5F, 0, 0));
        var rain = AgeVisualMath.cloud(s, 0, 0, 0, 1, 0);
        assertEquals(.221F, rain.red(), .00001F); assertEquals(.171F, rain.green(), .00001F);
    }
    @Test void terrainCloudHeightAndHorizonUseClassicValues() {
        assertEquals(128, compile().environment().cloudHeight());
        assertEquals(200, compile("TerrainNether").environment().cloudHeight());
        assertEquals(128, compile("TerrainNether").environment().horizon());
        assertEquals(42.5F, compile("Skylands").environment().cloudHeight());
        assertEquals(0, compile("TerrainVoid").environment().cloudHeight());
        assertEquals(0, compile("TerrainEnd").environment().horizon());
        assertEquals(121.25F, compile("TerrainNether", "Skylands").environment().cloudHeight());
        assertEquals(64, compile("TerrainNether", "Skylands").environment().horizon());
        assertFalse(compile("NoHorizon").environment().drawVoid());
    }
    @Test void versionedEnvironmentRetainsOldNativeCloudsAndNewFrozenSettings() {
        var s = compile("WeatherSnow", "ModColorBlue", "ColorCloud", "TerrainNether");
        assertEquals(s, AgeVisualSettings.fromTag(s.toTag()));
        CompoundTag old = s.toTag(); old.putInt("Version", 2); old.remove("Environment");
        assertTrue(Float.isNaN(AgeVisualSettings.fromTag(old).environment().cloudHeight()));
        assertEquals("normal", AgeVisualSettings.fromTag(old).environment().weather());
        assertThrows(IllegalArgumentException.class, () -> new AgeVisualSettings.Environment(List.of(), List.of(), List.of(), Float.POSITIVE_INFINITY, 63, "normal"));
    }
    @Test void rainSnowStormCloudyAreDistinctAndSleepDisablePersistsExactly12000Ticks() {
        assertEquals(.2F, AgeWeatherRules.temperature("rain", -.5F));
        assertEquals(.1F, AgeWeatherRules.temperature("snow", 2));
        assertEquals(Biome.Precipitation.RAIN, AgeWeatherRules.precipitation("storm", Biome.Precipitation.NONE, true));
        assertEquals(Biome.Precipitation.SNOW, AgeWeatherRules.precipitation("snow", Biome.Precipitation.NONE, true));
        assertEquals(Biome.Precipitation.NONE, AgeWeatherRules.precipitation("cloudy", Biome.Precipitation.RAIN, true));
        CompoundTag state = new CompoundTag(); state.putString("MystcraftWeather", "storm");
        assertTrue(AgeWeatherRules.raining(state)); assertTrue(AgeWeatherRules.thundering(state));
        AgeWeatherRules.disable(state); state = state.copy();
        for (int i = 0; i < 11999; i++) AgeWeatherRules.tick(state);
        assertFalse(AgeWeatherRules.raining(state)); assertFalse(AgeWeatherRules.thundering(state));
        AgeWeatherRules.tick(state); assertTrue(AgeWeatherRules.raining(state)); assertTrue(AgeWeatherRules.thundering(state));
    }
    @Test void executionSeedsIncludeDynamicTerminalAndControllerDuplicatesAreLastWins() {
        var pages = List.of(page("biome999"), page("SunNormal"));
        Random expected = new Random(123); expected.nextLong();
        assertEquals(expected.nextLong(), AgeVisualCompiler.parseExecution(pages, 123).settings().celestials().bodies().get(0).seed());
        assertEquals("bright", compile("LightingDark", "LightingBright").lighting());
        assertEquals("snow", compile("WeatherRain", "WeatherSnow").environment().weather());
    }
    @Test void executionDanglingAndClearCostsCountModifierValuesNotInputPages() {
        var pages = List.of(page("ModColorRed"), page("ModColorBlue"), page("ModFull"));
        assertEquals(200, AgeVisualCompiler.parseExecution(pages, 1).danglingInstability());
        var clear = List.of(page("ModColorRed"), page("ModColorBlue"), page("ModFull"), page("ModClear"));
        var result = AgeVisualCompiler.parseExecution(clear, 1);
        assertEquals(40, result.danglingInstability()); assertEquals(4, result.consumed().size());
        assertEquals(100, AgeVisualCompiler.parseExecution(List.of(page("ModGradient")), 1).danglingInstability());
    }
    @Test void rainbowConsumesOnlyAngleAndUsesSourceStationaryGeometryAndUnconditionalColors() {
        var result = compile("ModFull", "ModNoon", "ModColorRed", "ModEast", "Rainbow", "SunNormal");
        var rainbow = result.celestials().bodies().get(0);
        assertEquals(AgeCelestialSettings.Kind.RAINBOW, rainbow.kind());
        assertEquals(-90, rainbow.yaw()); assertEquals(0, rainbow.period());
        var sun = result.celestials().bodies().get(1);
        assertEquals(24000, sun.period()); assertEquals(0, sun.offset());
        assertEquals(0xC80000, RainbowMath.color(0));
        assertEquals(100, RainbowMath.height(0)); assertEquals(0, RainbowMath.height(100));
        assertEquals(86.60254F, RainbowMath.height(50), .0001F);
        assertEquals(result, AgeVisualSettings.fromTag(result.toTag()));
        var seeded = AgeVisualCompiler.parseExecution(List.of(page("Rainbow")), 123).settings().celestials().bodies().get(0);
        long symbolSeed = new Random(123).nextLong();
        assertEquals(-(float) (new Random(symbolSeed).nextDouble() * 360), seeded.yaw());
        var noHorizon = compile("NoHorizon", "SunNormal", "StarsNormal");
        assertFalse(noHorizon.celestials().legacyVanilla()); assertFalse(noHorizon.environment().drawVoid());
    }
}
