package ports.buildcraft.core;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.marker.MarkerCache;
import ports.buildcraft.lib.marker.MarkerSubCache;

/**
 * Upstream {@code ItemMarkerConnector}: using it while looking along the line between two markers that
 * could connect connects them. Upstream also used it to edit volume boxes, which are not ported yet.
 */
public class MarkerConnectorItem extends Item {
    public MarkerConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            for (MarkerCache<?> cache : MarkerCache.CACHES) {
                if (interactCache(cache.getSubCache(level), player)) {
                    player.swing(hand, true);
                    break;
                }
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private static boolean interactCache(MarkerSubCache<?> cache, Player player) {
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
        return cache.tryConnect(best.marker1, best.marker2) || cache.tryConnect(best.marker2, best.marker1);
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
