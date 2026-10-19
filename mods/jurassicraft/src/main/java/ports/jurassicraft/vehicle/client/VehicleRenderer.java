package ports.jurassicraft.vehicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.vehicle.VehicleEntity;

public final class VehicleRenderer<T extends VehicleEntity> extends EntityRenderer<T> {
    private final VehicleModel model;
    private final ResourceLocation texture;
    public VehicleRenderer(EntityRendererProvider.Context context, String name) {
        super(context);
        model = new VehicleModel(name);
        texture = new ResourceLocation(PortMod.MOD_ID, "textures/entities/" + name + "/" + name + ".png");
        shadowRadius = 1.25F;
    }
    @Override public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        model.resetAnimations();
        VehicleVisuals.State animation = VehicleVisuals.state(entity);
        if (entity instanceof ports.jurassicraft.vehicle.HelicopterEntity) {
            model.rotateY("rotorbase_rotatehere", animation.rotor(partialTick));
            model.rotateX("tailrotor_rotatehere", animation.rotor(partialTick));
            float gear = animation.gear(partialTick);
            model.offsetY("gearL1", gear); model.offsetY("gearR1", gear); model.offsetY("gearFront1", gear);
            ports.jurassicraft.vehicle.HelicopterEntity helicopter = (ports.jurassicraft.vehicle.HelicopterEntity)entity;
            model.rotateX("controlstick1", (float)Math.toRadians(-15 + net.minecraft.util.Mth.clamp(helicopter.flightPitch(), -25, 25) * .6F));
            model.rotateZ("controlstick1", (float)Math.toRadians(net.minecraft.util.Mth.clamp(helicopter.flightRoll(), -20, 20) * .75F));
        } else {
            model.rotateX("wheel holder front", animation.wheel(partialTick));
            model.rotateX("wheel holder back", animation.wheel(partialTick));
            model.rotateY("wheel holder front", -animation.steer(partialTick) * .15F);
            model.rotateZ("steering wheel main", animation.steer(partialTick));
            model.rotateY("door left main", animation.door(0, partialTick));
            model.rotateY("door right main", animation.door(1, partialTick));
            model.rotateY("Back door left main", animation.door(2, partialTick));
            model.rotateY("Back door right main", animation.door(3, partialTick));
        }
        pose.pushPose();
        pose.translate(0, 1.25, 0);
        pose.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));
        if (entity instanceof ports.jurassicraft.vehicle.HelicopterEntity helicopter) {
            pose.mulPose(Vector3f.XP.rotationDegrees(-helicopter.flightPitch()));
            pose.mulPose(Vector3f.ZP.rotationDegrees(helicopter.flightRoll()));
        } else {
            double backPivot = entity instanceof ports.jurassicraft.vehicle.ExplorerEntity explorer && explorer.isOnTourRail() ? 0 : 1.4;
            pose.translate(0, -.5, backPivot);
            pose.mulPose(Vector3f.XP.rotationDegrees(animation.pitch(partialTick)));
            pose.translate(0, .5, -backPivot);
            pose.mulPose(Vector3f.ZP.rotationDegrees(-animation.roll(partialTick)));
        }
        pose.scale(-1, -1, 1);
        model.render(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }
    @Override public ResourceLocation getTextureLocation(T entity) { return texture; }
}
