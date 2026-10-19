package ports.jurassicraft.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Third-person zoom limits and vanilla-style collision clipping. */
public final class VehicleZoom {
    public static final float DEFAULT = 4F, MAXIMUM = 20F;
    private VehicleZoom() {}
    public static float step(float previous, boolean zoomIn, boolean zoomOut) {
        int change = (zoomOut ? 1 : 0) - (zoomIn ? 1 : 0);
        return Math.max(DEFAULT, Math.min(MAXIMUM, previous + change));
    }
    public static double distance(BlockGetter level, Entity viewer, Vec3 origin, Vec3 look, double requested) {
        double result = Math.max(0, requested);
        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) == 0 ? -.1 : .1), y = ((i & 2) == 0 ? -.1 : .1), z = ((i & 4) == 0 ? -.1 : .1);
            Vec3 from = origin.add(x, y, z);
            Vec3 to = origin.subtract(look.scale(result)).add(x + z, y, z);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
            if (hit.getType() != HitResult.Type.MISS) result = Math.min(result, hit.getLocation().distanceTo(origin));
        }
        return result;
    }
}
