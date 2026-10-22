package ports.jurassicraft.vehicle.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.platform.client.ClientPlatform.Key;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;

/** Client-only registration, control sampling and presentation. */
public final class VehicleClient {
    private static final BooleanSupplier IN_VEHICLE = () -> riding(VehicleEntity.class);
    private static final BooleanSupplier IN_HELICOPTER = () -> riding(HelicopterEntity.class);
    private static KeyMapping radio, rotateLeft, rotateRight, ascend, descend, autopilot, rotationLock;
    private static final KeyMapping[] SEATS = new KeyMapping[9];
    private static int previousId = -1, previousState = -1, ticks;
    private VehicleClient() {}

    private static boolean riding(Class<? extends VehicleEntity> type) {
        Minecraft client = Minecraft.getInstance();
        return client.screen == null && client.player != null && type.isInstance(client.player.getVehicle());
    }
    public static void keys() {
        radio = ClientPlatform.keyMapping(Key.of("key.jurassicraft.vehicle_radio", GLFW.GLFW_KEY_R));
        rotateLeft = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_rotate_left", GLFW.GLFW_KEY_LEFT).when(IN_VEHICLE));
        rotateRight = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_rotate_right", GLFW.GLFW_KEY_RIGHT).when(IN_VEHICLE));
        ascend = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_ascend", GLFW.GLFW_KEY_SPACE).when(IN_HELICOPTER));
        descend = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_descend", GLFW.GLFW_KEY_LEFT_CONTROL).when(IN_HELICOPTER));
        autopilot = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_autopilot", GLFW.GLFW_KEY_A)
                .control(GLFW.GLFW_KEY_HOME).when(IN_HELICOPTER));
        rotationLock = ClientPlatform.keyMapping(Key.of("key.jurassicraft.helicopter_rotation_lock", GLFW.GLFW_KEY_D)
                .control(GLFW.GLFW_KEY_END).when(IN_HELICOPTER));
        for (int i = 0; i < SEATS.length; i++) {
            SEATS[i] = ClientPlatform.keyMapping(Key.of("key.jurassicraft.vehicle_seat_" + (i + 1), GLFW.GLFW_KEY_1 + i)
                    .control(GLFW.GLFW_KEY_KP_1 + i).when(IN_VEHICLE));
        }
        VehicleCamera.keys();
    }
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.entity(VehicleRegistry.JEEP.get(), context -> new VehicleRenderer<>(context, "jeep_wrangler"));
        renderers.entity(VehicleRegistry.EXPLORER.get(), context -> new VehicleRenderer<>(context, "ford_explorer"));
        renderers.entity(VehicleRegistry.HELICOPTER.get(), context -> new VehicleRenderer<>(context, "helicopter"));
    }
    public static void setup() { VehicleClientNetwork.register(); }
    /**
     * Space and Left Ctrl are also jump and sprint. While they are held to fly, clear the vanilla
     * mapping before the player reads input so the same press does not jump or sprint.
     */
    public static void beforeTick(Minecraft client) {
        if (ascend == null || client.player == null || client.screen != null
                || !(client.player.getVehicle() instanceof HelicopterEntity helicopter)
                || helicopter.getControllingPassenger() != client.player) return;
        releaseVanilla(ascend, client.options.keyJump);
        releaseVanilla(descend, client.options.keySprint);
    }
    private static void releaseVanilla(KeyMapping flight, KeyMapping vanilla) {
        if (!flight.isDown() || !flight.same(vanilla)) return;
        for (int i = 0; i < 16 && vanilla.consumeClick(); i++) {}
        vanilla.setDown(false);
    }
    public static void tick(Minecraft client) {
        VehicleRadioClient.tick(client);
        VehicleVisuals.tick(client);
        VehicleCamera.tick(client);
        if (radio == null) return;
        while (radio.consumeClick()) {
            if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                VehicleClientNetwork.sendRadioControl(vehicle);
        }
        for (int i = 0; i < SEATS.length; i++) {
            while (SEATS[i].consumeClick()) {
                if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                    VehicleClientNetwork.sendSeatControl(vehicle, i);
            }
        }
        while (autopilot.consumeClick()) {
            if (client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity helicopter)
                VehicleClientNetwork.sendFlightMode(helicopter, HelicopterEntity.TOGGLE_AUTOPILOT);
        }
        while (rotationLock.consumeClick()) {
            if (client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity helicopter)
                VehicleClientNetwork.sendFlightMode(helicopter, HelicopterEntity.TOGGLE_ROTATION_LOCK);
        }
        if (client.player == null || !(client.player.getVehicle() instanceof VehicleEntity vehicle)
                || vehicle.getControllingPassenger() != client.player) {
            previousId = -1; previousState = -1; ticks = 0;
            return;
        }
        int state = 0;
        if (client.screen == null) {
            // Ctrl+A and Ctrl+D share the strafe keys; a held mode chord must not also steer.
            boolean chord = autopilot.isDown() || rotationLock.isDown();
            if (client.options.keyUp.isDown()) state |= VehicleEntity.FORWARD;
            if (client.options.keyDown.isDown()) state |= VehicleEntity.BACK;
            if (client.options.keyLeft.isDown() && !chord) state |= VehicleEntity.LEFT;
            if (client.options.keyRight.isDown() && !chord) state |= VehicleEntity.RIGHT;
            if (vehicle instanceof HelicopterEntity) {
                if (ascend.isDown()) state |= VehicleEntity.UP;
                if (descend.isDown()) state |= VehicleEntity.DOWN;
            }
            if (rotateLeft.isDown()) state |= VehicleEntity.ROTATE_LEFT;
            if (rotateRight.isDown()) state |= VehicleEntity.ROTATE_RIGHT;
        }
        if (vehicle.getId() != previousId || state != previousState || ++ticks >= 10) {
            VehicleClientNetwork.sendControls(vehicle, state);
            previousId = vehicle.getId(); previousState = state; ticks = 0;
        }
    }
}
