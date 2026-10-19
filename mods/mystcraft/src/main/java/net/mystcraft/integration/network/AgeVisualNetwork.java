package net.mystcraft.integration.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative, one-way presentation snapshots; clients never submit visual settings. */
public final class AgeVisualNetwork {
    public static final int MAX_ENTRIES = 16;
    public static final int MAX_STOPS = 1024;
    private static final List<String> LIGHTING = List.of("normal", "dark", "bright");
    private static final List<String> MODES = List.of("natural", "day", "night", "gradient", "constant");
    private static Transport transport;
    private AgeVisualNetwork() {}
    public interface Transport { void sendToPlayer(ServerPlayer player, Snapshot packet); }
    public static void initialize(Transport implementation) { transport = Objects.requireNonNull(implementation); }

    public record Entry(ResourceLocation dimension, AgeVisualSettings settings) {
        public Entry {
            Objects.requireNonNull(settings);
            if (dimension == null || !dimension.getNamespace().equals("mystcraft") || !dimension.getPath().matches("age_[1-9][0-9]{0,18}"))
                throw new IllegalArgumentException("Invalid Age visual dimension");
            validate(settings);
        }
    }
    public record Snapshot(boolean reset, List<Entry> entries) {
        public Snapshot {
            entries = List.copyOf(entries);
            if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many Age visual entries");
            var ids = new HashSet<ResourceLocation>();
            for (Entry entry : entries) if (!ids.add(entry.dimension())) throw new IllegalArgumentException("Duplicate Age visual entry");
        }
        public static void encode(Snapshot packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(3); buffer.writeBoolean(packet.reset()); buffer.writeVarInt(packet.entries().size());
            for (Entry entry : packet.entries()) {
                buffer.writeUtf(entry.dimension().toString(), 128);
                encodeSettings(buffer, entry.settings());
            }
        }
        public static Snapshot decode(FriendlyByteBuf buffer) {
            if (buffer.readVarInt() != 3) throw new IllegalArgumentException("Unknown Age presentation packet version");
            boolean reset = buffer.readBoolean();
            int count = bounded(buffer.readVarInt(), MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(new Entry(new ResourceLocation(buffer.readUtf(128)), decodeSettings(buffer)));
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing Age visual payload");
            return new Snapshot(reset, entries);
        }
    }
    private static int bounded(int value, int limit) {
        if (value < 0 || value > limit) throw new IllegalArgumentException("Oversized Age visual payload");
        return value;
    }
    private static void validate(AgeVisualSettings settings) {
        int stops = 0;
        for (var group : List.of(settings.sky(), settings.fog(), settings.water(), settings.environment().cloud(), settings.environment().grass(), settings.environment().foliage()))
            for (var provider : group) stops += provider.stops().size();
        bounded(stops, MAX_STOPS);
    }
    private static void encodeSettings(FriendlyByteBuf buffer, AgeVisualSettings settings) {
        validate(settings);
        buffer.writeByte(LIGHTING.indexOf(settings.lighting()));
        for (var group : List.of(settings.sky(), settings.fog(), settings.water(), settings.environment().cloud(), settings.environment().grass(), settings.environment().foliage())) {
            buffer.writeVarInt(group.size());
            for (var provider : group) {
                buffer.writeByte(MODES.indexOf(provider.mode())); buffer.writeVarInt(provider.stops().size());
                for (var stop : provider.stops()) {
                    buffer.writeFloat(stop.color().red()); buffer.writeFloat(stop.color().green()); buffer.writeFloat(stop.color().blue());
                    buffer.writeFloat(stop.duration());
                }
            }
        }
        buffer.writeBoolean(!Float.isNaN(settings.environment().cloudHeight()));
        if (!Float.isNaN(settings.environment().cloudHeight())) buffer.writeFloat(settings.environment().cloudHeight());
        buffer.writeBoolean(settings.environment().drawVoid()); buffer.writeFloat(settings.environment().horizon()); buffer.writeUtf(settings.environment().weather(), 16);
        var celestial = settings.celestials();
        buffer.writeBoolean(celestial.legacyVanilla()); buffer.writeVarInt(celestial.bodies().size());
        for (var body : celestial.bodies()) {
            buffer.writeByte(body.kind().ordinal()); buffer.writeLong(body.seed()); buffer.writeLong(body.renderSeed()); buffer.writeVarLong(body.period());
            buffer.writeFloat(body.yaw()); buffer.writeFloat(body.offset());
            encodeStops(buffer, body.gradient()); encodeStops(buffer, body.sunset());
        }
        encodeStops(buffer, celestial.sunset());
    }
    private static void encodeStops(FriendlyByteBuf buffer, List<AgeVisualSettings.Stop> stops) {
        buffer.writeVarInt(stops.size());
        for (var stop : stops) {
            buffer.writeFloat(stop.color().red()); buffer.writeFloat(stop.color().green()); buffer.writeFloat(stop.color().blue()); buffer.writeFloat(stop.duration());
        }
    }
    private static List<AgeVisualSettings.Stop> decodeStops(FriendlyByteBuf buffer) {
        int count = bounded(buffer.readVarInt(), 256);
        List<AgeVisualSettings.Stop> stops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) stops.add(new AgeVisualSettings.Stop(new AgeVisualSettings.Rgb(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()), buffer.readFloat()));
        return stops;
    }
    private static AgeVisualSettings decodeSettings(FriendlyByteBuf buffer) {
        int lighting = bounded(buffer.readUnsignedByte(), LIGHTING.size() - 1);
        List<List<AgeVisualSettings.Provider>> groups = new ArrayList<>(6);
        int providers = 0, stops = 0;
        for (int group = 0; group < 6; group++) {
            int count = bounded(buffer.readVarInt(), AgeVisualSettings.MAX_PROVIDERS - providers);
            providers += count;
            List<AgeVisualSettings.Provider> values = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String mode = MODES.get(bounded(buffer.readUnsignedByte(), MODES.size() - 1));
                int length = bounded(buffer.readVarInt(), Math.min(AgeVisualSettings.MAX_PROVIDERS, MAX_STOPS - stops));
                stops += length;
                List<AgeVisualSettings.Stop> points = new ArrayList<>(length);
                for (int j = 0; j < length; j++) points.add(new AgeVisualSettings.Stop(
                        new AgeVisualSettings.Rgb(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()), buffer.readFloat()));
                values.add(new AgeVisualSettings.Provider(mode, points));
            }
            groups.add(values);
        }
        boolean hasCloudHeight = buffer.readBoolean();
        float cloudHeight = hasCloudHeight ? buffer.readFloat() : Float.NaN;
        if (hasCloudHeight && !Float.isFinite(cloudHeight)) throw new IllegalArgumentException("Nonfinite cloud height");
        boolean drawVoid = buffer.readBoolean();
        float horizon = buffer.readFloat(); String weather = buffer.readUtf(16);
        boolean legacy = buffer.readBoolean();
        int count = bounded(buffer.readVarInt(), AgeCelestialSettings.MAX_BODIES);
        List<AgeCelestialSettings.Body> bodies = new ArrayList<>(count);
        int celestialStops = 0;
        for (int i = 0; i < count; i++) {
            var kind = AgeCelestialSettings.Kind.values()[bounded(buffer.readUnsignedByte(), AgeCelestialSettings.Kind.values().length - 1)];
            long seed = buffer.readLong(), renderSeed = buffer.readLong(), period = buffer.readVarLong();
            float yaw = buffer.readFloat(), offset = buffer.readFloat();
            var gradient = decodeStops(buffer); var sunset = decodeStops(buffer);
            celestialStops += gradient.size() + sunset.size(); bounded(celestialStops, 1024);
            bodies.add(new AgeCelestialSettings.Body(kind, seed, period, yaw, offset, gradient, sunset, renderSeed));
        }
        return new AgeVisualSettings(LIGHTING.get(lighting), groups.get(0), groups.get(1), groups.get(2),
                new AgeCelestialSettings(legacy, bodies, decodeStops(buffer)),
                new AgeVisualSettings.Environment(groups.get(3), groups.get(4), groups.get(5), cloudHeight, horizon, weather, drawVoid));
    }
    /** Full snapshots are streamed in bounded batches, even on worlds with many saved Ages. */
    public static void syncAll(ServerPlayer player) {
        List<Entry> batch = new ArrayList<>(MAX_ENTRIES);
        boolean reset = true;
        var saved = AgeManager.data(player.server);
        for (var age : saved.entries()) {
            boolean frozen = age.state().contains(AgeVisualSettings.STATE_KEY);
            AgeVisualSettings settings = AgeManager.visualSettings(age);
            if (!frozen) saved.setDirty();
            batch.add(new Entry(age.dimensionId(), settings));
            if (batch.size() == MAX_ENTRIES) {
                transport.sendToPlayer(player, new Snapshot(reset, batch)); batch = new ArrayList<>(MAX_ENTRIES); reset = false;
            }
        }
        if (!batch.isEmpty() || reset) transport.sendToPlayer(player, new Snapshot(reset, batch));
        // Keep the current Age at the end for clients whose presentation cache evicts older entries.
        syncCurrent(player);
    }
    public static void syncCurrent(ServerPlayer player) { syncLevel(player, player.getLevel()); }
    /** Can be sent before vanilla's respawn packet; the client caches profiles by destination key. */
    public static void syncLevel(ServerPlayer player, ServerLevel destination) {
        ResourceLocation id = destination.dimension().location();
        if (id.getNamespace().equals("mystcraft") && id.getPath().matches("age_[1-9][0-9]{0,18}"))
            transport.sendToPlayer(player, new Snapshot(false, List.of(new Entry(id, AgeManager.visualSettings(destination)))));
    }
    public static void syncAll(MinecraftServer server) { for (ServerPlayer player : server.getPlayerList().getPlayers()) syncAll(player); }
}
