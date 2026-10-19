package net.mystcraft.minecraft.age.visual;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.writing.PageData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeCelestialTest {
    private AgeVisualCompiler.Result compile(long seed, String... symbols) {
        return AgeVisualCompiler.parse(Arrays.stream(symbols).map(symbol -> {
            CompoundTag page = new CompoundTag(); PageData.setLegacySymbol(page, "mystcraft:" + symbol); return page;
        }).toList(), seed);
    }
    private AgeCelestialSettings.Body sun(long period, float offset) {
        return new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.SUN, 1, period, 0, offset, List.of(), List.of());
    }
    @Test void keepsLegacySkyWithoutExplicitBodiesAndDarkTerminalsNeverEraseRegisteredBodies() {
        assertTrue(compile(1, "LightingBright").settings().celestials().legacyVanilla());
        var dark = compile(1, "SunDark", "MoonDark", "StarsDark").settings().celestials();
        assertFalse(dark.legacyVanilla()); assertTrue(dark.bodies().isEmpty());
        assertEquals(.5F, CelestialMath.daylightAngle(dark, 6000, 0));
        assertEquals(1, compile(1, "SunNormal", "SunDark").settings().celestials().bodies().size());
        assertEquals(2, compile(1, "SunNormal", "SunNormal").settings().celestials().bodies().size());
    }
    @Test void factorsAnglesAndPhasesUseOrderedExplicitSymbolSeeds() {
        var result = compile(9876, "ModFull", "ModEast", "ModNoon", "SunNormal");
        assertEquals(4, result.consumed().size()); assertTrue(result.errors().isEmpty());
        var body = result.settings().celestials().bodies().get(0);
        Random stream = new Random(9876); stream.nextLong(); stream.nextLong(); stream.nextLong();
        assertEquals(stream.nextLong(), body.seed());
        assertEquals(24000, body.period()); assertEquals(-90F, body.yaw()); assertEquals(0F, body.offset());
        assertEquals(0F, CelestialMath.altitude(body, 0, 0));
        assertEquals(0F, CelestialMath.averageAngles(270, 90));
        assertEquals(315F, CelestialMath.averageAngles(270, 0));
        assertEquals(90F, CelestialMath.averageAngles(0, 180));
    }
    @Test void bodyRandomDefaultsAndContinuationMatchJavaRandomDrawOrder() {
        var body = AgeCelestialCompiler.realize(AgeCelestialSettings.Kind.SUN, 123456, null, null, null, List.of(), List.of());
        Random reference = new Random(123456);
        assertEquals((long)((.4 * reference.nextDouble() + .8) * 24000), body.period());
        assertEquals(-(float)(reference.nextDouble() * 360), body.yaw());
        assertEquals(reference.nextFloat() - .5F, body.offset());
        assertEquals(reference.nextLong(), new Random(body.renderSeed()).nextLong());
        var fixed = AgeCelestialCompiler.realize(AgeCelestialSettings.Kind.MOON, 456, 0F, 0F, null, List.of(), List.of());
        assertEquals(new Random(456).nextFloat() / 2 + .25F - .5F, fixed.offset());
        assertEquals(fixed.offset(), CelestialMath.altitude(fixed, Long.MIN_VALUE, .5F));
    }
    @Test void starsUseTenTimesTheOrbitPeriodAndDoNotStealMoonPhase() {
        var plan = compile(42, "ModSetting", "ModHalf", "StarsNormal", "SunNormal").settings().celestials();
        assertEquals(120000, plan.bodies().get(0).period());
        assertEquals(0F, plan.bodies().get(0).offset());
        assertEquals(.25F, plan.bodies().get(1).offset());
        assertEquals(0, compile(42, "ModZero", "ModNoon", "SunNormal").settings().celestials().bodies().get(0).period());
        assertEquals(48000, compile(42, "ModDouble", "SunNormal").settings().celestials().bodies().get(0).period());
    }
    @Test void gradientIntervalsAndBodyPeriodsShareTheirOriginalModifierChannel() {
        var body = compile(56, "ModColorRed", "ModHalf", "ModGradient", "StarsNormal").settings().celestials().bodies().get(0);
        assertEquals(.5F, body.gradient().get(0).duration());
        long expected = (long)((1.8 * new Random(body.seed()).nextDouble() + .2) * 240000);
        assertEquals(expected, body.period());
        var plan = compile(56, "ModColorRed", "ColorHorizon", "SunNormal", "ModColorBlue", "ColorHorizon", "StarsNormal").settings().celestials();
        assertEquals(new AgeVisualSettings.Rgb(1, 0, 0), plan.bodies().get(0).sunset().get(0).color());
        assertEquals(new AgeVisualSettings.Rgb(0, 0, 1), plan.sunset().get(0).color());
        assertEquals(new AgeVisualSettings.Rgb(1, 1, 1), plan.bodies().get(1).gradient().get(0).color());
    }
    @Test void sourceOrbitVectorsHandleNegativeTimeZeroPeriodAndMoonBoundaries() {
        var body = sun(24000, -.5F);
        assertEquals(.5F, CelestialMath.altitude(body, 0, 0), .000001);
        assertEquals(.7845178F, CelestialMath.altitude(body, 6000, 0), .000001);
        assertEquals(0F, CelestialMath.altitude(body, 12000, 0), .000001);
        assertEquals(.2154822F, CelestialMath.altitude(body, 18000, 0), .000001);
        assertEquals(.2154822F, CelestialMath.altitude(body, -6000, 0), .000001);
        assertEquals(-.25F, CelestialMath.altitude(sun(0, -.25F), 12345, .5F));
        assertEquals(-1, CelestialMath.moonPhase(body, -24000));
        assertEquals(0, CelestialMath.moonPhase(body, 23999));
        assertEquals(1, CelestialMath.moonPhase(body, 24000));
        assertEquals(0, CelestialMath.moonPhase(body, 192000));
    }
    @Test void multipleSunsChooseNearestDayAndSleepChoosesEarliestMovingDawn() {
        var plan = new AgeCelestialSettings(false, List.of(sun(0, .1F), sun(0, -.05F)), List.of());
        assertEquals(.95F, CelestialMath.daylightAngle(plan, 0, 0), .000001);
        assertEquals(24000, CelestialMath.timeToDawn(plan, 24000));
        plan = new AgeCelestialSettings(false, List.of(sun(24000, 0), sun(12000, 0)), List.of());
        assertEquals(9000, CelestialMath.timeToDawn(plan, 0));
        assertEquals(0, CelestialMath.timeToDawn(plan, 18000));
        assertEquals(2999, CelestialMath.timeToDawn(plan, 18001));
    }
    @Test void sunsetAndTwinkleUseOriginalCosineWindows() {
        assertEquals(0F, CelestialMath.sunsetAlpha(0)); assertEquals(1F, CelestialMath.sunsetAlpha(.25F), .000001);
        assertEquals(0F, CelestialMath.twinkle(0, 0, 0)); assertEquals(1F, CelestialMath.twinkle(50, 0, 0));
    }
    @Test void frozenPlansRoundtripAndOldVisualTagsNeverReroll() {
        var settings = compile(5678, "ModZero", "ModNoon", "SunNormal", "StarsTwinkle", "StarsEndSky").settings();
        assertEquals(settings, AgeVisualSettings.fromTag(settings.toTag()));
        assertEquals(settings.celestials(), AgeCelestialSettings.fromTag(settings.celestials().toTag()));
        CompoundTag old = AgeVisualSettings.DEFAULT.toTag(); old.putInt("Version", 1); old.remove("Celestials");
        assertTrue(AgeVisualSettings.fromTag(old).celestials().legacyVanilla());
        assertThrows(IllegalArgumentException.class, () -> sun(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> sun(1, Float.NaN));
    }
}
