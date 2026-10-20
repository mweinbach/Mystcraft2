package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.silicon.LaserBlock;
import ports.buildcraft.silicon.LaserBlockEntity;

/**
 * Upstream {@code RenderLaser}: a beam from the laser's lens to its target, coloured by the average power it
 * has been sending. Upstream's option to hide beams unless wearing goggles is not ported; beams always show,
 * as upstream's default did.
 */
public final class LaserBlockRenderer implements BlockEntityRenderer<LaserBlockEntity> {
    @Override
    public void render(LaserBlockEntity laser, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = laser.getLevel();
        Vec3 laserPos = laser.laserPos();
        if (level == null || laserPos == null) return;
        int band = LaserBlockEntity.renderBand(laser.averagePower());
        if (band < 0) return;
        BlockPos origin = laser.getBlockPos();
        BlockState state = laser.getBlockState();
        if (!state.hasProperty(LaserBlock.FACING)) return;
        Direction side = state.getValue(LaserBlock.FACING);
        Vec3 start = Vec3.atCenterOf(origin).add(Vec3.atLowerCornerOf(side.getNormal()).scale(4 / 16D));
        LaserRenderer.render(level, origin, poseStack, buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)),
                LaserRenderer.POWERS[band], start, laserPos, 1 / 16D);
    }

    @Override
    public boolean shouldRenderOffScreen(LaserBlockEntity laser) {
        return true;
    }
}
