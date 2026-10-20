package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.builders.BuilderBlockEntity;

/**
 * Upstream {@code RenderBuilder}: write stripes around the box being built, directed stripes along the
 * builder's path, and the builder's {@link SnapshotBuilderRenderer} work.
 */
public final class BuilderRenderer implements BlockEntityRenderer<BuilderBlockEntity> {
    private static final double OFFSET = 0.1;

    @Override
    public void render(BuilderBlockEntity builder, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = builder.getLevel();
        if (level == null) return;
        BlockPos origin = builder.getBlockPos();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        LaserRenderer.box(level, origin, poseStack, consumer, builder.getBox(), LaserRenderer.STRIPES_WRITE, true);
        List<BlockPos> path = builder.path;
        if (path != null) {
            for (int i = 1; i < path.size(); i++) {
                Vec3 from = Vec3.atCenterOf(path.get(i - 1));
                Vec3 to = Vec3.atCenterOf(path.get(i));
                LaserRenderer.render(level, origin, poseStack, consumer, LaserRenderer.STRIPES_WRITE_DIRECTION,
                        offset(from, to), offset(to, from), 1 / 16.1);
            }
        }
        if (builder.getBuilder() != null) SnapshotBuilderRenderer.render(builder.getBuilder(), level, origin, partialTicks, poseStack, buffers);
    }

    private static Vec3 offset(Vec3 from, Vec3 to) {
        return from.add(to.subtract(from).normalize().scale(OFFSET));
    }

    @Override
    public boolean shouldRenderOffScreen(BuilderBlockEntity builder) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
