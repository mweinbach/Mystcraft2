package net.mystcraft.minecraft.age;

import java.util.function.Supplier;
import net.minecraft.world.level.LevelReader;

/** A call-scoped temperature override: never changes the biome registry or another world's climate. */
public final class AgeWeatherContext {
    private static final ThreadLocal<String> MODE = new ThreadLocal<>();
    private AgeWeatherContext() {}
    public static float temperature(float natural) {
        String mode = MODE.get();
        return mode == null ? natural : AgeWeatherRules.temperature(mode, natural);
    }
    public static <T> T with(LevelReader level, Supplier<T> action) {
        if (!(level instanceof AgeServerLevel age)) return action.get();
        String previous = MODE.get(); MODE.set(age.weatherMode());
        try { return action.get(); }
        finally { if (previous == null) MODE.remove(); else MODE.set(previous); }
    }
}
