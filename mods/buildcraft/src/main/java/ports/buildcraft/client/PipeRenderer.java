package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.FluidFlow;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.flow.PowerFlow;

/** Upstream {@code PipeFlowRendererItems}, {@code PipeFlowRendererFluids} and {@code PipeFlowRendererPower}. */
public final class PipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    public static final ResourceLocation POWER_FLOW = BuildCraft.id("block/pipe/power_flow");
    private static final float ITEM_SCALE = 0.7F;
    /** Upstream's flow renderers were TESRs, which 1.12 drew within 64 blocks. */
    private static final double FLOW_RANGE = 64;
    private final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

    /** Pluggables are part of the pipe model upstream, so they are drawn as far as chunks are. */
    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    @Override
    public void render(PipeBlockEntity pipe, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        PluggableRenderers.render(pipe, partialTicks, poseStack, buffers, light);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (!Vec3.atCenterOf(pipe.getBlockPos()).closerThan(camera, FLOW_RANGE)) return;
        if (pipe.flow() instanceof ItemFlow items) renderItems(pipe, items, partialTicks, poseStack, buffers, light);
        else if (pipe.flow() instanceof FluidFlow fluids) renderFluids(pipe, fluids, partialTicks, poseStack, buffers, light);
        else if (pipe.flow() instanceof PowerFlow power) renderPower(pipe, power, partialTicks, poseStack, buffers);
    }

    private void renderItems(PipeBlockEntity pipe, ItemFlow flow, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (pipe.getLevel() == null) return;
        long tick = pipe.getLevel().getGameTime();
        int seed = (int) pipe.getBlockPos().asLong();
        for (ItemFlow.ClientItem item : flow.clientItems()) {
            Vec3 pos = item.item().renderPosition(tick, partialTicks, item.pipeLength());
            poseStack.pushPose();
            poseStack.translate(pos.x, pos.y - 0.1, pos.z);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(item.item().stack(), ItemTransforms.TransformType.GROUND, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, seed++);
            poseStack.popPose();
        }
    }

    private static void renderFluids(PipeBlockEntity pipe, FluidFlow flow, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Fluid fluid = flow.fluidForRender();
        if (fluid == Fluids.EMPTY) return;
        double[] amounts = flow.amountsForRender(partialTicks);
        double capacity = flow.capacity;
        TextureAtlasSprite sprite = ClientPlatform.get().fluidSprite(fluid);
        int colour = ClientPlatform.get().fluidColour(fluid);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        boolean gas = Platform.get().isGaseous(fluid);
        boolean horizontal = false;
        boolean vertical = pipe.isConnected(gas ? Direction.DOWN : Direction.UP);

        for (Direction face : Direction.values()) {
            double amount = amounts[face.ordinal()];
            if (!pipe.isConnected(face) || amount <= 0) continue;
            if (face.getAxis() != Direction.Axis.Y) horizontal = true;
            double size = 0.25;
            double[] centre = offset(face, 0.245 + size / 2);
            double[] radius = {0.24, 0.24, 0.24};
            radius[face.getAxis().ordinal()] = 0.005 + size / 2;
            double fill = amount / capacity;
            if (face.getAxis() == Direction.Axis.Y) {
                double perc = Math.sqrt(fill);
                radius[0] = radius[2] = perc * 0.24;
                fill = 1;
            }
            fluidBox(poseStack, consumer, sprite, colour, light, gas, fill,
                    centre[0] - radius[0], centre[1] - radius[1], centre[2] - radius[2],
                    centre[0] + radius[0], centre[1] + radius[1], centre[2] + radius[2]);
        }

        double amount = amounts[6];
        double horizPos = 0.26;
        if (horizontal || !vertical) {
            fluidBox(poseStack, consumer, sprite, colour, light, gas, amount / capacity, 0.26, 0.26, 0.26, 0.74, 0.74, 0.74);
            horizPos += 0.48 * amount / capacity;
        }
        if (vertical && horizPos < 0.74 && amount > 0) {
            double perc = Math.sqrt(amount / capacity);
            double min = 0.5 - 0.24 * perc;
            double max = 0.5 + 0.24 * perc;
            fluidBox(poseStack, consumer, sprite, colour, light, gas, 1, min, gas ? 0.26 : horizPos, min, max, gas ? 1 - horizPos : 0.74, max);
        }
    }

    private static double[] offset(Direction face, double distance) {
        return new double[] {0.5 + face.getStepX() * distance, 0.5 + face.getStepY() * distance, 0.5 + face.getStepZ() * distance};
    }

    /** Upstream {@code FluidRenderer.renderFluid}: fills the box from the bottom (or from the top for gases). */
    private static void fluidBox(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int colour, int light,
            boolean gas, double fill, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (fill <= 0) return;
        fill = Math.min(1, fill);
        double height = (y2 - y1) * fill;
        if (gas) y1 = y2 - height;
        else y2 = y1 + height;
        RenderUtil.box(poseStack, consumer, sprite, RenderUtil.AUTO, (float) x1 * 16, (float) y1 * 16, (float) z1 * 16,
                (float) x2 * 16, (float) y2 * 16, (float) z2 * 16, colour, light);
    }

    private static void renderPower(PipeBlockEntity pipe, PowerFlow flow, float partialTicks, PoseStack poseStack, MultiBufferSource buffers) {
        double centrePower = 0;
        double[] power = new double[6];
        for (Direction side : Direction.values()) {
            power[side.ordinal()] = flow.displayPower(side);
            centrePower = Math.max(centrePower, power[side.ordinal()]);
        }
        if (centrePower <= 0) return;
        TextureAtlasSprite sprite = RenderUtil.sprite(POWER_FLOW);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (Direction side : Direction.values()) {
            if (!pipe.isConnected(side) || power[side.ordinal()] <= 0) continue;
            renderSidePower(poseStack, consumer, sprite, side, power[side.ordinal()], centrePower, flow.displayFlow(side, partialTicks));
        }
        float radius = (float) Math.min(0.248, 0.248 * centrePower);
        powerBox(poseStack, consumer, sprite, null, 0.5 - radius, 0.5 - radius, 0.5 - radius, 0.5 + radius, 0.5 + radius, 0.5 + radius, 0, 0, 0);
    }

    private static void renderSidePower(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, Direction side,
            double power, double centrePower, double offset) {
        double radius = Math.min(0.248, 0.248 * power);
        double centreRadius = 0.252 - 0.248 * centrePower;
        double[] centre = offset(side, 0.25 + 0.125 - centreRadius / 2);
        double[] r = {radius, radius, radius};
        r[side.getAxis().ordinal()] = 0.125 + centreRadius / 2;
        double shift = offset * side.getAxisDirection().getStep() / 32;
        powerBox(poseStack, consumer, sprite, side.getOpposite(), centre[0] - r[0], centre[1] - r[1], centre[2] - r[2],
                centre[0] + r[0], centre[1] + r[1], centre[2] + r[2],
                side.getStepX() * shift, side.getStepY() * shift, side.getStepZ() * shift);
    }

    /** Fullbright power beam whose texture scrolls with the flow, as upstream maps half-scale box UVs. */
    private static void powerBox(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, Direction skip,
            double x1, double y1, double z1, double x2, double y2, double z2, double du, double dv, double dw) {
        float[] box = {(float) x1 * 16, (float) y1 * 16, (float) z1 * 16, (float) x2 * 16, (float) y2 * 16, (float) z2 * 16};
        for (Direction face : Direction.values()) {
            if (face == skip) continue;
            float[] uv = RenderUtil.autoUv(face, (float) (x1 + du) * 8, (float) (y1 + dv) * 8, (float) (z1 + dw) * 8,
                    (float) (x2 + du) * 8, (float) (y2 + dv) * 8, (float) (z2 + dw) * 8);
            wrap(uv, 0, 2);
            wrap(uv, 1, 3);
            RenderUtil.face(poseStack, consumer, sprite, face, uv, box[0], box[1], box[2], box[3], box[4], box[5], 0xFFFFFFFF, RenderUtil.FULL_BRIGHT);
        }
    }

    /** Keeps a scrolled UV span inside the sprite; the power texture repeats every 8 pixels. */
    private static void wrap(float[] uv, int a, int b) {
        float shift = (float) Math.floor(Math.min(uv[a], uv[b]) / 8) * 8;
        uv[a] -= shift;
        uv[b] -= shift;
    }
}
