package net.mystcraft.minecraft.age;

import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import net.mystcraft.minecraft.age.visual.CelestialMath;

/** Only vanilla's actual sleep skip is replaced; ordinary ticks and explicit clock changes are separate. */
public final class AgeSleepSchedule {
    private AgeSleepSchedule() {}
    public static boolean isVanillaSleepAdvance(long current, long requested) {
        long dawn = current + 24000;
        dawn -= dawn % 24000;
        return requested == dawn;
    }
    public static long target(AgeCelestialSettings settings, long current) {
        if (settings.legacyVanilla()) {
            long dawn = current + 24000;
            return dawn - dawn % 24000;
        }
        return Math.addExact(current, CelestialMath.timeToDawn(settings, current));
    }
}
