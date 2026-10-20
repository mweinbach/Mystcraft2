package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.builders.snapshot.SnapshotBuilder;
import ports.buildcraft.builders.snapshot.SnapshotHost;

/**
 * Upstream {@code RenderFiller} and {@code RenderSnapshotBuilder}: blocks fly from the filler to where
 * they are placed, a robot hovers over the blocks being broken and fires power lasers at them, and a
 * marked area is outlined with write stripes.
 */
public final class FillerRenderer implements BlockEntityRenderer<FillerBlockEntity> {
    public static final ResourceLocation ROBOT = BuildCraft.id("robot");
    private static final float ROBOT_RADIUS = 4;
    private static final float ITEM_SCALE = 0.7F;
    private final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

    @Override
    public void render(FillerBlockEntity filler, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = filler.getLevel();
        if (level == null) return;
        BlockPos origin = filler.getBlockPos();
        if (filler.getBuilder() != null) renderBuilder(filler.getBuilder(), level, origin, partialTicks, poseStack, buffers);
        if (filler.markerBox) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
            LaserRenderer.box(level, origin, poseStack, consumer, filler.box, LaserRenderer.STRIPES_WRITE, true);
        }
    }

    private <H extends SnapshotHost> void renderBuilder(SnapshotBuilder<H> builder, Level level, BlockPos origin, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 start = Vec3.atLowerCornerOf(origin).add(0.5, 1, 0.5);
        int seed = (int) origin.asLong();
        for (SnapshotBuilder<H>.PlaceTask placeTask : builder.clientPlaceTasks) {
            Vec3 prevPos = builder.prevClientPlaceTasks.stream()
                    .filter(previous -> previous.pos.equals(placeTask.pos))
                    .map(builder::getPlaceTaskItemPos)
                    .findFirst()
                    .orElse(start);
            Vec3 pos = prevPos.add(builder.getPlaceTaskItemPos(placeTask).subtract(prevPos).scale(partialTicks));
            int itemLight = LevelRenderer.getLightColor(level, new BlockPos(pos));
            for (ItemStack item : placeTask.items) {
                poseStack.pushPose();
                poseStack.translate(pos.x - origin.getX(), pos.y - origin.getY() - 0.1, pos.z - origin.getZ());
                poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                itemRenderer.renderStatic(item, ItemTransforms.TransformType.GROUND, itemLight, OverlayTexture.NO_OVERLAY, poseStack, buffers, seed++);
                poseStack.popPose();
            }
        }

        Vec3 robotPos = builder.robotPos;
        if (robotPos == null) return;
        if (builder.prevRobotPos != null) robotPos = builder.prevRobotPos.add(robotPos.subtract(builder.prevRobotPos).scale(partialTicks));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite robot = RenderUtil.sprite(ROBOT);
        int robotLight = LevelRenderer.getLightColor(level, new BlockPos(robotPos));
        float cx = (float) (robotPos.x - origin.getX()) * 16;
        float cy = (float) (robotPos.y - origin.getY()) * 16;
        float cz = (float) (robotPos.z - origin.getZ()) * 16;
        int i = 0;
        for (Direction face : Direction.values()) {
            RenderUtil.face(poseStack, consumer, robot, face, new float[] {i * 2, 0, (i + 1) * 2, 2},
                    cx - ROBOT_RADIUS, cy - ROBOT_RADIUS, cz - ROBOT_RADIUS, cx + ROBOT_RADIUS, cy + ROBOT_RADIUS, cz + ROBOT_RADIUS,
                    0xFFFFFFFF, robotLight);
            i++;
        }
        Vec3 laserStart = robotPos.subtract(0, 0.27, 0);
        for (SnapshotBuilder<H>.BreakTask breakTask : builder.clientBreakTasks) {
            double progress = Mth.clamp(breakTask.power * 1D / Math.max(1, breakTask.getTarget()), 0, 1);
            LaserRenderer.Type type = LaserRenderer.POWERS[(int) Math.round(progress * (LaserRenderer.POWERS.length - 1))];
            LaserRenderer.render(level, origin, poseStack, consumer, type, laserStart, Vec3.atCenterOf(breakTask.pos), 1 / 16D);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FillerBlockEntity filler) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
