package ports.jurassicraft.feeder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.feeder.FeederBlock;
import ports.jurassicraft.feeder.FeederBlockEntity;

/** Two moving parts of the original Tabula model; all other cubes are baked. */
public final class FeederRenderer implements BlockEntityRenderer<FeederBlockEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/feeder.png");
    private final ModelPart lid1 = lid(27, 19, 0, -4);
    private final ModelPart lid2 = lid(36, 24, -4, 4);
    public FeederRenderer(BlockEntityRendererProvider.Context context) {}
    private static ModelPart lid(int u, int v, float offsetZ, float pivotZ) {
        ModelPart.Cube cube = new ModelPart.Cube(u, v, -4, -.5F, offsetZ, 8, 1, 4, 0, 0, 0, false, 64, 64);
        ModelPart part = new ModelPart(List.of(cube), Map.of());
        // These pivots include the translations of Base, Pedestal, Central column and Opening.
        part.setPos(0, 10.05F, pivotZ);
        return part;
    }
    @Override public void render(FeederBlockEntity feeder, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = feeder.getBlockState().getValue(FeederBlock.FACING);
        int x = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
        int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
        pose.pushPose();
        // Match the exact blockstate rotation of the baked base, around its center.
        pose.translate(.5, .5, .5);
        pose.mulPose(BlockModelRotation.by(x, y).getRotation().getLeftRotation());
        pose.translate(0, 1, 0);
        pose.scale(1, -1, 1);
        float angle = (float)Math.toRadians(99.13F * feeder.lidOpening(partial));
        lid1.setRotation(angle, 0, 0);
        lid2.setRotation(-angle, 0, 0);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        lid1.render(pose, vertices, light, overlay);
        lid2.render(pose, vertices, light, overlay);
        pose.popPose();
    }
}
