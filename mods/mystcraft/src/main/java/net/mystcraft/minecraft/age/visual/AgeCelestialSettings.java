package net.mystcraft.minecraft.age.visual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Realized explicit-page celestial plan. Version-zero/legacy Ages keep their prior vanilla sky. */
public record AgeCelestialSettings(boolean legacyVanilla, List<Body> bodies, List<AgeVisualSettings.Stop> sunset) {
    public static final AgeCelestialSettings LEGACY = new AgeCelestialSettings(true, List.of(), List.of());
    public static final int MAX_BODIES = 256;
    public enum Kind { SUN, MOON, STARS, TWINKLE, END_SKY, RAINBOW }
    public record Body(Kind kind, long seed, long period, float yaw, float offset,
            List<AgeVisualSettings.Stop> gradient, List<AgeVisualSettings.Stop> sunset, long renderSeed) {
        public Body(Kind kind, long seed, long period, float yaw, float offset, List<AgeVisualSettings.Stop> gradient, List<AgeVisualSettings.Stop> sunset) {
            this(kind, seed, period, yaw, offset, gradient, sunset, seed);
        }
        public Body {
            gradient = List.copyOf(gradient); sunset = List.copyOf(sunset);
            if ((kind == Kind.STARS || kind == Kind.TWINKLE || kind == Kind.END_SKY) && gradient.isEmpty()) throw new IllegalArgumentException("Missing celestial color");
            if (kind == null || period < 0 || period > 480000 || !Float.isFinite(yaw) || yaw < -360 || yaw > 360
                    || !Float.isFinite(offset) || offset < -.5 || offset > .5 || gradient.size() > 256 || sunset.size() > 256)
                throw new IllegalArgumentException("Invalid celestial body");
        }
    }
    public AgeCelestialSettings {
        bodies = List.copyOf(bodies); sunset = List.copyOf(sunset);
        if (bodies.size() > MAX_BODIES || sunset.size() > 256 || legacyVanilla && (!bodies.isEmpty() || !sunset.isEmpty()))
            throw new IllegalArgumentException("Invalid celestial plan");
        int totalStops = sunset.size();
        for (Body body : bodies) totalStops += body.gradient().size() + body.sunset().size();
        if (totalStops > 1024) throw new IllegalArgumentException("Oversized celestial gradients");
    }
    public boolean endSky() { return bodies.stream().anyMatch(body -> body.kind() == Kind.END_SKY); }
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1); tag.putBoolean("Legacy", legacyVanilla);
        tag.put("Sunset", stops(sunset));
        ListTag list = new ListTag();
        for (Body body : bodies) {
            CompoundTag value = new CompoundTag(); value.putString("Kind", body.kind().name()); value.putLong("Seed", body.seed());
            value.putLong("RenderSeed", body.renderSeed()); value.putLong("Period", body.period()); value.putFloat("Yaw", body.yaw()); value.putFloat("Offset", body.offset());
            value.put("Gradient", stops(body.gradient())); value.put("Sunset", stops(body.sunset())); list.add(value);
        }
        tag.put("Bodies", list); return tag;
    }
    public static AgeCelestialSettings fromTag(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unknown celestial plan version");
        ListTag list = tag.getList("Bodies", Tag.TAG_COMPOUND);
        if (list.size() > MAX_BODIES) throw new IllegalArgumentException("Too many celestial bodies");
        List<Body> bodies = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag value = list.getCompound(i);
            bodies.add(new Body(Kind.valueOf(value.getString("Kind")), value.getLong("Seed"), value.getLong("Period"),
                    value.getFloat("Yaw"), value.getFloat("Offset"), readStops(value, "Gradient"), readStops(value, "Sunset"), value.getLong("RenderSeed")));
        }
        return new AgeCelestialSettings(tag.getBoolean("Legacy"), bodies, readStops(tag, "Sunset"));
    }
    private static ListTag stops(List<AgeVisualSettings.Stop> stops) {
        ListTag list = new ListTag();
        for (var stop : stops) {
            CompoundTag point = new CompoundTag(); point.putFloat("R", stop.color().red()); point.putFloat("G", stop.color().green());
            point.putFloat("B", stop.color().blue()); point.putFloat("Duration", stop.duration()); list.add(point);
        }
        return list;
    }
    private static List<AgeVisualSettings.Stop> readStops(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        if (list.size() > 256) throw new IllegalArgumentException("Too many celestial gradient stops");
        List<AgeVisualSettings.Stop> stops = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag point = list.getCompound(i);
            stops.add(new AgeVisualSettings.Stop(new AgeVisualSettings.Rgb(point.getFloat("R"), point.getFloat("G"), point.getFloat("B")), point.getFloat("Duration")));
        }
        return stops;
    }
}
