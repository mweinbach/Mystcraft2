package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.factory.HeatExchangerBlock;
import ports.buildcraft.factory.HeatExchangerBlockEntity;
import ports.buildcraft.factory.HeatExchangerBlockEntity.ProgressState;
import ports.buildcraft.lib.ClientTank;

/**
 * Upstream {@code RenderHeatExchange}, drawn by the start of a formed row: the fluid in the start's and
 * end's tanks, and the two fluids flowing through the row, which grow from their inlets while the
 * exchanger warms up and scroll toward their outlets. Positions are for an exchanger facing west, whose
 * start is to the north and whose end is to the south.
 */
public final class HeatExchangerRenderer implements BlockEntityRenderer<HeatExchangerBlockEntity> {
    private static final float INSET = 16 / 64F;
    private static final int SCROLL_TICKS = 31;

    @Override
    public boolean shouldRenderOffScreen(HeatExchangerBlockEntity exchanger) {
        return exchanger.part() == HeatExchangerBlock.Part.START;
    }

    @Override
    public void render(HeatExchangerBlockEntity start, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (start.part() != HeatExchangerBlock.Part.START || start.getLevel() == null) return;
        HeatExchangerBlockEntity end = start.end();
        poseStack.pushPose();
        RenderUtil.rotateFromWest(poseStack, start.facing());

        tank(poseStack, buffers, start.inputForRender(), partialTicks, 2 + INSET, 0, 2 + INSET, 14 - INSET, 2, 14 - INSET, light);
        tank(poseStack, buffers, start.outputForRender(), partialTicks, 4 + INSET, 4 + INSET, 0, 12 - INSET, 12 - INSET, 2, light);
        int middles = start.middleCount();
        if (end != null) {
            poseStack.pushPose();
            poseStack.translate(0, 0, middles + 1);
            tank(poseStack, buffers, end.outputForRender(), partialTicks, 2 + INSET, 14, 2 + INSET, 14 - INSET, 16, 14 - INSET, light);
            tank(poseStack, buffers, end.inputForRender(), partialTicks, 4 + INSET, 4 + INSET, 14, 12 - INSET, 12 - INSET, 16, light);
            poseStack.popPose();

            double progress = start.progress(partialTicks);
            if (middles > 0 && progress > 0) {
                double length = middles + 2 - 4 / 16.0 - 0.02;
                double p0 = 2 / 16.0 + 0.01;
                double p1 = p0 + length - 0.01;
                double grown = length * progress;
                // Warming up, each fluid grows from its inlet; cooling down, it drains toward its outlet.
                boolean warming = start.progressState() == ProgressState.PREPARING;
                double coolFrom = warming ? p1 - grown : p0, coolTo = warming ? p1 : p0 + grown;
                double heatFrom = warming ? p0 : p1 - grown, heatTo = warming ? p0 + grown : p1;
                double scroll = (start.getLevel().getGameTime() % SCROLL_TICKS + partialTicks) / SCROLL_TICKS;
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
                flow(poseStack, consumer, end.inputForRender().fluid(), 4, coolFrom + 0.01, coolTo - 0.01, -scroll, light);
                flow(poseStack, consumer, start.inputForRender().fluid(), 2, heatFrom, heatTo, scroll, light);
            }
        }
        poseStack.popPose();
    }

    private static void tank(PoseStack poseStack, MultiBufferSource buffers, ClientTank tank, float partialTicks,
            float x1, float y1, float z1, float x2, float y2, float z2, int light) {
        TankRenderer.fluid(poseStack, buffers, tank.fluid(), tank.amount(partialTicks), tank.capacity(), x1, y1, z1, x2, y2, z2, light);
    }

    /**
     * Upstream {@code renderFlow}: a bar of fluid along the row from {@code from} to {@code to} blocks
     * south of the start, inset {@code inset} pixels from the sides, textured once per block on a grid
     * shifted by {@code shift} blocks so that the texture moves along the row.
     */
    private static void flow(PoseStack poseStack, VertexConsumer consumer, Fluid fluid, int inset, double from, double to, double shift, int light) {
        if (fluid == Fluids.EMPTY || to <= from) return;
        float side = inset + 0.1F;
        int colour = ClientPlatform.get().fluidColour(fluid);
        TextureAtlasSprite sprite = ClientPlatform.get().fluidSprite(fluid);
        for (int cell = (int) Math.floor(from - shift); cell + shift < to; cell++) {
            double cellStart = cell + shift;
            double a = Math.max(from, cellStart), b = Math.min(to, cellStart + 1);
            if (b <= a) continue;
            boolean openNorth = a > from, openSouth = b < to;
            RenderUtil.FaceUv uvs = (face, x1, y1, z1, x2, y2, z2) ->
                    face == Direction.NORTH && openNorth || face == Direction.SOUTH && openSouth ? null : RenderUtil.autoUv(face, x1, y1, z1, x2, y2, z2);
            poseStack.pushPose();
            poseStack.translate(0, 0, cellStart);
            RenderUtil.box(poseStack, consumer, sprite, uvs, side, side, (float) (a - cellStart) * 16,
                    16 - side, 16 - side, (float) (b - cellStart) * 16, colour, light);
            poseStack.popPose();
        }
    }
}
