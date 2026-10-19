package ports.jurassicraft.vehicle;

/** Pure movement calculations shared by each vehicle. */
public final class VehiclePhysics {
    public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, UP = 16, DOWN = 32,
            ROTATE_LEFT = 64, ROTATE_RIGHT = 128;
    private VehiclePhysics() {}
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(value, max)); }
    public static double speed(double oldSpeed, int controls, boolean grounded, boolean flying) {
        int throttle = ((controls & FORWARD) != 0 ? 1 : 0) - ((controls & BACK) != 0 ? 1 : 0);
        double acceleration = throttle * .045;
        if (!flying && (controls & (FORWARD | BACK)) == 0
                && ((controls & LEFT) != 0) != ((controls & RIGHT) != 0)) {
            acceleration = .0225; // Steering alone creeps forward at half throttle, as upstream.
        }
        return clamp(oldSpeed * (grounded || flying ? .88 : .96) + acceleration, -.25, .48);
    }
    public static float steering(double speed, int controls, boolean flying) {
        int steer = ((controls & RIGHT) != 0 ? 1 : 0) - ((controls & LEFT) != 0 ? 1 : 0);
        if (steer == 0 || (!flying && Math.abs(speed) <= .015)) return 0;
        float direction = speed < -.015 ? -1 : 1;
        return steer * direction * (float)Math.min(4, .8 + Math.abs(speed) * 9);
    }
    /** Aircraft movement keys bank sideways; separate pedals turn the nose. */
    public static double helicopterLateral(double oldSpeed, int controls) {
        int strafe = ((controls & RIGHT) != 0 ? 1 : 0) - ((controls & LEFT) != 0 ? 1 : 0);
        return clamp(oldSpeed * .88 + strafe * .035, -.35, .35);
    }
    public static float helicopterYaw(float oldRate, int controls) {
        int turn = ((controls & ROTATE_RIGHT) != 0 ? 1 : 0) - ((controls & ROTATE_LEFT) != 0 ? 1 : 0);
        if (turn != 0) return (float)clamp(oldRate + turn * .1F, -2F, 2F);
        return Math.abs(oldRate) <= .05F ? 0F : oldRate - Math.copySign(.05F, oldRate);
    }
    public static double vertical(double oldVertical, int controls, boolean flyingWithDriver, boolean noGravity) {
        if (flyingWithDriver) {
            int lift = ((controls & UP) != 0 ? 1 : 0) - ((controls & DOWN) != 0 ? 1 : 0);
            return clamp(oldVertical * .75 + lift * .035, -.35, .35);
        }
        return noGravity ? oldVertical : Math.max(-.8, oldVertical - .08);
    }
}
