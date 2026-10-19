package ports.jurassicraft.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Ground contact beneath the four original wheel locations, used for visual suspension. */
public final class VehicleSuspension {
    private VehicleSuspension() {}
    public record Tilt(float pitch, float roll) {}

    public static Tilt sample(VehicleEntity vehicle) {
        if (vehicle instanceof HelicopterEntity) return new Tilt(0, 0);
        boolean explorer = vehicle instanceof ExplorerEntity;
        double halfWidth = explorer ? 1 : 1.2;
        double front = explorer ? 2.2 : 2.65;
        double back = explorer ? 2 : 1.5;
        double theta = Math.toRadians(vehicle.getYRot());
        double rightX = Math.cos(theta), rightZ = Math.sin(theta);
        double forwardX = -Math.sin(theta), forwardZ = Math.cos(theta);
        Double frontLeft = height(vehicle, -halfWidth, front, rightX, rightZ, forwardX, forwardZ);
        Double frontRight = height(vehicle, halfWidth, front, rightX, rightZ, forwardX, forwardZ);
        Double backLeft = height(vehicle, -halfWidth, -back, rightX, rightZ, forwardX, forwardZ);
        Double backRight = height(vehicle, halfWidth, -back, rightX, rightZ, forwardX, forwardZ);
        if (frontLeft == null || frontRight == null || backLeft == null || backRight == null) return new Tilt(0, 0);
        float pitch = angle((frontLeft + frontRight - backLeft - backRight) / 2, front + back);
        float roll = explorer && ((ExplorerEntity)vehicle).isOnTourRail() ? 0
                : angle((frontRight + backRight - frontLeft - backLeft) / 2, halfWidth * 2);
        return new Tilt(pitch, roll);
    }
    private static float angle(double rise, double run) {
        return (float)Math.max(-30, Math.min(30, Math.toDegrees(Math.atan2(rise, run))));
    }
    private static Double height(VehicleEntity vehicle, double side, double forward,
                                 double rightX, double rightZ, double forwardX, double forwardZ) {
        Level level = vehicle.level;
        double x = vehicle.getX() + side * rightX + forward * forwardX;
        double z = vehicle.getZ() + side * rightZ + forward * forwardZ;
        if (!level.hasChunkAt(new BlockPos(x, vehicle.getY(), z))) return null;
        Vec3 top = new Vec3(x, vehicle.getY() + 1.25, z);
        Vec3 bottom = new Vec3(x, vehicle.getY() - 3, z);
        BlockHitResult hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, vehicle));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : null;
    }
}
