package net.mystcraft.minecraft.age.visual;

/** Shared equations from classic celestial objects and AgeController; no rendering dependencies. */
public final class CelestialMath {
    private CelestialMath() {}
    public static float averageAngles(float first, float second) {
        float third = second;
        if (Math.abs(first - second) > 180) third += 360;
        if (Math.abs(first - third) == 180) third = first + 180;
        float average = (first + third) / 2;
        if (average >= 360) average -= 360;
        return average;
    }
    public static float altitude(AgeCelestialSettings.Body body, long time, float partial) {
        if (body.period() == 0) return body.offset();
        float f = ((int)(time % body.period()) + partial) / body.period() + body.offset();
        if (f < 0) ++f;
        if (f > 1) --f;
        float original = f;
        f = 1 - (float)((Math.cos(f * Math.PI) + 1) / 2);
        return original + (f - original) / 3;
    }
    public static int moonPhase(AgeCelestialSettings.Body body, long time) {
        return body.period() == 0 ? 0 : (int)(time / body.period()) % 8;
    }
    public static float daylightAngle(AgeCelestialSettings settings, long time, float partial) {
        float lowest = .5F, highest = .5F;
        for (var body : settings.bodies()) if (body.kind() == AgeCelestialSettings.Kind.SUN) {
            float angle = altitude(body, time, partial);
            while (angle < 0) angle++;
            while (angle > 1) angle--;
            highest = Math.max(highest, angle); lowest = Math.min(lowest, angle);
        }
        return 1 - highest < lowest ? highest : lowest;
    }
    public static long timeToDawn(AgeCelestialSettings settings, long time) {
        Long result = null;
        for (var body : settings.bodies()) if (body.kind() == AgeCelestialSettings.Kind.SUN && body.period() != 0) {
            long current = time % body.period();
            long next = (long)(body.period() * Math.abs(.75F - body.offset()));
            if (current > next) next += body.period();
            long delay = next - current;
            if (result == null || delay < result) result = delay;
        }
        return result == null ? 24000 - time % 24000 : result;
    }
    public static float twinkle(long time, float partial, long offset) {
        float phase = ((time + offset) % 100 + partial) / 100;
        return Math.max(0, Math.min(1, 1 - ((float)Math.cos(phase * Math.PI * 2) * 2 + .25F)));
    }
    public static float sunsetAlpha(float angle) {
        float cosine = (float)Math.cos(angle * Math.PI * 2);
        if (cosine < -.4F || cosine > .4F) return 0;
        float phase = cosine / .4F * .5F + .5F;
        float alpha = 1 - (1 - (float)Math.sin(phase * Math.PI)) * .99F;
        return alpha * alpha;
    }
}
