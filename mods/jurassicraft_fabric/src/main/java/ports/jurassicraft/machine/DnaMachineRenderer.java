package ports.jurassicraft.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Displays the sample items in the same positions as the original lab machines. */
public final class DnaMachineRenderer implements BlockEntityRenderer<DnaMachineBlockEntity> {
    private final ItemRenderer items;
    public DnaMachineRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(DnaMachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (machine.getLevel() == null || !(machine.getBlockState().getBlock() instanceof DnaMachineBlock block)) return;
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (block.kind() == DnaMachineKind.EXTRACTOR) {
            if (machine.getItem(0).isEmpty()) return;
            if (facing == Direction.EAST || facing == Direction.WEST) facing = facing.getOpposite();
            pose.pushPose();
            pose.translate(.5, 1.5, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(facing.get2DDataValue() * 90 - 180));
            pose.scale(-1, -1, 1);
            pose.translate(.225, 1.25, -.125);
            pose.mulPose(Vector3f.XP.rotationDegrees(-90));
            pose.scale(-.375F, -.375F, .375F);
            items.renderStatic(machine.getItem(0), ItemTransforms.TransformType.FIXED, light, overlay, pose, buffers, 0);
            pose.popPose();
        } else if (block.kind() == DnaMachineKind.SEQUENCER) {
            if (facing == Direction.NORTH || facing == Direction.SOUTH) facing = facing.getOpposite();
            pose.pushPose();
            pose.translate(.5, 1.5, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(facing.get2DDataValue() * 90));
            pose.translate(-.2, -.6, .15);
            pose.scale(-.375F, -.375F, .375F);
            pose.mulPose(Vector3f.XP.rotationDegrees(90));
            pose.translate(0, 0, Mth.sin((machine.getLevel().getGameTime() + partialTick) * .05F) * .025F);
            for (int slot = 0; slot < 6; slot++) {
                pose.translate(0, 0, -.4);
                if (slot % 2 == 0 && !machine.getItem(slot).isEmpty())
                    items.renderStatic(machine.getItem(slot), ItemTransforms.TransformType.FIXED, light, overlay, pose, buffers, slot);
            }
            pose.popPose();
        }
    }
}
