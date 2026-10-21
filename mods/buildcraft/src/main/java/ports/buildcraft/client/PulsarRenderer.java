package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.plug.PluggablePulsar;
import ports.buildcraft.transport.pluggable.PipePluggable;

/**
 * Upstream {@code plugs/pulsar_static.json} and {@code plugs/pulsar_dynamic.json}: the body, the piston
 * (extended by the stage formula, full bright while on) and eight lights. An auto light is red and a manual
 * light green, each full bright while that mode is on and dark otherwise.
 */
final class PulsarRenderer {
    static final ResourceLocation STATIC = BuildCraft.id("block/pipe/pulsar_static");
    static final ResourceLocation ON = BuildCraft.id("block/pipe/pulsar_dynamic_on");
    static final ResourceLocation OFF = BuildCraft.id("block/pipe/pulsar_dynamic_off");
    private static final ResourceLocation LED = new ResourceLocation("block/white_concrete");
    /** The item's lights, matching {@code setModelVariablesForItem}: stage 0 and both modes off. */
    static final int ITEM_LED_COLOUR = 0xFF222222;

    private static final float[] BODY_SIDE = {2, 5, 5, 11};
    private static final float[] BODY_END = {5, 5, 11, 11};
    private static final float[] PISTON_ON_SIDE = {4, 6, 6, 10};
    private static final float[] PISTON_OFF_SIDE = {5.8F, 6, 6, 10};
    private static final float[] PISTON_END = {6, 6, 10, 10};
    private static final float[] LED_UV = {0, 0, 16, 16};
    private static final int AUTO_ON = 0xFFFF9999;
    private static final int MANUAL_ON = 0xFF99FF99;

    private record Led(float x1, float y1, float z1, float x2, float y2, float z2, boolean auto) {}

    private static final List<Led> LEDS = List.of(
            new Led(2.5F, 6.5F, 4.9F, 3.5F, 7.5F, 5, true),
            new Led(2.5F, 8.5F, 4.9F, 3.5F, 9.5F, 5, false),
            new Led(2.5F, 8.5F, 11, 3.5F, 9.5F, 11.1F, true),
            new Led(2.5F, 6.5F, 11, 3.5F, 7.5F, 11.1F, false),
            new Led(2.5F, 4.9F, 8.5F, 3.5F, 5, 9.5F, true),
            new Led(2.5F, 4.9F, 6.5F, 3.5F, 5, 7.5F, false),
            new Led(2.5F, 11, 6.5F, 3.5F, 11.1F, 7.5F, true),
            new Led(2.5F, 11, 8.5F, 3.5F, 11.1F, 9.5F, false));

    private PulsarRenderer() {}

    static List<ResourceLocation> sprites() {
        return List.of(STATIC, ON, OFF);
    }

    static void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        PluggablePulsar pulsar = (PluggablePulsar) plug;
        float stage = pulsar.stage(partialTicks);
        float mirrored = 2 * (stage > 0.5F ? 1 - stage : stage);
        float pos = (1 - mirrored) * 2 - 0.001F;
        boolean on = pulsar.clientPulsing();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        RenderUtil.rotateFromWestAny(poseStack, plug.side);
        box(poseStack, consumer, STATIC, BODY_SIDE, BODY_END, 2, 5, 5, 4.01F, 11, 11, 0xFFFFFFFF, light);
        float[] side = on ? PISTON_ON_SIDE : PISTON_OFF_SIDE;
        box(poseStack, consumer, on ? ON : OFF, side, PISTON_END, pos, 6, 6, pos + 2, 10, 10, 0xFFFFFFFF, on ? RenderUtil.FULL_BRIGHT : light);
        for (Led led : LEDS) {
            boolean lit = led.auto ? pulsar.autoEnabled() : pulsar.manuallyEnabled();
            int colour = lit ? (led.auto ? AUTO_ON : MANUAL_ON) : ITEM_LED_COLOUR;
            box(poseStack, consumer, LED, LED_UV, LED_UV, led.x1, led.y1, led.z1, led.x2, led.y2, led.z2, colour, lit ? RenderUtil.FULL_BRIGHT : light);
        }
        poseStack.popPose();
    }

    private static void box(PoseStack poseStack, VertexConsumer consumer, ResourceLocation texture, float[] side, float[] end,
            float x1, float y1, float z1, float x2, float y2, float z2, int argb, int light) {
        RenderUtil.box(poseStack, consumer, RenderUtil.sprite(texture),
                (face, px1, py1, pz1, px2, py2, pz2) -> face.getAxis() == Direction.Axis.X ? end : side,
                x1, y1, z1, x2, y2, z2, argb, light);
    }
}
