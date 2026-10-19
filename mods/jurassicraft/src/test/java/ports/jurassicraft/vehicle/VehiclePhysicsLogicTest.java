package ports.jurassicraft.vehicle;

/** Executable without launching Minecraft or accepting the EULA. */
public final class VehiclePhysicsLogicTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        double speed = 0;
        for (int i = 0; i < 200; i++) speed = VehiclePhysics.speed(speed, VehiclePhysics.FORWARD, true, false);
        check(speed > .3 && speed <= .48, "forward acceleration and speed limit");
        check(VehiclePhysics.speed(speed, 0, true, false) < speed, "coasting should slow down");
        check(VehiclePhysics.speed(0, VehiclePhysics.BACK, true, false) < 0, "reverse should move backwards");
        check(VehiclePhysics.speed(0, VehiclePhysics.FORWARD | VehiclePhysics.BACK, true, false) == 0, "opposite keys cancel");
        check(VehiclePhysics.steering(.2, VehiclePhysics.LEFT, false) < 0, "left should turn left");
        check(VehiclePhysics.steering(-.2, VehiclePhysics.LEFT, false) > 0, "steering should reverse while backing up");
        check(VehiclePhysics.steering(0, VehiclePhysics.RIGHT, false) == 0, "cars should not rotate while stopped");
        check(VehiclePhysics.helicopterLateral(0, VehiclePhysics.LEFT) < 0, "helicopter left should move sideways");
        check(VehiclePhysics.helicopterLateral(0, VehiclePhysics.LEFT | VehiclePhysics.RIGHT) == 0, "opposite lateral inputs cancel");
        float yaw = VehiclePhysics.helicopterYaw(0, VehiclePhysics.ROTATE_RIGHT);
        check(yaw > 0 && VehiclePhysics.helicopterYaw(yaw, 0) < yaw, "rotation should accelerate and settle");
        check(VehiclePhysics.helicopterYaw(0, VehiclePhysics.ROTATE_LEFT | VehiclePhysics.ROTATE_RIGHT) == 0,
                "opposite rotation inputs cancel");
        check(VehiclePhysics.vertical(0, VehiclePhysics.UP, true, false) > 0, "helicopter should rise");
        check(VehiclePhysics.vertical(0, VehiclePhysics.DOWN, true, false) < 0, "helicopter should descend");
        check(VehiclePhysics.vertical(0, VehiclePhysics.UP | VehiclePhysics.DOWN, true, false) == 0, "opposite lift keys cancel");
        check(VehiclePhysics.vertical(0, 0, false, false) < 0, "unoccupied aircraft should fall");
        System.out.println("Vehicle physics checks passed");
    }
}
