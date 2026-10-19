package net.mystcraft.minecraft.age.visual;

import java.util.List;
import java.util.Random;

/** Realizes classic per-body random defaults. The ordered modifier machine lives in AgeVisualCompiler. */
public final class AgeCelestialCompiler {
    private AgeCelestialCompiler() {}
    /** Java Random's exact 48-bit stream, with an explicit continuation seed for render initialization. */
    private static final class ClassicRandom extends Random {
        private long state;
        private static final long MULTIPLIER = 0x5deece66dL, MASK = (1L << 48) - 1;
        ClassicRandom(long seed) { super(0); state = (seed ^ MULTIPLIER) & MASK; }
        @Override protected int next(int bits) { state = (state * MULTIPLIER + 11) & MASK; return (int)(state >>> (48 - bits)); }
        long snapshotSeed() { return state ^ MULTIPLIER; }
    }
    public static AgeCelestialSettings.Body realize(AgeCelestialSettings.Kind kind, long seed, Float factor, Float yaw, Float phase,
            List<AgeVisualSettings.Stop> gradient, List<AgeVisualSettings.Stop> sunset) {
        if (kind == AgeCelestialSettings.Kind.END_SKY) return new AgeCelestialSettings.Body(kind, seed, 0, 0, 0, gradient, sunset);
        ClassicRandom random = new ClassicRandom(seed);
        if (kind == AgeCelestialSettings.Kind.RAINBOW) {
            float angle = yaw == null ? (float) (random.nextDouble() * 360) : yaw;
            return new AgeCelestialSettings.Body(kind, seed, 0, -angle, 0, List.of(), List.of(), random.snapshotSeed());
        }
        boolean sun = kind == AgeCelestialSettings.Kind.SUN;
        boolean orbit = sun || kind == AgeCelestialSettings.Kind.MOON;
        double multiplier = factor == null ? sun ? .4 * random.nextDouble() + .8 : 1.8 * random.nextDouble() + .2 : factor;
        long period = (long)(multiplier * (orbit ? 24000L : 240000L));
        float angle = yaw == null ? (float)(random.nextDouble() * 360) : yaw;
        float offset = 0;
        if (orbit) {
            if (phase == null) {
                offset = random.nextFloat();
                if (period == 0) offset = offset / 2 + .25F;
            } else offset = phase / 360;
            offset -= .5F;
        }
        return new AgeCelestialSettings.Body(kind, seed, period, -angle, offset, gradient, sunset, random.snapshotSeed());
    }
}
