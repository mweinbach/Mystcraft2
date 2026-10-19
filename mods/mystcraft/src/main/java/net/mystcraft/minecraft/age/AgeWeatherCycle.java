package net.mystcraft.minecraft.age;

import net.minecraft.nbt.CompoundTag;

/** Original WeatherControllerBase timer semantics, using a persisted per-Age Java-style RNG. */
public final class AgeWeatherCycle {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long MASK = (1L << 48) - 1;
    private AgeWeatherCycle() {}

    public record Profile(int rainDurationBase, int rainDurationRandom,
                          int rainCooldownBase, int rainCooldownRandom,
                          int thunderDurationBase, int thunderDurationRandom,
                          int thunderCooldownBase, int thunderCooldownRandom) {}

    public static Profile profile(String mode) {
        int scale = switch (mode) { case "slow" -> 2; case "fast" -> 0; default -> 1; };
        if (!mode.equals("normal") && !mode.equals("slow") && !mode.equals("fast"))
            throw new IllegalArgumentException("Not a cycling weather mode: " + mode);
        if (scale == 0) return new Profile(6000, 6000, 6000, 84000, 1800, 6000, 6000, 84000);
        return new Profile(12000 * scale, 12000 * scale, 12000 * scale, 168000 * scale,
                3600 * scale, 12000 * scale, 12000 * scale, 168000 * scale);
    }

    /** A counter reaching zero toggles its flag; the following tick chooses the next interval. */
    public static void tick(CompoundTag state, long ageSeed) {
        String mode = state.getString("MystcraftWeather");
        if (mode.isEmpty()) mode = "normal";
        Profile p = profile(mode);
        if (!state.contains("MystcraftWeatherRandom")) state.putLong("MystcraftWeatherRandom", (ageSeed ^ MULTIPLIER) & MASK);
        advance(state, "ThunderTime", "Thundering", p.thunderDurationBase(), p.thunderDurationRandom(),
                p.thunderCooldownBase(), p.thunderCooldownRandom());
        advance(state, "RainTime", "Raining", p.rainDurationBase(), p.rainDurationRandom(),
                p.rainCooldownBase(), p.rainCooldownRandom());
    }

    private static void advance(CompoundTag state, String counter, String flag, int activeBase, int activeRandom,
                                int inactiveBase, int inactiveRandom) {
        int remaining = state.getInt(counter);
        if (remaining <= 0) {
            boolean active = state.getBoolean(flag);
            state.putInt(counter, (active ? activeBase : inactiveBase)
                    + nextInt(state, active ? activeRandom : inactiveRandom));
        } else {
            state.putInt(counter, remaining - 1);
            if (remaining == 1) state.putBoolean(flag, !state.getBoolean(flag));
        }
    }

    private static int next(CompoundTag state) {
        long value = (state.getLong("MystcraftWeatherRandom") * MULTIPLIER + 11) & MASK;
        state.putLong("MystcraftWeatherRandom", value);
        return (int) (value >>> 17);
    }
    private static int nextInt(CompoundTag state, int bound) {
        if ((bound & -bound) == bound) return (int) ((bound * (long) next(state)) >> 31);
        int bits, value;
        do { bits = next(state); value = bits % bound; } while (bits - value + (bound - 1) < 0);
        return value;
    }
}
