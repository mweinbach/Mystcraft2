package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.mystcraft.minecraft.portal.BookReceptacleBlock;
import net.mystcraft.minecraft.portal.BookReceptacleBlockEntity;

/** Closed articulated book pose from the original RenderBookReceptacle. */
public final class BookReceptacleRenderer implements BlockEntityRenderer<BookReceptacleBlockEntity> {
    private final ClassicBookVisuals book = new ClassicBookVisuals();
    public BookReceptacleRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(BookReceptacleBlockEntity receptacle, float delta, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (receptacle.getBook().isEmpty()) return;
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        switch (receptacle.getBlockState().getValue(BookReceptacleBlock.FACING)) {
            case UP -> { poses.mulPose(Vector3f.XP.rotationDegrees(-90)); poses.mulPose(Vector3f.YP.rotationDegrees(90)); }
            case NORTH -> poses.mulPose(Vector3f.YP.rotationDegrees(-90));
            case SOUTH -> poses.mulPose(Vector3f.YP.rotationDegrees(90));
            case EAST -> poses.mulPose(Vector3f.YP.rotationDegrees(180));
            default -> { }
        }
        poses.mulPose(Vector3f.XP.rotationDegrees(receptacle.getPitch()));
        poses.mulPose(Vector3f.YP.rotationDegrees(receptacle.getYaw()));
        poses.scale(0.8F, 0.8F, 0.8F);
        book.render(receptacle.getBook(), poses, buffers, light, overlay, 0.005F);
        poses.popPose();
        ClassicBookLabels.render(receptacle.getBook(), net.minecraft.world.phys.Vec3.atCenterOf(receptacle.getBlockPos()), .5F, 1.25F, .5F, poses, buffers, light);
    }
}
