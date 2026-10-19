package ports.jurassicraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurProfile;

public final class DinosaurRenderer extends MobRenderer<DinosaurEntity, DinosaurModel> {
    private final Species species;
    public DinosaurRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new DinosaurModel(species), .65f);
        this.species = species;
        if (species == Species.TYRANNOSAURUS) addLayer(new TyrannosaurusLayers(this));
    }
    @Override protected void scale(DinosaurEntity entity, PoseStack pose, float partialTick) {
        DinosaurProfile p = DinosaurProfile.of(species);
        float scale = Mth.lerp(entity.growth(), p.babyScale(), p.adultScale()) * entity.geneticScale();
        shadowRadius = Math.min(2, .65f * scale);
        pose.translate(0, p.offsetY() * scale, p.offsetZ() * scale);
        pose.scale(scale, scale, scale);
    }
    @Override public ResourceLocation getTextureLocation(DinosaurEntity entity) {
        String id = species.id();
        return new ResourceLocation(PortMod.MOD_ID, "textures/entities/" + id + "/" + id + "_" + (entity.isMale() ? "male" : "female") + "_adult.png");
    }
    private static final class TyrannosaurusLayers extends RenderLayer<DinosaurEntity, DinosaurModel> {
        TyrannosaurusLayers(DinosaurRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, DinosaurEntity entity,
                float limbSwing, float amount, float partialTick, float age, float yaw, float pitch) {
            if (entity.isInvisible()) return;
            String gender = entity.isMale() ? "male" : "female";
            for (String layer : new String[]{"claws", "eyes", "mouth", "nostrils", "stripes", "teeth"}) {
                ResourceLocation texture = new ResourceLocation(PortMod.MOD_ID,
                        "textures/entities/tyrannosaurus/tyrannosaurus_" + gender + "_adult_" + layer + ".png");
                renderColoredCutoutModel(getParentModel(), texture, pose, buffers, light, entity, 1, 1, 1);
            }
        }
    }
}
