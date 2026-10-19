package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.EngineBlockEntity;

/**
 * The moving parts of upstream {@code engine_base.json}: the piston plate and the bellows chamber.
 * The base and the stage-coloured trunk are static block models selected by the block state.
 */
public final class EngineRenderer<T extends EngineBlockEntity> implements BlockEntityRenderer<T> {
    public static final ResourceLocation CHAMBER = BuildCraft.id("block/engine/chamber");
    private static final float[] PLATE_TOP = {0, 0, 16, 16};
    private static final float[] PLATE_SIDE = {0, 0, 16, 4};
    private final ResourceLocation back;
    private final ResourceLocation side;

    /** @param material the engine texture folder: {@code wood}, {@code stone} or {@code iron}. */
    public EngineRenderer(String material) {
        back = BuildCraft.id("block/engine/" + material + "/back");
        side = BuildCraft.id("block/engine/" + material + "/side");
    }

    /** Upstream {@code progress_size}: the plate rises over the first half of a stroke and falls over the second. */
    public static float progressSize(float progress) {
        return progress > 0.5F ? (1 - progress) * (8 * 2 - 0.01F) : progress * (8 * 2 - 0.01F);
    }

    @Override
    public void render(T engine, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        float size = progressSize(engine.getProgressClient(partialTicks));
        Direction facing = engine.facing();
        TextureAtlasSprite backSprite = RenderUtil.sprite(back);
        TextureAtlasSprite sideSprite = RenderUtil.sprite(side);
        TextureAtlasSprite chamber = RenderUtil.sprite(CHAMBER);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        RenderUtil.rotateFromUp(poseStack, facing);
        for (Direction face : Direction.values()) {
            boolean vertical = face.getAxis() == Direction.Axis.Y;
            RenderUtil.face(poseStack, consumer, vertical ? backSprite : sideSprite, face, vertical ? PLATE_TOP : PLATE_SIDE,
                    0, 4 + size, 0, 16, 8 + size, 16, 0xFFFFFFFF, light);
        }
        if (size > 0) {
            float[] uv = {3, size, 13, 0};
            for (Direction face : Direction.Plane.HORIZONTAL) {
                RenderUtil.face(poseStack, consumer, chamber, face, uv, 3, 4, 3, 13, 4 + size, 13, 0xFFFFFFFF, light);
            }
        }
        poseStack.popPose();
    }
}
