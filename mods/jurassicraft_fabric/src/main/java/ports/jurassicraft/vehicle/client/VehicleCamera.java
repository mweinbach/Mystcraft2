package ports.jurassicraft.vehicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleConfig;
import ports.jurassicraft.vehicle.VehicleZoom;

/** Optional client-local zoom, reset on dismount. */
public final class VehicleCamera {
    private static final KeyMapping ZOOM_IN = new KeyMapping("key.jurassicraft.helicopter_zoom_in", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.jurassicraft");
    private static final KeyMapping ZOOM_OUT = new KeyMapping("key.jurassicraft.helicopter_zoom_out", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP, "key.categories.jurassicraft");
    private static float distance = VehicleZoom.DEFAULT;
    private VehicleCamera() {}
    static void init() { KeyBindingHelper.registerKeyBinding(ZOOM_IN); KeyBindingHelper.registerKeyBinding(ZOOM_OUT); }
    static void tick(Minecraft client) {
        if (client.player == null || !(client.player.getVehicle() instanceof HelicopterEntity) || !VehicleConfig.HELICOPTER_ZOOM.get()) {
            distance = VehicleZoom.DEFAULT;
            return;
        }
        if (client.screen == null) distance = VehicleZoom.step(distance, ZOOM_IN.isDown(), ZOOM_OUT.isDown());
    }
    public static void apply(Camera camera, float partialTick, Consumer<Vec3> setPosition) {
        Minecraft client = Minecraft.getInstance();
        if (distance <= VehicleZoom.DEFAULT || client.player == null || client.level == null
                || camera.getEntity() != client.player || !camera.isDetached()
                || !(client.player.getVehicle() instanceof HelicopterEntity)) return;
        Vec3 origin = client.player.getEyePosition(partialTick);
        var forward = camera.getLookVector();
        Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
        double clear = VehicleZoom.distance(client.level, client.player, origin, look, distance);
        setPosition.accept(origin.subtract(look.scale(clear)));
    }
}
