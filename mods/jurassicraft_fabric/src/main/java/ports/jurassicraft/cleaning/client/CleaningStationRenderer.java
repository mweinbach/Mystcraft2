package ports.jurassicraft.cleaning.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import ports.jurassicraft.cleaning.CleaningStationBlock;
import ports.jurassicraft.cleaning.CleaningStationBlockEntity;

/** The original cleaning station displays and slowly turns its input sample. */
public final class CleaningStationRenderer implements BlockEntityRenderer<CleaningStationBlockEntity> {
    private final ItemRenderer items;
    public CleaningStationRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @Override public void render(CleaningStationBlockEntity station, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (station.getLevel() == null || station.visualInput().isEmpty()) return;
        Direction facing = station.getBlockState().getValue(CleaningStationBlock.FACING);
        if (facing == Direction.NORTH || facing == Direction.SOUTH) facing = facing.getOpposite();
        pose.pushPose();
        pose.translate(.5, 1.5, .5);
        pose.mulPose(Vector3f.YP.rotationDegrees(facing.get2DDataValue() * 90));
        pose.translate(0, -1.03, -.05);
        pose.scale(-.25F, -.25F, .25F);
        pose.mulPose(Vector3f.XP.rotationDegrees(90 + station.visualRotation(partial)));
        // Upstream renders the untransformed baked model; FIXED applies the item-frame
        // display transform and halves the size of block-based fossil samples.
        items.renderStatic(station.visualInput(), ItemTransforms.TransformType.NONE, light, overlay, pose, buffers, 0);
        pose.popPose();
    }
}
