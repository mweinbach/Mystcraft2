package ports.jurassicraft.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import ports.jurassicraft.village.AncientItemHoldingBlockEntity;

public final class AncientItemHolderRenderer implements BlockEntityRenderer<AncientItemHoldingBlockEntity> {
    @Override public void render(AncientItemHoldingBlockEntity holder, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (holder.item().isEmpty()) return;
        pose.pushPose();
        pose.translate(.5F + holder.xOffset(), .5F + holder.yOffset(), .5F + holder.zOffset());
        pose.mulPose(Vector3f.YP.rotationDegrees(holder.rotation()));
        pose.mulPose(Vector3f.XP.rotationDegrees(90));
        pose.scale(.7F, .7F, .7F);
        Minecraft.getInstance().getItemRenderer().renderStatic(holder.item(), ItemTransforms.TransformType.FIXED, light, overlay, pose, buffers, 0);
        pose.popPose();
    }
}
