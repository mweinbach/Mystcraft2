package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.client.model.ClassicLecternModel;
import net.mystcraft.minecraft.exploration.MystLecternBlock;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.item.LinkingBookItem;

public final class MystLecternRenderer implements BlockEntityRenderer<MystLecternBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("mystcraft", "textures/entity/lectern.png");
    private final ClassicLecternModel model = new ClassicLecternModel();
    private final ClassicBookVisuals book = new ClassicBookVisuals();
    public MystLecternRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(MystLecternBlockEntity lectern, float delta, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        Direction facing = lectern.getBlockState().getValue(MystLecternBlock.FACING);
        if (facing.getAxis() == Direction.Axis.Z) facing = facing.getOpposite();
        poses.pushPose(); poses.translate(0.5, 0, 0.5);
        poses.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot() + 90));
        model.render(poses, buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, overlay);
        if (!lectern.getBook().isEmpty()) {
            poses.translate(0, 0.255, 0); poses.mulPose(Vector3f.ZP.rotationDegrees(110));
            if (lectern.getBook().getItem() instanceof LinkingBookItem) {
                poses.scale(0.8F, 0.8F, 0.8F);
                book.render(lectern.getBook(), poses, buffers, light, overlay, 1.22F);
            } else {
                poses.translate(0, 0.2, 0); poses.mulPose(Vector3f.XP.rotationDegrees(180));
                poses.mulPose(Vector3f.YP.rotationDegrees(90));
                if (lectern.getBook().is(net.minecraft.world.item.Items.FILLED_MAP)) {
                    poses.mulPose(Vector3f.YP.rotationDegrees(180));
                    poses.mulPose(Vector3f.ZP.rotationDegrees(180));
                    poses.scale(.006F, .006F, .006F); poses.translate(-65, -107, -3);
                    var vertices = buffers.getBuffer(RenderType.text(new ResourceLocation("minecraft", "textures/map/map_background.png")));
                    var matrix = poses.last().pose();
                    vertices.vertex(matrix, -7, 135, 0).color(255,255,255,255).uv(0,1).uv2(light).endVertex();
                    vertices.vertex(matrix, 135, 135, 0).color(255,255,255,255).uv(1,1).uv2(light).endVertex();
                    vertices.vertex(matrix, 135, -7, 0).color(255,255,255,255).uv(1,0).uv2(light).endVertex();
                    vertices.vertex(matrix, -7, -7, 0).color(255,255,255,255).uv(0,0).uv2(light).endVertex();
                    Integer id = net.minecraft.world.item.MapItem.getMapId(lectern.getBook());
                    var map = net.minecraft.world.item.MapItem.getSavedData(lectern.getBook(), lectern.getLevel());
                    if (id != null && map != null) Minecraft.getInstance().gameRenderer.getMapRenderer().render(poses, buffers, id, map, true, light);
                } else {
                    poses.translate(0, 0.25, 0);
                    Minecraft.getInstance().getItemRenderer().renderStatic(lectern.getBook(), ItemTransforms.TransformType.FIXED,
                            light, overlay, poses, buffers, 0);
                }
            }
        }
        poses.popPose();
        if (lectern.getBook().getItem() instanceof LinkingBookItem)
            ClassicBookLabels.render(lectern.getBook(), net.minecraft.world.phys.Vec3.atCenterOf(lectern.getBlockPos()),
                    .5F, 1.25F, .5F, poses, buffers, light);
    }
}
