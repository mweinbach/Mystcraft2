package net.mystcraft.minecraft.age.visual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Immutable, bounded presentation settings; no client rendering classes or shared biome mutation. */
public record AgeVisualSettings(String lighting, List<Provider> sky, List<Provider> fog, List<Provider> water, AgeCelestialSettings celestials, Environment environment) {
    public static final String STATE_KEY = "MystcraftVisual";
    public static final int MAX_PROVIDERS = 256;
    public static final AgeVisualSettings DEFAULT = new AgeVisualSettings("normal", List.of(), List.of(), List.of());

    public AgeVisualSettings(String lighting, List<Provider> sky, List<Provider> fog, List<Provider> water) {
        this(lighting, sky, fog, water, AgeCelestialSettings.LEGACY);
    }
    public AgeVisualSettings(String lighting, List<Provider> sky, List<Provider> fog, List<Provider> water, AgeCelestialSettings celestials) {
        this(lighting, sky, fog, water, celestials, Environment.LEGACY);
    }
    public AgeVisualSettings {
        if (!List.of("normal", "dark", "bright").contains(lighting)) throw new IllegalArgumentException("Unknown lighting");
        if (celestials == null || environment == null) throw new IllegalArgumentException("Missing celestial plan");
        sky = List.copyOf(sky); fog = List.copyOf(fog); water = List.copyOf(water);
        if (sky.size() + fog.size() + water.size() + environment.cloud().size() + environment.grass().size() + environment.foliage().size() > MAX_PROVIDERS) throw new IllegalArgumentException("Too many visual providers");
    }
    /** NaN height means native modern cloud height for pre-v3 saved profiles. */
    public record Environment(List<Provider> cloud, List<Provider> grass, List<Provider> foliage,
                              float cloudHeight, float horizon, String weather, boolean drawVoid) {
        public Environment(List<Provider> cloud, List<Provider> grass, List<Provider> foliage, float cloudHeight, float horizon, String weather) {
            this(cloud, grass, foliage, cloudHeight, horizon, weather, true);
        }
        public static final Environment LEGACY = new Environment(List.of(), List.of(), List.of(), Float.NaN, 63, "normal");
        public Environment {
            cloud = List.copyOf(cloud); grass = List.copyOf(grass); foliage = List.copyOf(foliage);
            if ((!Float.isNaN(cloudHeight) && (!Float.isFinite(cloudHeight) || cloudHeight < -2048 || cloudHeight > 2048))
                    || !Float.isFinite(horizon) || horizon < -2048 || horizon > 2048)
                throw new IllegalArgumentException("Invalid cloud/horizon height");
            if (!net.mystcraft.minecraft.age.AgeWeatherRules.validMode(weather)) throw new IllegalArgumentException("Unknown weather");
        }
    }
    public record Rgb(float red, float green, float blue) {
        public Rgb {
            if (!valid(red) || !valid(green) || !valid(blue)) throw new IllegalArgumentException("Invalid RGB");
        }
        private static boolean valid(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }
        public Rgb average(Rgb right) { return new Rgb((red + right.red) / 2, (green + right.green) / 2, (blue + right.blue) / 2); }
        public Rgb multiply(float factor) { return new Rgb(red * factor, green * factor, blue * factor); }
        public int packed() { return ((int) (red * 255) << 16) | ((int) (green * 255) << 8) | (int) (blue * 255); }
    }
    public record Stop(Rgb color, float duration) {
        public Stop {
            if (!Float.isFinite(duration) || duration <= 0 || duration > 1_000_000) throw new IllegalArgumentException("Invalid gradient interval");
        }
    }
    /** Modes are natural, day, night, gradient, or constant, depending on the channel. */
    public record Provider(String mode, List<Stop> stops) {
        public Provider {
            if (!List.of("natural", "day", "night", "gradient", "constant").contains(mode)) throw new IllegalArgumentException("Unknown color mode");
            stops = List.copyOf(stops);
            if (stops.size() > MAX_PROVIDERS || !mode.equals("natural") && stops.isEmpty()) throw new IllegalArgumentException("Invalid color gradient");
        }
        public Rgb color(double time) {
            if (stops.isEmpty()) throw new IllegalStateException("Natural color needs biome context");
            if (stops.size() == 1) return stops.get(0).color();
            double length = stops.stream().mapToDouble(Stop::duration).sum();
            double phase = ((time % length) + length) % length;
            for (int i = 0; i < stops.size(); i++) {
                Stop current = stops.get(i);
                if (phase < current.duration()) {
                    float amount = (float) (phase / current.duration());
                    Rgb a = current.color(), b = stops.get((i + 1) % stops.size()).color();
                    return new Rgb(a.red * (1 - amount) + b.red * amount, a.green * (1 - amount) + b.green * amount, a.blue * (1 - amount) + b.blue * amount);
                }
                phase -= current.duration();
            }
            return stops.get(0).color();
        }
    }
    public CompoundTag toTag() {
        CompoundTag result = new CompoundTag();
        result.putInt("Version", 3); result.putString("Lighting", lighting);
        result.put("Sky", writeProviders(sky)); result.put("Fog", writeProviders(fog)); result.put("Water", writeProviders(water));
        result.put("Celestials", celestials.toTag());
        CompoundTag env = new CompoundTag();
        env.put("Cloud", writeProviders(environment.cloud())); env.put("Grass", writeProviders(environment.grass())); env.put("Foliage", writeProviders(environment.foliage()));
        if (!Float.isNaN(environment.cloudHeight())) env.putFloat("CloudHeight", environment.cloudHeight());
        env.putBoolean("DrawVoid", environment.drawVoid()); env.putFloat("Horizon", environment.horizon()); env.putString("Weather", environment.weather()); result.put("Environment", env);
        return result;
    }
    public static AgeVisualSettings fromTag(CompoundTag tag) {
        int version = tag.getInt("Version");
        if (version != 1 && version != 2 && version != 3) throw new IllegalArgumentException("Unknown Age visual version");
        return new AgeVisualSettings(tag.getString("Lighting"), readProviders(tag, "Sky"), readProviders(tag, "Fog"), readProviders(tag, "Water"),
                version == 1 ? AgeCelestialSettings.LEGACY : AgeCelestialSettings.fromTag(tag.getCompound("Celestials")), version < 3 ? Environment.LEGACY : readEnvironment(tag.getCompound("Environment")));
    }
    private static Environment readEnvironment(CompoundTag tag) {
        return new Environment(readProviders(tag, "Cloud"), readProviders(tag, "Grass"), readProviders(tag, "Foliage"),
                tag.contains("CloudHeight", Tag.TAG_FLOAT) ? tag.getFloat("CloudHeight") : Float.NaN,
                tag.getFloat("Horizon"), tag.getString("Weather"), tag.getBoolean("DrawVoid"));
    }
    public static ListTag writeGradient(List<Stop> stops) {
        if (stops.size() > MAX_PROVIDERS) throw new IllegalArgumentException("Too many gradient stops");
        ListTag result = new ListTag();
        for (Stop stop : stops) {
            CompoundTag point = new CompoundTag(); point.putFloat("R", stop.color().red()); point.putFloat("G", stop.color().green());
            point.putFloat("B", stop.color().blue()); point.putFloat("Duration", stop.duration()); result.add(point);
        }
        return result;
    }
    public static List<Stop> readGradient(ListTag list) {
        if (list.size() > MAX_PROVIDERS || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid gradient payload");
        List<Stop> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag point = list.getCompound(i);
            result.add(new Stop(new Rgb(point.getFloat("R"), point.getFloat("G"), point.getFloat("B")), point.getFloat("Duration")));
        }
        return List.copyOf(result);
    }
    private static ListTag writeProviders(List<Provider> values) {
        ListTag list = new ListTag();
        for (Provider provider : values) {
            CompoundTag entry = new CompoundTag(); entry.putString("Mode", provider.mode());
            ListTag stops = new ListTag();
            for (Stop stop : provider.stops()) {
                CompoundTag point = new CompoundTag(); point.putFloat("R", stop.color.red); point.putFloat("G", stop.color.green); point.putFloat("B", stop.color.blue); point.putFloat("Duration", stop.duration);
                stops.add(point);
            }
            entry.put("Stops", stops); list.add(entry);
        }
        return list;
    }
    private static List<Provider> readProviders(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        if (list.size() > MAX_PROVIDERS) throw new IllegalArgumentException("Too many color providers");
        List<Provider> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i); ListTag points = entry.getList("Stops", Tag.TAG_COMPOUND);
            if (points.size() > MAX_PROVIDERS) throw new IllegalArgumentException("Too many gradient stops");
            List<Stop> stops = new ArrayList<>();
            for (int j = 0; j < points.size(); j++) {
                CompoundTag point = points.getCompound(j);
                stops.add(new Stop(new Rgb(point.getFloat("R"), point.getFloat("G"), point.getFloat("B")), point.getFloat("Duration")));
            }
            result.add(new Provider(entry.getString("Mode"), stops));
        }
        return result;
    }
}
