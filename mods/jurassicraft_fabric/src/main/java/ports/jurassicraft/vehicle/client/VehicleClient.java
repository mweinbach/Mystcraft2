package ports.jurassicraft.vehicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleConfig;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;

/** Client-only registration, control sampling and presentation. */
public final class VehicleClient {
    private static final String CATEGORY = "key.categories.jurassicraft";
    // R is used for recipe viewing by common modpacks. Keep vehicle controls on the keypad.
    private static final KeyMapping RADIO = key("vehicle_radio", GLFW.GLFW_KEY_KP_0);
    private static final KeyMapping ROTATE_LEFT = key("helicopter_rotate_left", GLFW.GLFW_KEY_LEFT);
    private static final KeyMapping ROTATE_RIGHT = key("helicopter_rotate_right", GLFW.GLFW_KEY_RIGHT);
    private static final KeyMapping AUTOPILOT = key("helicopter_autopilot", GLFW.GLFW_KEY_HOME);
    private static final KeyMapping ROTATION_LOCK = key("helicopter_rotation_lock", GLFW.GLFW_KEY_END);
    private static final KeyMapping[] SEATS = new KeyMapping[9];
    private static int previousId = -1, previousState = -1, ticks;
    private VehicleClient() {}
    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.jurassicraft." + name, InputConstants.Type.KEYSYM, key, CATEGORY);
    }
    public static void init() {
        VehicleConfig.initClient();
        KeyBindingHelper.registerKeyBinding(RADIO);
        KeyBindingHelper.registerKeyBinding(ROTATE_LEFT); KeyBindingHelper.registerKeyBinding(ROTATE_RIGHT);
        KeyBindingHelper.registerKeyBinding(AUTOPILOT); KeyBindingHelper.registerKeyBinding(ROTATION_LOCK);
        // Dedicated defaults avoid overriding vanilla movement and hotbar keys.
        for (int i = 0; i < SEATS.length; i++) {
            SEATS[i] = KeyBindingHelper.registerKeyBinding(key("vehicle_seat_" + (i + 1), GLFW.GLFW_KEY_KP_1 + i));
        }
        VehicleCamera.init();
        VehicleClientNetwork.init();
        EntityRendererRegistry.register(VehicleRegistry.JEEP.get(), context -> new VehicleRenderer<>(context, "jeep_wrangler"));
        EntityRendererRegistry.register(VehicleRegistry.EXPLORER.get(), context -> new VehicleRenderer<>(context, "ford_explorer"));
        EntityRendererRegistry.register(VehicleRegistry.HELICOPTER.get(), context -> new VehicleRenderer<>(context, "helicopter"));
        HudRenderCallback.EVENT.register(HelicopterHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(VehicleClient::tick);
    }
    private static void tick(Minecraft client) {
        VehicleRadioClient.tick(client);
        VehicleVisuals.tick(client);
        VehicleCamera.tick(client);
        while (RADIO.consumeClick()) {
            if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                VehicleClientNetwork.sendRadioControl(vehicle);
        }
        for (int i = 0; i < SEATS.length; i++) {
            while (SEATS[i].consumeClick()) {
                if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                    VehicleClientNetwork.sendSeatControl(vehicle, i);
            }
        }
        while (AUTOPILOT.consumeClick()) {
            if (client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity helicopter)
                VehicleClientNetwork.sendFlightMode(helicopter, HelicopterEntity.TOGGLE_AUTOPILOT);
        }
        while (ROTATION_LOCK.consumeClick()) {
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
            if (client.options.keyUp.isDown()) state |= VehicleEntity.FORWARD;
            if (client.options.keyDown.isDown()) state |= VehicleEntity.BACK;
            if (client.options.keyLeft.isDown()) state |= VehicleEntity.LEFT;
            if (client.options.keyRight.isDown()) state |= VehicleEntity.RIGHT;
            if (client.options.keyJump.isDown()) state |= VehicleEntity.UP;
            if (client.options.keySprint.isDown()) state |= VehicleEntity.DOWN;
            if (ROTATE_LEFT.isDown()) state |= VehicleEntity.ROTATE_LEFT;
            if (ROTATE_RIGHT.isDown()) state |= VehicleEntity.ROTATE_RIGHT;
        }
        if (vehicle.getId() != previousId || state != previousState || ++ticks >= 10) {
            VehicleClientNetwork.sendControls(vehicle, state);
            previousId = vehicle.getId(); previousState = state; ticks = 0;
        }
    }
}
