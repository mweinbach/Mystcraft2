package ports.buildcraft.robotics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
    /** How far, in chunks, a map request may sit from the open planner. */
    public static final int MAP_CHUNK_RADIUS = 32;
    /** Scans accepted from one player during one tick. */
    public static final int MAP_REQUESTS_PER_TICK = 4;
    private static final Map<UUID, Budget> MAP_BUDGETS = new HashMap<>();

    private ZonePackets() {}

    private static final class Budget {
        private long tick;
        private int count;
    }

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

    /**
     * Upstream scanned whatever slice the sender named, in the sender's world, and loaded the chunk to do it.
     * A request is scanned only while that player's planner GUI is open and still valid, for a slice inside the
     * world's build height, a loaded chunk within {@link #MAP_CHUNK_RADIUS} of the planner, and at most
     * {@link #MAP_REQUESTS_PER_TICK} times a tick. Anything else is ignored, so a hostile key cannot walk the
     * height of the world or force terrain to load.
     */
    public static void receiveMapRequest(ServerPlayer player, FriendlyByteBuf buffer) {
        ZonePlannerMapChunkKey key = ZonePlannerMapChunkKey.read(buffer);
        if (!mayServeMap(player, key)) return;
        ZonePlannerMapChunk chunk = ZonePlannerMapDataServer.INSTANCE.getChunk(player.level, key);
        Platform.get().sendToPlayer(player, MAP_RESPONSE_CHANNEL, payload -> {
            key.write(payload);
            chunk.write(payload);
        });
    }

    /** Whether {@link #receiveMapRequest} would scan this key. A refusal sends nothing. */
    public static boolean mayServeMap(ServerPlayer player, ZonePlannerMapChunkKey key) {
        if (!(player.containerMenu instanceof ZonePlannerMenu menu) || !menu.stillValid(player)) return false;
        ServerLevel world = player.getLevel();
        if (key.dimensionId != ZonePlannerMapChunkKey.dimensionId(world)) return false;
        if (key.level < ZonePlannerMapChunkKey.minSlice(world) || key.level > ZonePlannerMapChunkKey.maxSlice(world)) return false;
        BlockPos origin = menu.pos();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (Math.abs(key.chunkPos.x - chunkX) > MAP_CHUNK_RADIUS || Math.abs(key.chunkPos.z - chunkZ) > MAP_CHUNK_RADIUS) return false;
        if (world.getChunkSource().getChunkNow(key.chunkPos.x, key.chunkPos.z) == null) return false;
        return budget(player, world.getGameTime());
    }

    private static boolean budget(ServerPlayer player, long tick) {
        if (MAP_BUDGETS.size() > 32) {
            Iterator<Budget> budgets = MAP_BUDGETS.values().iterator();
            while (budgets.hasNext()) {
                if (tick - budgets.next().tick > 200) budgets.remove();
            }
        }
        Budget budget = MAP_BUDGETS.computeIfAbsent(player.getUUID(), id -> new Budget());
        if (budget.tick != tick) {
            budget.tick = tick;
            budget.count = 0;
        }
        if (budget.count >= MAP_REQUESTS_PER_TICK) return false;
        budget.count++;
        return true;
    }
}
