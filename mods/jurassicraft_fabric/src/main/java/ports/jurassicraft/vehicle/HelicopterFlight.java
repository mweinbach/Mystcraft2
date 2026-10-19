package ports.jurassicraft.vehicle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The transport helicopter's upstream rotor and air-resistance calculations. */
final class HelicopterFlight {
    static final float MAX_RPM = 300F;
    static final float THROTTLE = 3992F * .00029481132F;
    private static final double WEIGHT = 6838, POWER = 3992 * 735.5, ROTOR_AREA = Math.PI * 25;
    private static final double TORQUE = POWER / (MAX_RPM * Math.PI / 30), QUALITY = .75;
    private HelicopterFlight() {}

    static float hoverRpm(float pitch, float roll) {
        double verticalFactor = (1 - Math.abs(pitch) / 90D) * (1 - Math.abs(roll) / 90D);
        if (verticalFactor <= 0) return Float.POSITIVE_INFINITY;
        double force = 9.81 * WEIGHT / verticalFactor;
        return (float)(Math.sqrt(force * force * force / (2 * 1.2 * ROTOR_AREA)) / (QUALITY * TORQUE * Math.PI / 30));
    }
    static double rotorForce(float rpm) {
        double shaftPower = TORQUE * rpm * Math.PI / 30;
        return Math.cbrt(2 * 1.2 * ROTOR_AREA * Math.pow(QUALITY * shaftPower, 2));
    }
    static float maximumTilt(double distance, boolean lock) {
        return (float)(distance <= 3 ? Math.max(0, distance) / 3 * (lock ? 15 : 90) : lock ? 15 : 180);
    }
    static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    static Vec3 motion(Vec3 old, float yaw, float rpm, float pitch, float roll, boolean water, boolean pilot) {
        double force = water ? 0 : rotorForce(rpm);
        double verticalFactor = (1 - Math.abs(pitch) / 90D) * (1 - Math.abs(roll) / 90D);
        double pitchForce;
        if (Math.abs(pitch) <= 90) pitchForce = pitch / 90D * (1 - Math.abs(roll) / 90D) * verticalFactor * 5 * force;
        else {
            double beyond = Math.abs(pitch) - 90;
            pitchForce = Math.copySign((1 - beyond / 90) * (1 - Math.abs(roll) / 90D) * beyond / 90
                    * (1 - Math.abs(roll) / 90D) * 5 * force, pitch);
        }
        double rollForce = roll / 90D * (1 - pitch / 90D) * force;
        double angle = Math.toRadians(yaw);
        double x = old.x * .8 + (-Math.sin(angle) * pitchForce - Math.cos(angle) * rollForce) / WEIGHT / 20;
        double z = old.z * .8 + (Math.cos(angle) * pitchForce - Math.sin(angle) * rollForce) / WEIGHT / 20;
        double horizontal = Math.hypot(old.x, old.z);
        if (horizontal > 1.0E-7) {
            double resistance = 2 * 2 * 3.5 * .5 * 1.2 * Math.pow(horizontal * 20, 2) / WEIGHT / 20;
            // Drag opposes travel. The upstream formula subtracts the same unsigned
            // resistance from both axes, which causes unpowered sideways drift.
            double limited = Math.min(resistance, Math.hypot(x, z));
            x -= old.x / horizontal * limited;
            z -= old.z / horizontal * limited;
        }
        double verticalResistance = 2 * 2 * 5 * .5 * 1.2 * Math.pow(old.y * 20, 2) / WEIGHT / 20;
        double y = old.y * .8 + (verticalFactor * force / WEIGHT - 9.81) / 20;
        if (old.y != 0) y -= Math.copySign(Math.min(verticalResistance, Math.abs(old.y * .8)), old.y);
        if (!pilot || water) y -= .15; // upstream adds ordinary gravity when not actively flown
        return new Vec3(Mth.clamp(x, -2, 2), Mth.clamp(y, -2, 2), Mth.clamp(z, -2, 2));
    }
}
