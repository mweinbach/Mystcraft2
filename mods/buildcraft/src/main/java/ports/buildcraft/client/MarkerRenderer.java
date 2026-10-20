package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MarkerConnectorItem;
import ports.buildcraft.core.MarkerPathBlockEntity;
import ports.buildcraft.core.MarkerVolumeBlockEntity;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.core.marker.PathConnection;
import ports.buildcraft.core.marker.VolumeConnection;
import ports.buildcraft.lib.marker.MarkerBlockEntity;
import ports.buildcraft.lib.marker.MarkerConnection;
import ports.buildcraft.lib.marker.MarkerSubCache;

/**
 * Upstream's marker lasers: {@code MarkerRenderer} draws each connection, here from the first of its
 * markers that the client has loaded; {@code RenderMarkerVolume} draws a powered land mark's signals;
 * and {@code RenderTickListener} draws the connections a held marker connector could make, in the
 * default colour for the one the player is looking along.
 */
public abstract class MarkerRenderer<C extends MarkerConnection<C>, T extends MarkerBlockEntity<C>> implements BlockEntityRenderer<T> {
    private static final double CONNECTED_SCALE = 1 / 16.05;

    @Override
    public void render(T marker, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = marker.getLevel();
        if (level == null) return;
        BlockPos pos = marker.getBlockPos();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        MarkerSubCache<C> cache = marker.getLocalCache();
        C connection = cache.getConnection(pos);
        if (connection != null && isFirstLoaded(cache, connection, pos)) renderConnection(level, pos, poseStack, consumer, connection);
        renderMarker(marker, level, pos, poseStack, consumer);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (player.getMainHandItem().is(BCCore.MARKER_CONNECTOR.get()) || player.getOffhandItem().is(BCCore.MARKER_CONNECTOR.get()))) {
            renderPossible(level, pos, poseStack, consumer, cache, possibleType(), player);
        }
    }

    protected abstract void renderConnection(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, C connection);

    protected void renderMarker(T marker, Level level, BlockPos pos, PoseStack poseStack, VertexConsumer consumer) {}

    protected abstract LaserRenderer.Type possibleType();

    private static boolean isFirstLoaded(MarkerSubCache<?> cache, MarkerConnection<?> connection, BlockPos pos) {
        for (BlockPos p : connection.getMarkerPositions()) {
            if (cache.getMarker(p) != null) return p.equals(pos);
        }
        return false;
    }

    private static void renderPossible(Level level, BlockPos a, PoseStack poseStack, VertexConsumer consumer, MarkerSubCache<?> cache,
            LaserRenderer.Type possible, LocalPlayer player) {
        for (BlockPos b : cache.getValidConnections(a)) {
            // Only render each pair once.
            if (a.asLong() > b.asLong()) continue;
            LaserRenderer.Type type = MarkerConnectorItem.doesInteract(a, b, player) ? LaserRenderer.MARKER_DEFAULT_POSSIBLE : possible;
            Vec3[] ends = between(a, b);
            LaserRenderer.render(level, a, poseStack, consumer, type, ends[0], ends[1], 1 / 16.0);
        }
    }

    /** The line between two markers' centres, an eighth of a block shorter at each end. */
    static Vec3[] between(BlockPos a, BlockPos b) {
        Vec3 start = Vec3.atCenterOf(a);
        Vec3 end = Vec3.atCenterOf(b);
        Vec3 direction = end.subtract(start).normalize();
        return new Vec3[] {start.add(direction.scale(0.125)), end.subtract(direction.scale(0.125))};
    }

    @Override
    public boolean shouldRenderOffScreen(T marker) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    /** Land marks: the connected box, and signal lasers while powered. */
    public static final class Volume extends MarkerRenderer<VolumeConnection, MarkerVolumeBlockEntity> {
        private static final double SIGNAL_SCALE = 1 / 16.2;

        @Override
        protected void renderConnection(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, VolumeConnection connection) {
            LaserRenderer.box(level, origin, poseStack, consumer, connection.getBox(), LaserRenderer.MARKER_VOLUME_CONNECTED, true);
        }

        @Override
        protected void renderMarker(MarkerVolumeBlockEntity marker, Level level, BlockPos pos, PoseStack poseStack, VertexConsumer consumer) {
            if (!marker.isShowingSignals()) return;
            VolumeConnection volume = marker.getCurrentConnection();
            Set<Direction.Axis> taken = volume == null ? Set.of() : volume.getConnectedAxis();
            Vec3 start = Vec3.atCenterOf(pos);
            for (Direction face : Direction.values()) {
                if (taken.contains(face.getAxis())) continue;
                Vec3 end = start.add(Vec3.atLowerCornerOf(face.getNormal()).scale(MarkerCaches.MAX_DISTANCE));
                Vec3[] ends = LaserRenderer.axisLaser(start, end, face.getAxis());
                LaserRenderer.render(level, pos, poseStack, consumer, LaserRenderer.MARKER_VOLUME_SIGNAL, ends[0], ends[1], SIGNAL_SCALE);
            }
        }

        @Override
        protected LaserRenderer.Type possibleType() {
            return LaserRenderer.MARKER_VOLUME_POSSIBLE;
        }
    }

    /** Path marks: a laser along each step of the path. */
    public static final class Path extends MarkerRenderer<PathConnection, MarkerPathBlockEntity> {
        @Override
        protected void renderConnection(Level level, BlockPos origin, PoseStack poseStack, VertexConsumer consumer, PathConnection connection) {
            List<BlockPos> positions = connection.getMarkerPositions();
            for (int i = 1; i < positions.size(); i++) {
                Vec3[] ends = between(positions.get(i - 1), positions.get(i));
                LaserRenderer.render(level, origin, poseStack, consumer, LaserRenderer.MARKER_PATH_CONNECTED, ends[0], ends[1], CONNECTED_SCALE);
            }
        }

        @Override
        protected LaserRenderer.Type possibleType() {
            return LaserRenderer.MARKER_PATH_POSSIBLE;
        }
    }
}
