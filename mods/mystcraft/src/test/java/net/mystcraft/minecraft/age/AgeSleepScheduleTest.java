package net.mystcraft.minecraft.age;

import java.util.List;
import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeSleepScheduleTest {
    private AgeCelestialSettings.Body sun(long period, float offset) {
        return new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.SUN, 1, period, 0, offset, List.of(), List.of());
    }
    private AgeCelestialSettings settings(AgeCelestialSettings.Body... bodies) {
        return new AgeCelestialSettings(false, List.of(bodies), List.of());
    }
    @Test void sleepingAdvancesToFirstActualSunDawnInsteadOfAlwaysVanillaMidnightBoundary() {
        assertEquals(36000, AgeSleepSchedule.target(settings(sun(48000, 0)), 18000));
        assertEquals(18000, AgeSleepSchedule.target(settings(sun(48000, 0), sun(24000, 0)), 17000));
        assertEquals(24000, AgeSleepSchedule.target(AgeCelestialSettings.LEGACY, 18000));
    }
    @Test void staticOrMissingSunsUseOriginalFallbackDayIntervalAndNeverInventADynamicSun() {
        assertEquals(24000, AgeSleepSchedule.target(settings(sun(0, .25F)), 18000));
        assertEquals(48000, AgeSleepSchedule.target(settings(), 24000));
    }
    @Test void nativeSleepRequestIsDistinctFromOrdinaryTimeTickAndUnrelatedCommands() {
        assertTrue(AgeSleepSchedule.isVanillaSleepAdvance(18000, 24000));
        assertTrue(AgeSleepSchedule.isVanillaSleepAdvance(30000, 48000));
        assertFalse(AgeSleepSchedule.isVanillaSleepAdvance(18000, 18001));
        assertFalse(AgeSleepSchedule.isVanillaSleepAdvance(18000, 6000));
    }
}
