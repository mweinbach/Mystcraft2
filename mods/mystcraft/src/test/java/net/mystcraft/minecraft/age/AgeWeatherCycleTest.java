package net.mystcraft.minecraft.age;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeWeatherCycleTest {
    private CompoundTag state(String mode) { CompoundTag s = new CompoundTag(); s.putString("MystcraftWeather", mode); return s; }
    @Test void usesOriginalNormalSlowAndFastCooldownRangesWithoutImmediatelyStartingRain() {
        for (String mode : new String[]{"normal", "slow", "fast"}) {
            CompoundTag s = state(mode); AgeWeatherCycle.tick(s, 41829);
            var p = AgeWeatherCycle.profile(mode);
            assertTrue(s.getInt("RainTime") >= p.rainCooldownBase());
            assertTrue(s.getInt("RainTime") < p.rainCooldownBase() + p.rainCooldownRandom());
            assertTrue(s.getInt("ThunderTime") >= p.thunderCooldownBase());
            assertTrue(s.getInt("ThunderTime") < p.thunderCooldownBase() + p.thunderCooldownRandom());
            assertFalse(s.getBoolean("Raining")); assertFalse(s.getBoolean("Thundering"));
        }
        assertEquals(6000, AgeWeatherCycle.profile("fast").rainDurationBase());
        assertEquals(24000, AgeWeatherCycle.profile("slow").rainDurationBase());
        assertEquals(3600, AgeWeatherCycle.profile("normal").thunderDurationBase());
    }
    @Test void togglesExactlyAtCounterExpiryThenChoosesActiveDurationOnFollowingTick() {
        CompoundTag s = state("fast"); s.putInt("RainTime", 2); s.putInt("ThunderTime", 1);
        AgeWeatherCycle.tick(s, 99);
        assertFalse(s.getBoolean("Raining")); assertTrue(s.getBoolean("Thundering"));
        assertEquals(1, s.getInt("RainTime")); assertEquals(0, s.getInt("ThunderTime"));
        AgeWeatherCycle.tick(s, 99);
        assertTrue(s.getBoolean("Raining")); assertEquals(0, s.getInt("RainTime"));
        assertTrue(s.getInt("ThunderTime") >= 1800 && s.getInt("ThunderTime") < 7800);
        AgeWeatherCycle.tick(s, 99);
        assertTrue(s.getInt("RainTime") >= 6000 && s.getInt("RainTime") < 12000);
    }
    @Test void savedCountersFlagsAndRandomStateResumeWithoutRerollingOrSharingBetweenAges() {
        CompoundTag first = state("slow"); AgeWeatherCycle.tick(first, 1234);
        CompoundTag restored = first.copy();
        for (int i = 0; i < 8; i++) {
            first.putInt("RainTime", 1); first.putInt("ThunderTime", 1);
            restored.putInt("RainTime", 1); restored.putInt("ThunderTime", 1);
            AgeWeatherCycle.tick(first, 1234); AgeWeatherCycle.tick(restored, 9876);
            AgeWeatherCycle.tick(first, 1234); AgeWeatherCycle.tick(restored, 9876);
            assertEquals(first, restored, "persisted RNG must take precedence over constructor seed on restart");
        }
        CompoundTag other = state("slow"); AgeWeatherCycle.tick(other, 1235);
        assertNotEquals(first.getLong("MystcraftWeatherRandom"), other.getLong("MystcraftWeatherRandom"));
        assertThrows(IllegalArgumentException.class, () -> AgeWeatherCycle.tick(state("storm"), 1));
    }
}
