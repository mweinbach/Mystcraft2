package ports.jurassicraft.vehicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleConfig;
import ports.jurassicraft.vehicle.VehicleZoom;

/** Optional, client-local zoom that never changes the player's camera after dismounting. */
final class VehicleCamera {
    private static final KeyMapping ZOOM_IN = new KeyMapping("key.jurassicraft.helicopter_zoom_in", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.jurassicraft");
    private static final KeyMapping ZOOM_OUT = new KeyMapping("key.jurassicraft.helicopter_zoom_out", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "key.categories.jurassicraft");
    private static float distance = VehicleZoom.DEFAULT;
    private VehicleCamera() {}
    static void register(RegisterKeyMappingsEvent event) { event.register(ZOOM_IN); event.register(ZOOM_OUT); }
    static void tick(Minecraft client) {
        if (client.player == null || !(client.player.getVehicle() instanceof HelicopterEntity) || !VehicleConfig.HELICOPTER_ZOOM.get()) {
            distance = VehicleZoom.DEFAULT;
            return;
        }
        if (client.screen == null) distance = VehicleZoom.step(distance, ZOOM_IN.isDown(), ZOOM_OUT.isDown());
    }
    static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        if (distance <= VehicleZoom.DEFAULT || client.player == null || client.level == null
                || camera.getEntity() != client.player || !camera.isDetached()
                || !(client.player.getVehicle() instanceof HelicopterEntity)) return;
        Vec3 origin = client.player.getEyePosition((float)event.getPartialTick());
        var forward = camera.getLookVector();
        Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
        double clear = VehicleZoom.distance(client.level, client.player, origin, look, distance);
        Vec3 position = origin.subtract(look.scale(clear));
        camera.setPosition(position.x, position.y, position.z);
    }
}
