package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.robotics.RobotEntity;
import ports.buildcraft.robotics.board.RobotBoardType;

/**
 * Draws a robot as the half-block cube of upstream's robot item model, skinned with its board's robot texture
 * (upstream's MMPL robot art, copied to {@code textures/entity/robot}), facing where it flies and bobbing gently
 * unless asleep. The tool or stack it carries hangs beneath it.
 */
public class RobotRenderer extends EntityRenderer<RobotEntity> {
    /** Upstream {@code models/robot.json} face UVs, in sprite pixels. */
    private static final float[] UP = {8, 0, 12, 4}, DOWN = {4, 0, 8, 4}, NORTH = {4, 4, 8, 8}, SOUTH = {12, 4, 16, 8},
            WEST = {8, 4, 12, 8}, EAST = {0, 4, 4, 8};

    public RobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.2F;
    }

    public static List<ResourceLocation> sprites() {
        List<ResourceLocation> sprites = new ArrayList<>();
        sprites.add(RobotBoardType.BLANK_SPRITE);
        for (RobotBoardType type : RobotBoardType.values()) sprites.add(type.sprite());
        return sprites;
    }

    private static float[] uv(Direction face) {
        return switch (face) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    @Override
    public void render(RobotEntity robot, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        float bob = robot.isSleeping() ? 0 : Mth.sin((robot.tickCount + partialTicks) * 0.15F) * 0.03F;
        poseStack.translate(0, bob, 0);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180 - Mth.rotLerp(partialTicks, robot.yRotO, robot.getYRot())));
        poseStack.translate(-0.5, -0.25, -0.5);
        RobotBoardType board = robot.boardType();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        RenderUtil.box(poseStack, consumer, RenderUtil.sprite(board == null ? RobotBoardType.BLANK_SPRITE : board.sprite()),
                (face, x1, y1, z1, x2, y2, z2) -> uv(face), 4, 4, 4, 12, 12, 12, 0xFFFFFFFF, light);
        poseStack.popPose();

        ItemStack held = robot.heldForDisplay();
        if (!held.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, bob - 0.1, 0);
            poseStack.scale(0.4F, 0.4F, 0.4F);
            Minecraft.getInstance().getItemRenderer().renderStatic(held, ItemTransforms.TransformType.GROUND, light,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, robot.getId());
            poseStack.popPose();
        }
        super.render(robot, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RobotEntity robot) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
