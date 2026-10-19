package ports.jurassicraft.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.EntityRenderers;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleNetwork;
import ports.jurassicraft.vehicle.VehicleRegistry;
import ports.jurassicraft.vehicle.HelicopterEntity;

public final class VehicleClient {
    private static final KeyMapping RADIO = new KeyMapping("key.jurassicraft.vehicle_radio", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.jurassicraft");
    private static final IKeyConflictContext IN_VEHICLE = new IKeyConflictContext() {
        @Override public boolean isActive() {
            Minecraft client = Minecraft.getInstance();
            return client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity;
        }
        @Override public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };
    private static final IKeyConflictContext IN_HELICOPTER = new IKeyConflictContext() {
        @Override public boolean isActive() {
            Minecraft client = Minecraft.getInstance();
            return client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity;
        }
        @Override public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == IN_VEHICLE || other == KeyConflictContext.IN_GAME;
        }
    };
    private static final KeyMapping ROTATE_LEFT = new KeyMapping("key.jurassicraft.helicopter_rotate_left", IN_VEHICLE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.jurassicraft");
    private static final KeyMapping ROTATE_RIGHT = new KeyMapping("key.jurassicraft.helicopter_rotate_right", IN_VEHICLE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.jurassicraft");
    private static final KeyMapping AUTOPILOT = new KeyMapping("key.jurassicraft.helicopter_autopilot", IN_HELICOPTER,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A, "key.categories.jurassicraft");
    private static final KeyMapping ROTATION_LOCK = new KeyMapping("key.jurassicraft.helicopter_rotation_lock", IN_HELICOPTER,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D, "key.categories.jurassicraft");
    private static final KeyMapping[] SEATS = createSeatBindings();
    private static KeyMapping[] createSeatBindings() {
        KeyMapping[] bindings = new KeyMapping[9];
        for (int i = 0; i < bindings.length; i++) {
            bindings[i] = new KeyMapping("key.jurassicraft.vehicle_seat_" + (i + 1), IN_VEHICLE,
                    KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_1 + i, "key.categories.jurassicraft");
        }
        return bindings;
    }
    private VehicleClient() {}
    @Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(RADIO);
            event.register(ROTATE_LEFT); event.register(ROTATE_RIGHT);
            event.register(AUTOPILOT); event.register(ROTATION_LOCK);
            VehicleCamera.register(event);
            for (KeyMapping binding : SEATS) event.register(binding);
        }
        @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("helicopter_hud", HelicopterHud::render);
        }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                EntityRenderers.register(VehicleRegistry.JEEP.get(), context -> new VehicleRenderer<>(context, "jeep_wrangler"));
                EntityRenderers.register(VehicleRegistry.EXPLORER.get(), context -> new VehicleRenderer<>(context, "ford_explorer"));
                EntityRenderers.register(VehicleRegistry.HELICOPTER.get(), context -> new VehicleRenderer<>(context, "helicopter"));
            });
        }
    }
    @Mod.EventBusSubscriber(modid = PortMod.MOD_ID, value = Dist.CLIENT)
    public static final class Controls {
        private static int previousId = -1, previousState = -1, ticks;
        @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft client = Minecraft.getInstance();
            VehicleVisuals.tick(client);
            VehicleCamera.tick(client);
            while (RADIO.consumeClick()) {
                if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                    VehicleNetwork.sendRadioControl(vehicle);
            }
            for (int i = 0; i < SEATS.length; i++) {
                while (SEATS[i].consumeClick()) {
                    if (client.screen == null && client.player != null && client.player.getVehicle() instanceof VehicleEntity vehicle)
                        VehicleNetwork.sendSeatControl(vehicle, i);
                }
            }
            while (AUTOPILOT.consumeClick()) {
                if (client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity helicopter)
                    VehicleNetwork.sendFlightMode(helicopter, HelicopterEntity.TOGGLE_AUTOPILOT);
            }
            while (ROTATION_LOCK.consumeClick()) {
                if (client.screen == null && client.player != null && client.player.getVehicle() instanceof HelicopterEntity helicopter)
                    VehicleNetwork.sendFlightMode(helicopter, HelicopterEntity.TOGGLE_ROTATION_LOCK);
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
                if (client.options.keyLeft.isDown() && !AUTOPILOT.isDown() && !ROTATION_LOCK.isDown()) state |= VehicleEntity.LEFT;
                if (client.options.keyRight.isDown() && !AUTOPILOT.isDown() && !ROTATION_LOCK.isDown()) state |= VehicleEntity.RIGHT;
                if (client.options.keyJump.isDown()) state |= VehicleEntity.UP;
                if (client.options.keySprint.isDown() && !AUTOPILOT.isDown() && !ROTATION_LOCK.isDown()) state |= VehicleEntity.DOWN;
                if (ROTATE_LEFT.isDown()) state |= VehicleEntity.ROTATE_LEFT;
                if (ROTATE_RIGHT.isDown()) state |= VehicleEntity.ROTATE_RIGHT;
            }
            if (vehicle.getId() != previousId || state != previousState || ++ticks >= 10) {
                VehicleNetwork.sendControls(vehicle, state);
                previousId = vehicle.getId(); previousState = state; ticks = 0;
            }
        }
        @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) { VehicleCamera.camera(event); }
    }
}
