package ports.jurassicraft.assembly.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.SkeletonDisplayBlock;
import ports.jurassicraft.assembly.SkeletonDisplayBlockEntity;
import ports.jurassicraft.client.DinosaurModel;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurProfile;

public final class SkeletonDisplayRenderer implements BlockEntityRenderer<SkeletonDisplayBlockEntity> {
    private final Map<Species, DinosaurModel> models = new EnumMap<>(Species.class);
    private final Map<Integer, DinosaurModel> tyrannosaurusPoses = new HashMap<>();
    @Override public void render(SkeletonDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Species species = display.species();
        DinosaurProfile profile = DinosaurProfile.of(species);
        DinosaurModel model = species == Species.TYRANNOSAURUS && display.pose() > 0
                ? tyrannosaurusPoses.computeIfAbsent(display.pose(), value -> new DinosaurModel(species, value == 1 ? "death" : "attacking"))
                : models.computeIfAbsent(species, value -> new DinosaurModel(value, true));
        ResourceLocation texture = new ResourceLocation(PortMod.MOD_ID,
                "textures/entities/" + species.id() + "/" + species.id() + (display.fresh() ? "_female_skeleton.png" : "_male_skeleton.png"));
        float scale = profile.adultScale();
        pose.pushPose();
        pose.translate(0.5, 0.125, 0.5);
        pose.mulPose(Vector3f.YP.rotationDegrees(180.0F - display.getBlockState().getValue(SkeletonDisplayBlock.FACING).toYRot()));
        pose.scale(-scale, -scale, scale);
        pose.translate(0, profile.offsetY(), profile.offsetZ());
        pose.translate(0, -1.501F, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, 1, 1, 1, 1);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(SkeletonDisplayBlockEntity display) { return true; }
    @Override public int getViewDistance() { return 128; }
}
