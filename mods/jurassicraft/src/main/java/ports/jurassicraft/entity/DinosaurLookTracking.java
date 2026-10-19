package ports.jurassicraft.entity;

import net.minecraft.util.Mth;

/** Rotation increments used by the original faceTarget neck chains. */
public final class DinosaurLookTracking {
    private DinosaurLookTracking() {}
    public static float yaw(float degrees, float divisor, int parts) {
        return parts == 0 ? 0 : (float) Math.toRadians(Mth.clamp(Mth.wrapDegrees(degrees), -45, 45)) / (divisor * parts);
    }
    public static float pitch(float degrees, float divisor, int parts) {
        return parts == 0 ? 0 : (float) Math.toRadians(Mth.wrapDegrees(degrees)) / (divisor * parts);
    }
}
