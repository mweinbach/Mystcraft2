package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.age.*;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.writing.PageData;

/** Disposable-world integration checks: actual Age climate, vanilla snow checks and persistent sleep reset. */
public final class AgeEnvironmentSmoke {
    private AgeEnvironmentSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        var passed = new ArrayList<String>();
        check(server.isSameThread(), "environment smoke must run on server thread");
        var player = new ServerPlayer(server, origin, new GameProfile(UUID.randomUUID(), "MystSmokeClimate"), null);
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener callback) {}
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        try {
            var pages = new ArrayList<CompoundTag>();
            pages.add(PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get())));
            for (String id : List.of("terrainflat", "biome", "bioconsingle", "weathersnow", "lightingnormal", "modcolorred", "colorcloud", "modcolorblue", "colorgrass", "modeast", "rainbow", "nohorizon", "modfull", "modnorth", "modnoon", "sunnormal", "moondark", "starsdark", "featurelargedummy", "featuremediumdummy", "featuresmalldummy")) {
                CompoundTag page = new CompoundTag(); PageData.setLegacySymbol(page, "mystcraft:" + id); if (id.equals("biome")) page.putString("Biome", "minecraft:plains"); pages.add(page);
            }
            // Explicitly complete every mandatory root family so random repair cannot add trees,
            // spheres or other structures above the precipitation probe.
            var prepared = AgeAllocation.compile(server, pages, 22345L);
            check(prepared.compilation().successful(), "controlled climate book rejected:" + prepared.compilation().diagnostics());
            check(net.mystcraft.minecraft.age.generation.ClassicGenerationPlan.fromTag(prepared.state().getCompound(
                    net.mystcraft.minecraft.age.generation.ClassicGenerationPlan.KEY)).alterations().isEmpty(), "controlled climate book unexpectedly requested random generation features");
            ItemStack book = AgeBookData.create("Climate smoke", pages);
            CompoundTag props = new CompoundTag(); props.putLong("Seed", 22345L); book.getOrCreateTag().put("Props", props);
            check(AgeManager.prepare(player, book), "climate fixture must allocate a real Age");
            ServerLevel world = server.getLevel(LinkDestination.read(book).orElseThrow().dimension());
            check(world instanceof AgeServerLevel, "climate fixture is an independent Age");
            var age = (AgeServerLevel) world;
            var entry = AgeManager.data(server).get(AgeBookData.ageId(book).orElseThrow());
            var data = (ServerLevelData) world.getLevelData();
            int height = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 16, 16);
            var pos = new BlockPos(16, Math.max(80, height + 1), 16);
            world.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            check(world.canSeeSky(pos) && world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() <= pos.getY(),
                    "climate probe must be exposed to sky before testing precipitation");
            var biome = world.getBiome(pos).value();
            boolean nativeSnow = biome.shouldSnow(origin, pos);
            check(AgeWeatherContext.with(world, () -> biome.shouldSnow(world, pos)), "Snow must permit native snow placement in warm plains");
            check(!((Level) world).isRainingAt(pos), "Snow must not report liquid rain");
            check(nativeSnow == biome.shouldSnow(origin, pos), "Age climate must not mutate shared biome temperature");
            var settings = AgeManager.visualSettings(world);
            check(settings.environment().weather().equals("snow") && !settings.environment().cloud().isEmpty()
                    && !settings.environment().grass().isEmpty(), "actual Age freezes cloud/grass and weather channels");
            check(!settings.environment().drawVoid() && !settings.celestials().legacyVanilla()
                    && settings.celestials().bodies().stream().anyMatch(body -> body.kind() == net.mystcraft.minecraft.age.visual.AgeCelestialSettings.Kind.RAINBOW),
                    "completed environment realizes Rainbow and NoHorizon in the executing custom renderer plan");
            check(settings.equals(AgeVisualSettings.fromTag(settings.toTag())), "actual frozen environment survives NBT");
            passed.add("Environment: real warm-biome Snow Age permits native snow placement, rejects liquid rain and preserves other-world climate; visual settings freeze and roundtrip");

            data.setRaining(false);
            check(!data.isRaining() && !age.weatherActive(), "sleep/reset disables toggleable precipitation");
            CompoundTag restored = entry.state().copy();
            check(!AgeWeatherRules.active(restored) && restored.getInt("MystcraftWeatherReset") == 12000, "disabled flag and reset counter persist");
            for (int i = 0; i < 12000; i++) AgeWeatherRules.tick(restored);
            check(AgeWeatherRules.active(restored), "disabled precipitation resumes at exactly 12000 ticks");
            data.setRaining(true);
            ((Level) world).setRainLevel(1);
            for (String mode : List.of("rain", "storm", "cloudy")) {
                entry.state().putString("MystcraftWeather", mode);
                check(data.isRaining(), mode + " has full weather strength");
                check(data.isThundering() == mode.equals("storm"), "only Storm has thunder strength");
                check(age.weatherMode().equals(mode) && world.canSeeSky(pos) && ((Level) world).isRaining(),
                        "climate probe lost controlled weather/sky state mode=" + mode + " actual=" + age.weatherMode());
                check(((Level) world).isRainingAt(pos) == !mode.equals("cloudy"), "Rain/Storm versus Cloudy precipitation distinction mode=" + mode
                        + " active=" + age.weatherActive() + " sky=" + world.canSeeSky(pos) + " rain=" + world.getRainLevel(1)
                        + " height=" + world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() + " probe=" + pos);
                check(!AgeWeatherContext.with(world, () -> biome.shouldSnow(world, pos)), "warm rain/cloudy must not create plains snow");
            }
            entry.state().putString("MystcraftWeather", "snow");
            passed.add("Environment: Rain/Storm produce liquid rain, only Storm thunders, Cloudy darkens without precipitation; sleep-disable cooldown persists and resumes exactly");
        } finally { ((Entity) player).remove(Entity.RemovalReason.DISCARDED); }
        return passed;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
