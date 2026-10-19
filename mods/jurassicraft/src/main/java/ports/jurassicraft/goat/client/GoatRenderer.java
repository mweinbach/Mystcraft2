package ports.jurassicraft.goat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.goat.JurassicGoat;

public final class GoatRenderer extends MobRenderer<JurassicGoat, GoatModel> {
    private final GoatModel billy, nanny, kid;
    public GoatRenderer(EntityRendererProvider.Context context) {
        super(context, new GoatModel("billy"), .4F);
        billy = model;
        nanny = new GoatModel("nanny");
        kid = new GoatModel("kid");
    }
    @Override public void render(JurassicGoat goat, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        model = goat.isBaby() ? kid : goat.isBilly() ? billy : nanny;
        super.render(goat, yaw, partialTicks, pose, buffers, light);
    }
    @Override protected void scale(JurassicGoat goat, PoseStack pose, float partialTicks) {
        float scale = goat.isBaby() ? .2F : .47F;
        pose.scale(scale, scale, scale);
    }
    @Override public ResourceLocation getTextureLocation(JurassicGoat goat) {
        String type = goat.isBaby() ? "kid" : goat.isBilly() ? "billy" : "nanny";
        return new ResourceLocation(PortMod.MOD_ID, "textures/entities/goat/" + type + "_" + goat.variant() + ".png");
    }
}
