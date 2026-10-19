package ports.jurassicraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEggEntity;

public final class DinosaurEggRenderer extends EntityRenderer<DinosaurEggEntity> {
    private final Map<Species, DinosaurModel> models = new EnumMap<>(Species.class);
    public DinosaurEggRenderer(EntityRendererProvider.Context context) {
        super(context);
        for (Species species : Species.values()) if (species != Species.COELACANTH) models.put(species, DinosaurModel.egg(species));
        shadowRadius = .2f;
    }
    @Override public void render(DinosaurEggEntity egg, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 1.5, 0);
        pose.mulPose(Vector3f.YP.rotationDegrees(180 - yaw));
        pose.scale(-1, -1, 1);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(egg)));
        models.get(egg.species()).renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
        super.render(egg, yaw, partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(DinosaurEggEntity egg) {
        return new ResourceLocation(PortMod.MOD_ID, "textures/entities/egg/" + egg.species().id() + ".png");
    }
}
