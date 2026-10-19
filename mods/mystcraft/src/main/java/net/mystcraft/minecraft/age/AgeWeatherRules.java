package net.mystcraft.minecraft.age;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.biome.Biome;

/** Source WeatherControllerToggleable: persistent disable cooldown, independent rain/snow enablement and temperature. */
public final class AgeWeatherRules {
    private AgeWeatherRules() {}
    public static boolean validMode(String mode) { return List.of("normal", "off", "always", "slow", "fast", "rain", "snow", "storm", "cloudy").contains(mode); }
    public static boolean toggleable(String mode) { return List.of("rain", "snow", "storm", "cloudy").contains(mode); }
    public static boolean active(CompoundTag state) { return !state.getBoolean("MystcraftWeatherDisabled"); }
    public static void disable(CompoundTag state) { state.putBoolean("MystcraftWeatherDisabled", true); state.putInt("MystcraftWeatherReset", 12000); }
    public static void enable(CompoundTag state) { state.putBoolean("MystcraftWeatherDisabled", false); state.putInt("MystcraftWeatherReset", 0); }
    public static void tick(CompoundTag state) {
        int counter = state.getInt("MystcraftWeatherReset");
        if (counter > 0) { state.putInt("MystcraftWeatherReset", counter - 1); if (counter == 1) enable(state); }
    }
    public static boolean raining(CompoundTag state) { return active(state); }
    public static boolean thundering(CompoundTag state) { return state.getString("MystcraftWeather").equals("storm") && active(state); }
    public static float temperature(String mode, float natural) {
        return switch (mode) { case "rain", "storm" -> Math.max(.20F, natural); case "snow" -> Math.min(.10F, natural); default -> natural; };
    }
    public static Biome.Precipitation precipitation(String mode, Biome.Precipitation natural, boolean active) {
        if (!active) return natural;
        return switch (mode) { case "rain", "storm" -> Biome.Precipitation.RAIN; case "snow" -> Biome.Precipitation.SNOW;
            case "cloudy" -> Biome.Precipitation.NONE; default -> natural; };
    }
}
