package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.block.BookstandBlock;
import net.mystcraft.minecraft.blockentity.BookstandBlockEntity;
import net.mystcraft.minecraft.client.model.ClassicBookstandModel;

/** Original classic stand geometry, book geometry, texture UVs and display transforms. */
public final class BookstandRenderer implements BlockEntityRenderer<BookstandBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/entity/bookstand.png");
    private final ClassicBookstandModel standModel = new ClassicBookstandModel();
    private final ClassicBookVisuals book = new ClassicBookVisuals();
    public BookstandRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(BookstandBlockEntity stand, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        float rotation = BookstandBlock.rotationDegrees(stand.getBlockState());
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Vector3f.ZP.rotationDegrees(180));
        poses.mulPose(Vector3f.YP.rotationDegrees(rotation));
        standModel.render(poses, buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        poses.popPose();
        if (stand.getBook().isEmpty()) return;
        poses.pushPose();
        poses.translate(0.5, 0.55, 0.5);
        poses.mulPose(Vector3f.YN.rotationDegrees(90 + rotation));
        poses.mulPose(Vector3f.ZP.rotationDegrees(120));
        poses.scale(0.8F, 0.8F, 0.8F);
        book.render(stand.getBook(), poses, buffers, light, overlay, 1.05F);
        poses.popPose();
        ClassicBookLabels.render(stand.getBook(), net.minecraft.world.phys.Vec3.atCenterOf(stand.getBlockPos()), .5F, 1.25F, .5F, poses, buffers, light);
    }
}
