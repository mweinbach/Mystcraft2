package ports.jurassicraft.vehicle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.vehicle.HelicopterEntity;

/** The original cockpit gauges, driven by server-synchronized flight state. */
final class HelicopterHud {
    private static final int WHITE = 0xffffff;
    private HelicopterHud() {}
    static void render(ForgeGui gui, PoseStack pose, float partialTick, int width, int height) {
        Minecraft mc = gui.getMinecraft();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof HelicopterEntity helicopter)
                || helicopter.getControllingPassenger() != mc.player || !helicopter.isAlive()) return;
        float scale = mc.options.getCameraType().isFirstPerson() ? 1 : .5F;
        int originX = 25, originY = Math.round(height - 36 - 64 * scale);
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);
        gui.setupOverlayRenderState(true, false);

        gauge(pose, 0, "tachometer", 33 + helicopter.engineRpm() / 300F * 294,
                33 + Mth.clamp(helicopter.hoverRpm() / 300F, 0, 1) * 294);
        number(mc, pose, 0, helicopter.engineRpm());

        int horizonX = Math.round(originX + 75 * scale);
        GuiComponent.enableScissor(horizonX, originY, Math.round(horizonX + 64 * scale), Math.round(originY + 64 * scale));
        pose.pushPose();
        pose.translate(75 + 32, 32, 0);
        pose.mulPose(Vector3f.ZP.rotationDegrees(helicopter.flightRoll()));
        float horizon = Mth.clamp(helicopter.flightPitch(), -90, 90) * 58 / 90;
        GuiComponent.fill(pose, -100, -100, 100, (int)horizon, 0xff49b3ff);
        GuiComponent.fill(pose, -100, (int)horizon, 100, 100, 0xff6b2a00);
        GuiComponent.fill(pose, -100, (int)horizon, 100, (int)horizon + 1, 0xffffffff);
        pose.popPose();
        GuiComponent.disableScissor();
        texture(pose, 75, "artificial_horizon_second_layer");
        rotated(pose, 75, "artificial_horizon_needle", helicopter.flightRoll());
        texture(pose, 75, "artificial_horizon_pointer");

        float altitude = (float)helicopter.getY();
        gauge(pose, 2, "altimeter", (altitude - 63) / (250 - 63) * 360,
                (float)helicopter.groundDistance() / 250 * 360);
        number(mc, pose, 2, altitude);
        texture(pose, 225, "compass");
        rotated(pose, 225, "compass_needle", helicopter.getYRot() - 180);
        pose.popPose();

        if (!helicopter.autopilot()) mc.font.drawShadow(pose, Component.translatable("hud.jurassicraft.autopilot_disabled"), originX, height - 140, WHITE);
        mc.font.drawShadow(pose, Component.translatable(helicopter.rotationLock()
                ? "hud.jurassicraft.rotation_lock_on" : "hud.jurassicraft.rotation_lock_off"), originX, height - 130, WHITE);
    }
    private static void number(Minecraft mc, PoseStack pose, int index, float value) {
        pose.pushPose();
        pose.translate(index * 75 + 15, 17, 0);
        pose.scale(.7F, .7F, 1);
        mc.font.draw(pose, String.format(java.util.Locale.ROOT, "%05d", (int)value), 0, 0, WHITE);
        pose.popPose();
    }
    private static void gauge(PoseStack pose, int index, String name, float needle, float pointer) {
        int x = index * 75;
        texture(pose, x, name);
        rotated(pose, x, name + "_needle", needle);
        rotated(pose, x, name + "_pointer", pointer);
    }
    private static void rotated(PoseStack pose, int x, String name, float angle) {
        pose.pushPose();
        pose.translate(x + 32, 32, 0);
        pose.mulPose(Vector3f.ZP.rotationDegrees(angle));
        pose.translate(-x - 32, -32, 0);
        texture(pose, x, name);
        pose.popPose();
    }
    private static void texture(PoseStack pose, int x, String name) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, new ResourceLocation(PortMod.MOD_ID, "textures/entities/helicopter/hud/" + name + ".png"));
        RenderSystem.setShaderColor(1, 1, 1, 1);
        GuiComponent.blit(pose, x, 0, 64, 64, 0, 0, 128, 128, 128, 128);
    }
}
