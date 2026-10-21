package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.PipePluggable;
import ports.buildcraft.transport.pluggable.PluggableDefinition;

/**
 * Upstream's pluggable bakers, drawn by the pipe's block entity renderer instead of being baked into
 * the pipe model. Static models are upstream's {@code models/plugs} elements, built facing west and
 * turned to the pluggable's face as {@code PlugBakerSimple} does.
 */
public final class PluggableRenderers {
    @FunctionalInterface
    public interface Renderer {
        void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light);
    }

    /**
     * One element of an upstream plug model, in pixels: {@code endUv} textures the faces across the
     * x axis (west and east) and {@code sideUv} the other four.
     */
    public record Element(float x1, float y1, float z1, float x2, float y2, float z2, float[] sideUv, float[] endUv) {}

    public static final ResourceLocation PLUG = BuildCraft.id("block/pipe/plug");
    public static final ResourceLocation POWER_ADAPTER = BuildCraft.id("block/pipe/power_adapter");

    private static final Map<PluggableDefinition, Renderer> RENDERERS = new HashMap<>();
    private static final List<ResourceLocation> SPRITES = new ArrayList<>();

    static {
        register(BCTransport.BLOCKER, PLUG, new Element(2, 4, 4, 4.01F, 12, 12, new float[] {2, 4, 4, 12}, new float[] {4, 4, 12, 12}));
        register(BCTransport.POWER_ADAPTOR, POWER_ADAPTER,
                new Element(2, 4, 4, 4.01F, 12, 12, new float[] {2, 4, 4, 12}, new float[] {4, 4, 12, 12}),
                new Element(0, 3, 3, 2, 13, 13, new float[] {0, 3, 2, 13}, new float[] {3, 3, 13, 13}));
        register(BCSilicon.GATE, GateRenderer::render);
        SPRITES.addAll(GateRenderer.sprites());
        register(BCSilicon.LENS, LensRenderer::render);
        register(BCSilicon.TIMER, BuildCraft.id("block/pipe/timer"),
                new Element(2, 5, 5, 4.01F, 11, 11, new float[] {3, 5, 5, 11}, new float[] {5, 5, 11, 11}));
        register(BCSilicon.LIGHT_SENSOR, BuildCraft.id("block/pipe/light_sensor"),
                new Element(2, 5, 5, 4.01F, 11, 11, new float[] {3, 5, 5, 11}, new float[] {5, 5, 11, 11}));
        register(BCSilicon.PULSAR, PulsarRenderer::render);
        SPRITES.addAll(PulsarRenderer.sprites());
    }

    private PluggableRenderers() {}

    public static void register(PluggableDefinition definition, Renderer renderer) {
        RENDERERS.put(definition, renderer);
    }

    /** Registers a static model of {@code elements} drawn with one texture. */
    public static void register(PluggableDefinition definition, ResourceLocation texture, Element... elements) {
        SPRITES.add(texture);
        register(definition, (plug, partialTicks, poseStack, buffers, light) -> {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
            poseStack.pushPose();
            RenderUtil.rotateFromWestAny(poseStack, plug.side);
            for (Element element : elements) {
                RenderUtil.box(poseStack, consumer, RenderUtil.sprite(texture),
                        (face, x1, y1, z1, x2, y2, z2) -> face.getAxis() == Direction.Axis.X ? element.endUv : element.sideUv,
                        element.x1, element.y1, element.z1, element.x2, element.y2, element.z2, 0xFFFFFFFF, light);
            }
            poseStack.popPose();
        });
    }

    public static List<ResourceLocation> sprites() {
        return SPRITES;
    }

    static void render(PipeBlockEntity pipe, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        for (Direction face : Direction.values()) {
            PipePluggable plug = pipe.getPluggable(face);
            if (plug == null) continue;
            Renderer renderer = RENDERERS.get(plug.definition);
            if (renderer != null) renderer.render(plug, partialTicks, poseStack, buffers, light);
        }
    }
}
