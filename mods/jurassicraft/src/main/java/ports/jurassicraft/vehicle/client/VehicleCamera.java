package ports.jurassicraft.vehicle.client;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleConfig;
import ports.jurassicraft.vehicle.VehicleZoom;

/** Optional, client-local zoom that never changes the player's camera after dismounting. */
public final class VehicleCamera {
    private static KeyMapping zoomIn, zoomOut;
    private static float distance = VehicleZoom.DEFAULT;
    private VehicleCamera() {}
    static void keys() {
        zoomIn = ClientPlatform.keyMapping(ClientPlatform.Key.of("key.jurassicraft.helicopter_zoom_in", GLFW.GLFW_KEY_PAGE_DOWN));
        zoomOut = ClientPlatform.keyMapping(ClientPlatform.Key.of("key.jurassicraft.helicopter_zoom_out", GLFW.GLFW_KEY_PAGE_UP));
    }
    static void tick(Minecraft client) {
        if (client.player == null || !(client.player.getVehicle() instanceof HelicopterEntity) || !VehicleConfig.HELICOPTER_ZOOM.get()) {
            distance = VehicleZoom.DEFAULT;
            return;
        }
        if (client.screen == null && zoomIn != null) distance = VehicleZoom.step(distance, zoomIn.isDown(), zoomOut.isDown());
    }
    /** Called by the loader after vanilla positions the third-person camera. */
    public static void apply(Camera camera, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (distance <= VehicleZoom.DEFAULT || client.player == null || client.level == null
                || camera.getEntity() != client.player || !camera.isDetached()
                || !(client.player.getVehicle() instanceof HelicopterEntity)) return;
        Vec3 origin = client.player.getEyePosition(partialTick);
        var forward = camera.getLookVector();
        Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
        double clear = VehicleZoom.distance(client.level, client.player, origin, look, distance);
        Vec3 position = origin.subtract(look.scale(clear));
        camera.setPosition(position.x, position.y, position.z);
    }
}
