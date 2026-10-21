package ports.buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.robotics.zone.ZonePlan;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunk;
import ports.buildcraft.robotics.zone.ZonePlannerMapChunkKey;
import ports.buildcraft.robotics.zone.ZonePlannerMapDataServer;

/** The zone planner's client-to-server plan edit and its map-slice request. */
public final class ZonePackets {
    public static final ResourceLocation PLAN_CHANNEL = BuildCraft.id("zone_plan");
    public static final ResourceLocation MAP_REQUEST_CHANNEL = BuildCraft.id("zone_map_request");
    public static final ResourceLocation MAP_RESPONSE_CHANNEL = BuildCraft.id("zone_map_response");
    /** A painted plan larger than this is ignored. Upstream's packet had no cap. */
    public static final int MAX_CHUNKS = 4096;

    private ZonePackets() {}

    /**
     * Upstream applied a plan packet to whichever tile it named. Here it names the planner and applies only when
     * that planner's GUI is the one the sender has open.
     */
    public static void receivePlan(ServerPlayer player, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int index = buffer.readUnsignedShort();
        if (index >= ZonePlannerBlockEntity.LAYERS || buffer.readInt() > MAX_CHUNKS) return;
        buffer.readerIndex(buffer.readerIndex() - 4);
        ZonePlan plan = new ZonePlan();
        plan.read(buffer);
        if (player.containerMenu instanceof ZonePlannerMenu menu && menu.pos().equals(pos) && menu.stillValid(player)) {
            menu.applyPlan(index, plan);
        }
    }

    /** Upstream {@code MessageZoneMapRequest}: the slice is scanned in the sender's world. */
    public static void receiveMapRequest(ServerPlayer player, FriendlyByteBuf buffer) {
        ZonePlannerMapChunkKey key = ZonePlannerMapChunkKey.read(buffer);
        ZonePlannerMapChunk chunk = ZonePlannerMapDataServer.INSTANCE.getChunk(player.level, key);
        Platform.get().sendToPlayer(player, MAP_RESPONSE_CHANNEL, payload -> {
            key.write(payload);
            chunk.write(payload);
        });
    }
}
