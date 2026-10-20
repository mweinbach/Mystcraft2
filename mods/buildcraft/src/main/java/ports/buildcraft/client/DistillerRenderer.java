package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.factory.DistillerBlock;
import ports.buildcraft.factory.DistillerBlockEntity;
import ports.buildcraft.lib.ClientTank;
import ports.buildcraft.lib.Power;

/**
 * Upstream {@code RenderDistiller} and {@code models/tiles/distiller.json}: the fluid in the three tanks
 * and the two power blocks that bob up the input tank faster the more power the distiller draws, their
 * texture brightening with the average power. Positions are for a distiller facing west.
 */
public final class DistillerRenderer implements BlockEntityRenderer<DistillerBlockEntity> {
    private static final List<ResourceLocation> POWER_SPRITES = List.of(
            BuildCraft.id("block/distiller/power_sprite_a"), BuildCraft.id("block/distiller/power_sprite_b"),
            BuildCraft.id("block/distiller/power_sprite_c"), BuildCraft.id("block/distiller/power_sprite_d"));
    private static final float INSET = 16 / 64F;

    public static List<ResourceLocation> sprites() {
        return POWER_SPRITES;
    }

    @Override
    public void render(DistillerBlockEntity distiller, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(distiller.getBlockState().getBlock() instanceof DistillerBlock)) return;
        poseStack.pushPose();
        RenderUtil.rotateFromWest(poseStack, distiller.getBlockState().getValue(DistillerBlock.FACING));

        float state = distiller.powerPartState(partialTicks);
        float y1 = 1 - Math.abs(state % 1 - 0.5F) * 2;
        float y2 = 1 - Math.abs((state <= 0.5F ? 0 : (state - 0.5F) % 1) - 0.5F) * 2;
        long powerMj = distiller.powerAverageClient() / Power.MJ;
        // Upstream "#off" is the top half of sprite a; "#power_N" steps through the halves of sprites a to d.
        int texture = distiller.powerAverageClient() <= 0 ? 0 : Mth.clamp((int) (powerMj * 6 / (DistillerBlockEntity.MAX_MJ_PER_TICK / Power.MJ)), 1, 6);
        TextureAtlasSprite sprite = RenderUtil.sprite(POWER_SPRITES.get(texture / 2));
        float v = texture % 2 == 1 ? 8 : 0;
        RenderUtil.FaceUv uvs = (face, ax, ay, az, bx, by, bz) -> switch (face.getAxis()) {
            case Y -> new float[] {4, v, 12, v + 4};
            case Z -> new float[] {4, v + 4, 12, v + 8};
            case X -> new float[] {0, v + 4, 4, v + 8};
        };
        VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        RenderUtil.box(poseStack, cutout, sprite, uvs, 0, y1 * 12, 12, 8, y1 * 12 + 4, 16, 0xFFFFFFFF, light);
        RenderUtil.box(poseStack, cutout, sprite, uvs, 0, y2 * 12, 0, 8, y2 * 12 + 4, 4, 0xFFFFFFFF, light);

        tank(poseStack, buffers, distiller.inputForRender(), partialTicks, 0, 0, 4, 8, 16, 12, light);
        tank(poseStack, buffers, distiller.gasForRender(), partialTicks, 8, 8, 0, 16, 16, 16, light);
        tank(poseStack, buffers, distiller.liquidForRender(), partialTicks, 8, 0, 0, 16, 8, 16, light);
        poseStack.popPose();
    }

    private static void tank(PoseStack poseStack, MultiBufferSource buffers, ClientTank tank, float partialTicks,
            float x1, float y1, float z1, float x2, float y2, float z2, int light) {
        TankRenderer.fluid(poseStack, buffers, tank.fluid(), tank.amount(partialTicks), tank.capacity(),
                x1 + INSET, y1 + INSET, z1 + INSET, x2 - INSET, y2 - INSET, z2 - INSET, light);
    }
}
