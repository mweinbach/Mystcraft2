package ports.buildcraft.core;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.marker.volume.ClientVolumeBoxes;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.lib.marker.MarkerCache;
import ports.buildcraft.lib.marker.MarkerSubCache;

/**
 * Upstream {@code ItemMarkerConnector}: using it while looking along the line between two markers that
 * could connect connects them. The same click edits volume boxes: it grabs the corner of a box the
 * player looks at and resizes the box until the next click, and sneaking removes a box or cancels the
 * edit. Upstream's volume box addons (the filler planner) are not ported.
 */
public class MarkerConnectorItem extends Item {
    private static final double VOLUME_BOX_REACH = 4;

    public MarkerConnectorItem(Properties properties) {
        super(properties);
    }

    /**
     * Consumes the click when it connects markers or edits a volume box, which clients predict from the
     * markers they have loaded and the volume boxes they were sent. Otherwise the other hand gets its turn.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean acted = false;
        for (MarkerCache<?> cache : MarkerCache.CACHES) {
            if (interactCache(cache.getSubCache(level), player, level.isClientSide)) {
                if (!level.isClientSide) player.swing(hand, true);
                acted = true;
                break;
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            VolumeBoxes volumeBoxes = VolumeBoxes.get(serverLevel);
            if (interactVolumeBoxes(volumeBoxes.volumeBoxes, volumeBoxes, player)) acted = true;
        } else if (interactVolumeBoxes(ClientVolumeBoxes.VOLUME_BOXES, null, player)) {
            acted = true;
        }
        return acted ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide) : InteractionResultHolder.pass(stack);
    }

    private static boolean interactCache(MarkerSubCache<?> cache, Player player, boolean predictOnly) {
        LineInteraction best = null;
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (BlockPos marker : cache.getAllMarkers()) {
            for (BlockPos possible : cache.getValidConnections(marker)) {
                LineInteraction interaction = new LineInteraction(marker, possible, eyes, look);
                if (interaction.didInteract()) best = interaction.getBetter(best);
            }
        }
        if (best == null) return false;
        if (predictOnly) return true;
        return cache.tryConnect(best.marker1, best.marker2) || cache.tryConnect(best.marker2, best.marker1);
    }

    /**
     * Upstream {@code onItemRightClickVolumeBoxes}. Without {@code data} nothing changes and the result
     * only predicts whether the server will act.
     */
    private static boolean interactVolumeBoxes(List<VolumeBox> volumeBoxes, @Nullable VolumeBoxes data, Player player) {
        VolumeBox currentEditing = null;
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.isEditingBy(player)) {
                currentEditing = volumeBox;
                break;
            }
        }
        if (currentEditing != null) {
            if (data != null) {
                if (player.isShiftKeyDown()) currentEditing.cancelEditing();
                else currentEditing.confirmEditing();
                data.markDirty();
            }
            return true;
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(VOLUME_BOX_REACH));
        if (player.isShiftKeyDown()) {
            for (VolumeBox volumeBox : volumeBoxes) {
                if (!volumeBox.box.isInitialized() || intercept(volumeBox.box.boundingBox(), start, end) == null) continue;
                if (volumeBox.isLockedFor(Lock.Resize.class)) return false;
                if (data != null) {
                    volumeBoxes.remove(volumeBox);
                    data.markDirty();
                }
                return true;
            }
            return false;
        }
        VolumeBox bestVolumeBox = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos editing = null;
        for (VolumeBox volumeBox : volumeBoxes) {
            if (volumeBox.isLockedFor(Lock.Resize.class)) continue;
            for (BlockPos corner : volumeBox.box.corners()) {
                Vec3 hit = intercept(new AABB(corner), start, end);
                if (hit == null) continue;
                double dist = hit.distanceTo(start);
                if (bestDist > dist) {
                    bestDist = dist;
                    bestVolumeBox = volumeBox;
                    editing = corner;
                }
            }
        }
        if (bestVolumeBox == null) return false;
        if (data != null) {
            BlockPos min = bestVolumeBox.box.min();
            BlockPos max = bestVolumeBox.box.max();
            BlockPos held = new BlockPos(editing.getX() == min.getX() ? max.getX() : min.getX(),
                    editing.getY() == min.getY() ? max.getY() : min.getY(),
                    editing.getZ() == min.getZ() ? max.getZ() : min.getZ());
            bestVolumeBox.setPlayer(player);
            bestVolumeBox.setHeldDistOldMinOldMax(held, Math.max(1.5, bestDist + 0.5), min, max);
            data.markDirty();
        }
        return true;
    }

    /**
     * 1.12's {@code AxisAlignedBB.calculateIntercept}: the point nearest {@code start} where the segment
     * crosses a face of the box, entering or leaving it. Vanilla's {@code AABB.clip} only finds entries,
     * so it misses boxes the player stands inside.
     */
    @Nullable
    private static Vec3 intercept(AABB aabb, Vec3 start, Vec3 end) {
        Vec3 best = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double delta = end.get(axis) - start.get(axis);
            if (delta * delta < 1.0E-7) continue;
            for (double plane : new double[] {aabb.min(axis), aabb.max(axis)}) {
                double t = (plane - start.get(axis)) / delta;
                if (t < 0 || t > 1) continue;
                Vec3 point = start.add(end.subtract(start).scale(t));
                if (!withinFace(aabb, point, axis)) continue;
                if (best == null || start.distanceToSqr(point) < start.distanceToSqr(best)) best = point;
            }
        }
        return best;
    }

    private static boolean withinFace(AABB aabb, Vec3 point, Direction.Axis faceAxis) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == faceAxis) continue;
            double value = point.get(axis);
            if (value < aabb.min(axis) || value > aabb.max(axis)) return false;
        }
        return true;
    }

    /** Whether the player is looking along the line between the two markers' centres. */
    public static boolean doesInteract(BlockPos a, BlockPos b, Player player) {
        return new LineInteraction(a, b, player.getEyePosition(), player.getLookAngle()).didInteract();
    }

    /** Upstream {@code MarkerLineInteraction}. */
    private static final class LineInteraction {
        final BlockPos marker1, marker2;
        final double distToPoint, distToLine;

        LineInteraction(BlockPos marker1, BlockPos marker2, Vec3 playerPos, Vec3 playerLook) {
            this.marker1 = marker1;
            this.marker2 = marker2;
            Vec3 start = Vec3.atCenterOf(marker1);
            Vec3 end = Vec3.atCenterOf(marker2);
            // Upstream PositionUtil.findLineSkewPoint: a bisection for the point on the marker line closest to the look ray.
            double ia = 0, ib = 1, da = 0, db = 0, id = 0.5;
            Vec3 best = start;
            for (int i = 0; i < 10; i++) {
                Vec3 a = start.scale(1 - ia).add(end.scale(ia));
                Vec3 b = start.scale(1 - ib).add(end.scale(ib));
                da = a.distanceToSqr(closestPointOnLine(a, playerPos, playerLook));
                db = b.distanceToSqr(closestPointOnLine(b, playerPos, playerLook));
                if (da < db) {
                    best = a;
                    ib -= id;
                } else {
                    best = b;
                    ia += id;
                }
                id /= 2.0;
            }
            distToPoint = best.distanceTo(playerPos);
            distToLine = Math.sqrt(Math.min(da, db));
        }

        private static Vec3 closestPointOnLine(Vec3 point, Vec3 linePoint, Vec3 lineVector) {
            Vec3 v = lineVector.normalize();
            return linePoint.add(v.scale(point.subtract(linePoint).dot(v)));
        }

        boolean didInteract() {
            return distToPoint <= 3 && distToLine < 0.3;
        }

        LineInteraction getBetter(@Nullable LineInteraction other) {
            if (other == null) return this;
            if (other.marker1 == marker2 && other.marker2 == marker1) return other;
            if (other.distToLine < distToLine) return other;
            if (other.distToLine > distToLine) return this;
            if (other.distToPoint < distToPoint) return other;
            return this;
        }
    }
}
