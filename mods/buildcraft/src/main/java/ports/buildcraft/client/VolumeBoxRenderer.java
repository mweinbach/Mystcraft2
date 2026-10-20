package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.marker.volume.ClientVolumeBoxes;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;

/**
 * Upstream {@code RenderVolumeBoxes}: every volume box in the client's level, drawn after translucent
 * blocks. A box the player is editing uses the signal laser, and a box a machine holds that machine's laser.
 */
public final class VolumeBoxRenderer {
    private VolumeBoxRenderer() {}

    /** {@code poseStack} carries the camera's rotation but not its position. */
    public static void render(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ClientVolumeBoxes.VOLUME_BOXES.isEmpty()) return;
        Vec3 cameraPos = camera.getPosition();
        BlockPos origin = camera.getBlockPosition();
        RenderType renderType = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        poseStack.pushPose();
        poseStack.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y, origin.getZ() - cameraPos.z);
        for (VolumeBox volumeBox : ClientVolumeBoxes.VOLUME_BOXES) {
            LaserRenderer.box(minecraft.level, origin, poseStack, consumer, volumeBox.box, laserType(volumeBox, minecraft.player), false);
        }
        poseStack.popPose();
        buffers.endBatch(renderType);
    }

    static LaserRenderer.Type laserType(VolumeBox volumeBox, Player player) {
        if (volumeBox.isEditingBy(player)) return LaserRenderer.MARKER_VOLUME_SIGNAL;
        return volumeBox.getLockTargetsStream()
                .filter(Lock.UsedByMachine.class::isInstance)
                .map(target -> switch (((Lock.UsedByMachine) target).use()) {
                    case STRIPES_READ -> LaserRenderer.STRIPES_READ;
                    case STRIPES_WRITE -> LaserRenderer.STRIPES_WRITE;
                })
                .findFirst()
                .orElse(LaserRenderer.MARKER_VOLUME_CONNECTED);
    }
}
