package ports.gridwright.deepdark;

import ports.gridwright.Gridwright;
import ports.gridwright.deepdark.DeepDarkReturnPoints.ReturnPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Travel through a Deep Dark portal. Going in keeps x and z (1:1) and remembers where the player came
 * from; coming out returns them there, or to the overworld surface if that place or its dimension is gone.
 */
public final class DeepDarkTravel {

    public static final ResourceKey<Level> DEEP_DARK = ResourceKey.create(Registry.DIMENSION_REGISTRY,
            new ResourceLocation(Gridwright.MOD_ID, "deep_dark"));
    /** Ticks after a use before the same player's next use counts, so a double click cannot ping-pong. */
    public static final int COOLDOWN_TICKS = 60;

    /** What a portal use did. */
    public enum Outcome { NO_DIMENSION, NOT_A_PLAYER, COOLDOWN, SCHEDULED }

    /** Where a return trip lands. */
    public record Destination(ServerLevel level, Vec3 position, float yRot, float xRot) { }

    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private DeepDarkTravel() { }

    /**
     * Handles a portal right-click. The trip itself runs as a server task straight after the current
     * tick, so the teleport never happens in the middle of the click's packet handling.
     */
    public static Outcome use(ServerPlayer player) {
        return use(player, player.server.getLevel(DEEP_DARK));
    }

    /** {@link #use(ServerPlayer)} with the Deep Dark level given ({@code null} if the server has none). */
    public static Outcome use(ServerPlayer player, @Nullable ServerLevel deepDark) {
        MinecraftServer server = player.server;
        if (deepDark == null) {
            tell(player, "message.gridwright.deep_dark.missing");
            return Outcome.NO_DIMENSION;
        }
        // Fake players (machines) and players that are not connected never travel.
        if (server.getPlayerList().getPlayer(player.getUUID()) != player) return Outcome.NOT_A_PLAYER;
        long now = server.overworld().getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now >= last && now - last < COOLDOWN_TICKS) return Outcome.COOLDOWN;
        LAST_USE.put(player.getUUID(), now);
        server.tell(new TickTask(server.getTickCount(), () -> travel(player)));
        return Outcome.SCHEDULED;
    }

    static void travel(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (player.isRemoved() || !player.isAlive() || player.hasDisconnected()) return;
        ServerLevel deepDark = server.getLevel(DEEP_DARK);
        if (deepDark == null) {
            tell(player, "message.gridwright.deep_dark.missing");
            return;
        }
        if (player.level.dimension().equals(DEEP_DARK)) leave(player);
        else enter(player, deepDark);
    }

    private static void enter(ServerPlayer player, ServerLevel deepDark) {
        DeepDarkArrival.Arrival arrival = DeepDarkArrival.prepare(deepDark, player.blockPosition());
        if (arrival == null) {
            tell(player, "message.gridwright.deep_dark.no_arrival");
            return;
        }
        DeepDarkReturnPoints points = DeepDarkReturnPoints.get(player.server);
        points.put(player.getUUID(), new ReturnPoint(player.level.dimension(), player.position(), player.getYRot(), player.getXRot()));
        BlockPos feet = arrival.feet();
        player.teleportTo(deepDark, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, player.getYRot(), player.getXRot());
        if (player.level != deepDark) {
            // Another mod vetoed the trip; nothing to come back to.
            points.remove(player.getUUID());
            tell(player, "message.gridwright.deep_dark.blocked");
            return;
        }
        settle(player);
    }

    private static void leave(ServerPlayer player) {
        DeepDarkReturnPoints points = DeepDarkReturnPoints.get(player.server);
        Destination destination = returnDestination(player, points.get(player.getUUID()));
        if (destination == null) {
            // Nowhere safe to go; stay put and keep the record so a later try can still use it.
            tell(player, "message.gridwright.deep_dark.no_return");
            return;
        }
        player.teleportTo(destination.level(), destination.position().x, destination.position().y, destination.position().z,
                destination.yRot(), destination.xRot());
        if (player.level == destination.level()) {
            points.remove(player.getUUID());
            settle(player);
        } else {
            tell(player, "message.gridwright.deep_dark.blocked");
        }
    }

    private static void settle(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    /**
     * Where {@code player} should come out: the recorded spot if its dimension still exists and the spot is
     * still clear, else a clear spot near it, else (for dimensions with a sky) the surface above it, else
     * the overworld surface at the player's own x/z, else the surface at world spawn, else a standable spot
     * at or near the world spawn. {@code null} if none of those is safe.
     */
    @Nullable
    public static Destination returnDestination(ServerPlayer player, @Nullable ReturnPoint point) {
        MinecraftServer server = player.server;
        if (point != null && !point.dimension().equals(DEEP_DARK)) {
            ServerLevel level = server.getLevel(point.dimension());
            if (level != null) {
                if (clear(level, player, point.position())) return new Destination(level, point.position(), point.yRot(), point.xRot());
                BlockPos recorded = new BlockPos(point.position());
                BlockPos near = nearbyStandingSpot(level, recorded);
                if (near != null) return new Destination(level, Vec3.atBottomCenterOf(near), point.yRot(), point.xRot());
                if (!level.dimensionType().hasCeiling()) {
                    Vec3 surface = surface(level, recorded.getX(), recorded.getZ());
                    if (surface != null) return new Destination(level, surface, point.yRot(), point.xRot());
                }
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos here = player.blockPosition();
        Vec3 surface = surface(overworld, here.getX(), here.getZ());
        if (surface == null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            surface = surface(overworld, spawn.getX(), spawn.getZ());
            if (surface == null) surface = nearSpawn(overworld, spawn);
            if (surface == null) return null;
        }
        return new Destination(overworld, surface, player.getYRot(), player.getXRot());
    }

    /**
     * A standable spot at or near {@code spawn} (moved inside the world border first), or {@code null}
     * if there is none.
     */
    @Nullable
    public static Vec3 nearSpawn(ServerLevel level, BlockPos spawn) {
        BlockPos near = nearbyStandingSpot(level, columnInside(level.getWorldBorder(), spawn.getX(), spawn.getZ()).atY(spawn.getY()));
        return near == null ? null : Vec3.atBottomCenterOf(near);
    }

    /** True if a standing player fits at {@code position} without touching blocks or anything harmful. */
    public static boolean clear(ServerLevel level, @Nullable ServerPlayer player, Vec3 position) {
        if (level.isOutsideBuildHeight(new BlockPos(position))) return false;
        AABB box = (player != null ? player.getDimensions(Pose.STANDING) : EntityType.PLAYER.getDimensions())
                .makeBoundingBox(position);
        if (!DeepDarkArrival.contains(level.getWorldBorder(), box)) return false;
        // Collision queries skip unloaded chunks, so load every chunk the box touches first.
        AABB touched = box.inflate(0.001);
        for (int cx = SectionPos.blockToSectionCoord(Mth.floor(touched.minX)); cx <= SectionPos.blockToSectionCoord(Mth.floor(touched.maxX)); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(Mth.floor(touched.minZ)); cz <= SectionPos.blockToSectionCoord(Mth.floor(touched.maxZ)); cz++) {
                level.getChunk(cx, cz);
            }
        }
        if (!level.noCollision(player, box)) return false;
        // The slightly larger box also takes in the floor block.
        if (level.getBlockStates(touched).anyMatch(DeepDarkArrival::harmful)) return false;
        // Something to stand on within a block (no fall damage), or water or a ladder to hold on to.
        AABB below = new AABB(box.minX, box.minY - 1.0, box.minZ, box.maxX, box.minY, box.maxZ);
        return !level.noCollision(player, below)
                || level.getBlockStates(touched).anyMatch(state -> !state.getFluidState().isEmpty() || state.is(BlockTags.CLIMBABLE));
    }

    @Nullable
    private static BlockPos nearbyStandingSpot(ServerLevel level, BlockPos center) {
        for (BlockPos offset : DeepDarkArrival.offsets(2, 6)) {
            BlockPos feet = center.offset(offset);
            if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) continue;
            if (DeepDarkArrival.standable(level, feet)) return feet.immutable();
        }
        return null;
    }

    /**
     * A safe place on the surface of the column nearest {@code x, z} that lies wholly inside the world
     * border: the top of the MOTION_BLOCKING heightmap if a player fits there clear of anything harmful
     * (water counts as a floor), else a standable spot near it, else {@code null} (for example an empty
     * column or a lava sea).
     */
    @Nullable
    public static Vec3 surface(ServerLevel level, int x, int z) {
        BlockPos column = columnInside(level.getWorldBorder(), x, z);
        x = column.getX();
        z = column.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight() || y + 1 >= level.getMaxBuildHeight()) return null;
        Vec3 top = new Vec3(x + 0.5, y, z + 0.5);
        if (clear(level, null, top)) return top;
        BlockPos near = nearbyStandingSpot(level, new BlockPos(x, y, z));
        return near == null ? null : Vec3.atBottomCenterOf(near);
    }

    /**
     * The block column nearest {@code x, z} that lies wholly inside {@code border} (y is 0). If the
     * border is narrower than a block, the column at its centre.
     */
    public static BlockPos columnInside(WorldBorder border, int x, int z) {
        int minX = Mth.ceil(border.getMinX()), maxX = Mth.floor(border.getMaxX()) - 1;
        int minZ = Mth.ceil(border.getMinZ()), maxZ = Mth.floor(border.getMaxZ()) - 1;
        int cx = minX <= maxX ? Mth.clamp(x, minX, maxX) : Mth.floor(border.getCenterX());
        int cz = minZ <= maxZ ? Mth.clamp(z, minZ, maxZ) : Mth.floor(border.getCenterZ());
        return new BlockPos(cx, 0, cz);
    }

    private static void tell(ServerPlayer player, String key) {
        if (player.connection != null) player.displayClientMessage(Component.translatable(key), true);
    }
}
