package ports.jurassicraft.figure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.figure.ActionFigureBlockEntity;

/** Render an unspawned adult through the normal dinosaur renderer at the original 0.15 scale. */
public final class ActionFigureRenderer implements BlockEntityRenderer<ActionFigureBlockEntity> {
    private final Map<Species, DinosaurEntity> models = new EnumMap<>(Species.class);
    private Level modelLevel;
    @Override public void render(ActionFigureBlockEntity figure, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = figure.getLevel();
        if (level == null) return;
        if (level != modelLevel) { models.clear(); modelLevel = level; }
        DinosaurEntity model = models.computeIfAbsent(figure.species(), species -> DinosaurRegistry.type(species).create(level));
        if (model == null) return;
        model.setMale(figure.gender() != 2);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Vector3f.YP.rotationDegrees(figure.rotation()));
        pose.scale(.15F, .15F, .15F);
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(model).render(model, 0, 0, pose, buffers, light);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(ActionFigureBlockEntity figure) { return true; }
}
