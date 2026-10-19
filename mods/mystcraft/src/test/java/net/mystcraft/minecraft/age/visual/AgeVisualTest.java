package net.mystcraft.minecraft.age.visual;

import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Provider;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Rgb;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Stop;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeVisualTest {
    private CompoundTag page(String symbol) { CompoundTag tag = new CompoundTag(); PageData.setLegacySymbol(tag, "mystcraft:" + symbol); return tag; }
    private AgeVisualCompiler.Result compile(String... symbols) { return AgeVisualCompiler.parse(java.util.Arrays.stream(symbols).map(this::page).toList()); }
    @Test void consumesOrderedColorAndCelestialParametersAndLeavesDanglingAngleVisible() {
        var result = compile("ModColorRed", "ModColorBlue", "ColorSky", "SunNormal", "ModEast");
        assertEquals(Set.of(0, 1, 2, 3), result.consumed());
        assertFalse(result.settings().celestials().legacyVanilla());
        assertEquals(AgeCelestialSettings.Kind.SUN, result.settings().celestials().bodies().get(0).kind());
        assertFalse(result.consumed().contains(4));
        assertTrue(result.errors().isEmpty());
        assertEquals(new Rgb(.5F, 0, .5F), result.settings().sky().get(0).color(0));
        assertTrue(compile("ModColorRed").consumed().isEmpty());
        assertFalse(compile("ColorWater").errors().isEmpty());
    }
    @Test void interpolatesGradientDurationsWrapsAndDoesNotConsumeSeparatePendingColor() {
        var result = compile("ModColorRed", "ModHalf", "ModGradient", "ModColorBlue", "ModDouble", "ModGradient", "ModColorGreen", "ColorFog");
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 7), result.consumed());
        var gradient = result.settings().fog().get(0);
        assertEquals(new Rgb(.5F, 0, .5F), gradient.color(.25));
        assertEquals(new Rgb(0, 0, 1), gradient.color(.5));
        assertEquals(new Rgb(1, 0, 0), gradient.color(2.5));
        assertEquals(new Rgb(1, 0, 0), gradient.color(-2.5));
        assertEquals(result.settings(), AgeVisualSettings.fromTag(result.settings().toTag()));
    }
    @Test void distinguishesDayNightAndAveragesSkyProvidersInClassicOrder() {
        var day = compile("ModColorRed", "ColorSky").settings();
        var night = compile("ModColorBlue", "ColorSkyNight").settings();
        assertEquals(new Rgb(1, 0, 0), AgeVisualMath.sky(day, 0, 0, 0, .8F));
        assertEquals(new Rgb(0, 0, 0), AgeVisualMath.sky(day, 0, 0, .5F, .8F));
        assertEquals(new Rgb(0, 0, 1), AgeVisualMath.sky(night, 0, 0, .5F, .8F));
        var combined = compile("ModColorRed", "ColorSky", "ModColorBlue", "ColorSky", "ModColorGreen", "ColorSky").settings();
        assertEquals(new Rgb(.25F, .5F, .25F), AgeVisualMath.sky(combined, 0, 0, 0, .8F));
    }
    @Test void matchesClassicBlackFogAndFirstStaticWaterProviderBehavior() {
        var settings = compile("ModColorBlack", "ColorFog", "ModColorRed", "ColorWater", "ModColorBlue", "ColorWater").settings();
        assertEquals(new Rgb(.0001F, .0001F, .0001F), AgeVisualMath.fog(settings, 0, 0, 0));
        assertEquals(0xFF0000, AgeVisualMath.water(settings, 0x123456));
        assertEquals(0x123456, AgeVisualMath.water(AgeVisualSettings.DEFAULT, 0x123456));
        assertEquals(0x7F0000, new Rgb(.5F, 0, 0).packed());
    }
    @Test void matchesClassicBrightnessTablesAndLastLightingControllerWins() {
        assertEquals(0, AgeVisualMath.lightBrightness("normal", 0));
        assertEquals(.25F, AgeVisualMath.lightBrightness("bright", 0));
        assertEquals(1, AgeVisualMath.lightBrightness("normal", 15));
        assertEquals(.5F, AgeVisualMath.lightBrightness("dark", 15));
        assertEquals(1, AgeVisualMath.lightBrightness("bright", 15));
        assertTrue(compile("LightingDark", "LightingBright").errors().isEmpty());
        assertEquals("bright", compile("LightingDark", "LightingBright").settings().lighting());
        for (int i = 0; i < 16; i++) assertEquals(AgeVisualMath.lightBrightness("normal", i) / 2, AgeVisualMath.lightBrightness("dark", i));
    }
    @Test void validatesPayloadsAndPreservesSourceNbt() {
        CompoundTag first = page("ModColorRed"); CompoundTag copy = first.copy();
        AgeVisualCompiler.parse(List.of(first, page("ColorSky")));
        assertEquals(copy, first);
        assertThrows(IllegalArgumentException.class, () -> new Rgb(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stop(new Rgb(0, 0, 0), Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Provider("gradient", List.of()));
    }
}
