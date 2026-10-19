package net.mystcraft.smoke;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.mystcraft.integration.network.AgeVisualNetwork;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Tests the actual bounded S2C visual binary codec, without loading any client class on the server. */
public final class AgeVisualNetworkSmoke {
    private AgeVisualNetworkSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        var red = new AgeVisualSettings.Stop(new AgeVisualSettings.Rgb(1, 0, 0), 1200);
        var blue = new AgeVisualSettings.Stop(new AgeVisualSettings.Rgb(0, 0, 1), 2400);
        var natural = new AgeVisualSettings.Provider("natural", List.of());
        var gradient = new AgeVisualSettings.Provider("gradient", List.of(red, blue));
        var celestials = new AgeCelestialSettings(false, List.of(
                new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.SUN, 123, 24000, -90, 0, List.of(), List.of(red)),
                new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.MOON, 456, 0, 0, -.25F, List.of(), List.of()),
                new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.TWINKLE, 789, 240000, -45, 0, List.of(blue), List.of()),
                new AgeCelestialSettings.Body(AgeCelestialSettings.Kind.RAINBOW, 987, 0, -90, 0, List.of(), List.of())), List.of(blue));
        var settings = new AgeVisualSettings("dark", List.of(natural, gradient), List.of(new AgeVisualSettings.Provider("day", List.of(red))),
                List.of(new AgeVisualSettings.Provider("constant", List.of(blue))), celestials,
                new AgeVisualSettings.Environment(List.of(gradient), List.of(natural), List.of(new AgeVisualSettings.Provider("constant", List.of(red))), 42.5F, 0, "snow"));
        var packet = new AgeVisualNetwork.Snapshot(true, List.of(new AgeVisualNetwork.Entry(id(1), settings),
                new AgeVisualNetwork.Entry(id(2), AgeVisualSettings.DEFAULT)));
        FriendlyByteBuf data = buffer();
        try {
            AgeVisualNetwork.Snapshot.encode(packet, data);
            require(data.readableBytes() < 1024, "Small visual snapshot grew unexpectedly");
            require(packet.equals(AgeVisualNetwork.Snapshot.decode(data)) && !data.isReadable(), "Visual snapshot lost lighting/provider order/gradient/natural/default values");
        } finally { data.release(); }
        passed.add("visual transport: full binary snapshot roundtrips dimension IDs, reset flag, lighting, ordered natural/gradient/day/constant providers, RGB and durations without client classes");

        List<AgeVisualNetwork.Entry> many = new ArrayList<>();
        for (int i = 1; i <= AgeVisualNetwork.MAX_ENTRIES; i++) many.add(new AgeVisualNetwork.Entry(id(i), settings));
        data = buffer();
        try {
            var batch = new AgeVisualNetwork.Snapshot(false, many);
            AgeVisualNetwork.Snapshot.encode(batch, data);
            require(batch.equals(AgeVisualNetwork.Snapshot.decode(data)), "Maximum profile batch changed in transit");
        } finally { data.release(); }
        reject(() -> new AgeVisualNetwork.Entry(new ResourceLocation("minecraft", "overworld"), settings), "Non-Age profile accepted");
        reject(() -> new AgeVisualNetwork.Snapshot(false, List.of(new AgeVisualNetwork.Entry(id(1), settings), new AgeVisualNetwork.Entry(id(1), settings))), "Duplicate dimension accepted");
        many.add(new AgeVisualNetwork.Entry(id(17), settings));
        reject(() -> new AgeVisualNetwork.Snapshot(false, many), "Oversized profile batch accepted");
        List<AgeVisualSettings.Stop> stops = new ArrayList<>();
        for (int i = 0; i < 256; i++) stops.add(red);
        List<AgeVisualSettings.Provider> providers = new ArrayList<>();
        for (int i = 0; i < 5; i++) providers.add(new AgeVisualSettings.Provider("gradient", stops));
        reject(() -> new AgeVisualNetwork.Entry(id(1), new AgeVisualSettings("normal", providers, List.of(), List.of())), "Oversized combined gradient stop count accepted");
        passed.add("visual transport: maximum batch roundtrips; non-Age/duplicate dimensions, oversized batches and aggregate gradient budgets reject");

        data = buffer();
        try {
            data.writeVarInt(3); data.writeBoolean(false); data.writeVarInt(AgeVisualNetwork.MAX_ENTRIES + 1);
            final FriendlyByteBuf malformed = data;
            reject(() -> AgeVisualNetwork.Snapshot.decode(malformed), "Oversized wire count accepted");
        } finally { data.release(); }
        data = buffer();
        try {
            prefix(data); data.writeByte(99);
            final FriendlyByteBuf malformed = data;
            reject(() -> AgeVisualNetwork.Snapshot.decode(malformed), "Unknown lighting enum accepted");
        } finally { data.release(); }
        data = buffer();
        try {
            prefix(data); data.writeByte(0); data.writeVarInt(1); data.writeByte(99);
            final FriendlyByteBuf malformed = data;
            reject(() -> AgeVisualNetwork.Snapshot.decode(malformed), "Unknown provider enum accepted");
        } finally { data.release(); }
        data = buffer();
        try {
            prefix(data); data.writeByte(0); data.writeVarInt(1); data.writeByte(4); data.writeVarInt(1);
            data.writeFloat(Float.NaN); data.writeFloat(0); data.writeFloat(0); data.writeFloat(1);
            data.writeVarInt(0); data.writeVarInt(0);
            final FriendlyByteBuf malformed = data;
            reject(() -> AgeVisualNetwork.Snapshot.decode(malformed), "Nonfinite wire RGB accepted");
        } finally { data.release(); }
        data = buffer();
        try {
            AgeVisualNetwork.Snapshot.encode(packet, data); data.writeByte(7);
            final FriendlyByteBuf malformed = data;
            reject(() -> AgeVisualNetwork.Snapshot.decode(malformed), "Trailing wire data accepted");
        } finally { data.release(); }
        passed.add("visual transport: malformed wire counts, enum codes, nonfinite RGB and trailing payload data reject before rendering");
        return passed;
    }
    private static void prefix(FriendlyByteBuf buffer) { buffer.writeVarInt(3); buffer.writeBoolean(false); buffer.writeVarInt(1); buffer.writeUtf(id(1).toString(), 128); }
    private static ResourceLocation id(int number) { return new ResourceLocation("mystcraft", "age_" + number); }
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }
    private static void reject(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
