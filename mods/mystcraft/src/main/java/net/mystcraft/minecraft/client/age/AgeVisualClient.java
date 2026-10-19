package net.mystcraft.minecraft.client.age;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.minecraft.age.visual.AgeVisualMath;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;

/** Dimension-keyed client settings; packets may arrive before the matching respawn creates its level. */
public final class AgeVisualClient {
    public static final ResourceLocation EFFECTS = new ResourceLocation("mystcraft", "age");
    private static final Map<ResourceLocation, AgeVisualSettings> SETTINGS = new ConcurrentHashMap<>();
    private static final java.util.ArrayDeque<ResourceLocation> ORDER = new java.util.ArrayDeque<>();
    private static final int MAX_CACHED_AGES = 4096;
    private AgeVisualClient() {}

    /** Loader transport calls this on the client game thread. */
    public static void receive(ResourceLocation dimension, AgeVisualSettings settings) {
        ORDER.remove(dimension);
        ORDER.addLast(dimension);
        SETTINGS.put(dimension, settings);
        while (ORDER.size() > MAX_CACHED_AGES) SETTINGS.remove(ORDER.removeFirst());
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.dimension().location().equals(dimension)) {
            client.level.clearTintCaches();
            client.levelRenderer.allChanged();
        }
    }
    public static void clear() { SETTINGS.clear(); ORDER.clear(); AgeSkyRenderer.clear(); }
    public static AgeVisualSettings settings(ClientLevel level) {
        if (level == null || !EFFECTS.equals(level.dimensionType().effectsLocation())) return AgeVisualSettings.DEFAULT;
        return SETTINGS.getOrDefault(level.dimension().location(), AgeVisualSettings.DEFAULT);
    }
    public static Vec3 sky(ClientLevel level, Vec3 camera, float partial, Vec3 vanilla) {
        AgeVisualSettings settings = settings(level);
        if (settings.sky().isEmpty()) return vanilla;
        BlockPos pos = new BlockPos(camera);
        float temperature = ((AgeBiomeTemperature) (Object) level.getBiome(pos).value()).mystcraft$temperature(pos);
        var color = AgeVisualMath.sky(settings, level.getDayTime(), partial, level.getTimeOfDay(partial), temperature);
        color = AgeVisualMath.weather(color, level.getRainLevel(partial), level.getThunderLevel(partial), level.getSkyFlashTime() - partial);
        return new Vec3(color.red(), color.green(), color.blue());
    }
    public static Vec3 fog(Vec3 vanilla, float brightness) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        AgeVisualSettings settings = settings(level);
        if (settings.fog().isEmpty()) return vanilla;
        float partial = client.getFrameTime();
        var color = AgeVisualMath.fog(settings, level.getDayTime(), partial, level.getTimeOfDay(partial));
        return new Vec3(color.red(), color.green(), color.blue());
    }
    public static int water(int vanilla) {
        return AgeVisualMath.water(settings(Minecraft.getInstance().level), vanilla);
    }
    public static int grass(BlockPos pos, int vanilla) { return vegetation(pos, vanilla, false); }
    public static int foliage(BlockPos pos, int vanilla) { return vegetation(pos, vanilla, true); }
    private static int vegetation(BlockPos pos, int vanilla, boolean foliage) {
        ClientLevel level = Minecraft.getInstance().level;
        var providers = foliage ? settings(level).environment().foliage() : settings(level).environment().grass();
        if (providers.isEmpty() || level == null) return vanilla;
        if (!providers.get(0).mode().equals("natural")) return AgeVisualMath.staticColor(providers, vanilla);
        var biome = level.getBiome(pos).value();
        float temp = net.mystcraft.minecraft.age.AgeWeatherRules.temperature(settings(level).environment().weather(), ((AgeBiomeTemperature) (Object) biome).mystcraft$temperature(pos));
        double temperature = Math.max(0, Math.min(1, temp)), humidity = Math.max(0, Math.min(1, biome.getDownfall()));
        return foliage ? net.minecraft.world.level.FoliageColor.get(temperature, humidity) : net.minecraft.world.level.GrassColor.get(temperature, humidity);
    }
    public static Vec3 cloud(ClientLevel level, float partial, Vec3 vanilla) {
        var settings = settings(level);
        if (settings.environment().cloud().isEmpty()) return vanilla;
        var color = AgeVisualMath.cloud(settings, level.getDayTime(), partial, level.getTimeOfDay(partial), level.getRainLevel(partial), level.getThunderLevel(partial));
        return new Vec3(color.red(), color.green(), color.blue());
    }
    public static float cloudHeight(float vanilla) {
        float height = settings(Minecraft.getInstance().level).environment().cloudHeight();
        return Float.isNaN(height) ? vanilla : height;
    }
    public static float brightness(DimensionType type, int light, float vanilla) {
        if (!EFFECTS.equals(type.effectsLocation())) return vanilla;
        return AgeVisualMath.lightBrightness(settings(Minecraft.getInstance().level).lighting(), light);
    }
}
