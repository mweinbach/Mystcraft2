package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.transport.pluggable.PipePluggable;

/**
 * Upstream's {@code plugs/gate.json} and {@code plugs/gate_dynamic.json}: the material body, the logic
 * light (not on clay brick gates), four modifier studs, and the on/off light, which glows at full
 * brightness while the gate is on.
 */
final class GateRenderer {
    static final ResourceLocation AND = BuildCraft.id("block/gate/and");
    static final ResourceLocation OR = BuildCraft.id("block/gate/or");
    static final ResourceLocation ON = BuildCraft.id("block/gate/on");
    static final ResourceLocation OFF = BuildCraft.id("block/gate/off");

    private static final float[] SIDE_UV = {2, 5, 4, 11};
    private static final float[] END_UV = {5, 5, 11, 11};
    private static final PluggableRenderers.Element BODY = element(2, 5, 5, 4.01F, 11, 11);
    private static final PluggableRenderers.Element LOGIC = element(1.8F, 7, 7, 4.2F, 9, 9);
    private static final PluggableRenderers.Element LIGHT = element(1.9F, 6, 6, 4.1F, 10, 10);
    private static final List<PluggableRenderers.Element> MODIFIERS = List.of(
            element(1.8F, 5.5F, 5.5F, 4.2F, 6.5F, 6.5F),
            element(1.8F, 9.5F, 5.5F, 4.2F, 10.5F, 6.5F),
            element(1.8F, 5.5F, 9.5F, 4.2F, 6.5F, 10.5F),
            element(1.8F, 9.5F, 9.5F, 4.2F, 10.5F, 10.5F));

    private GateRenderer() {}

    private static PluggableRenderers.Element element(float x1, float y1, float z1, float x2, float y2, float z2) {
        return new PluggableRenderers.Element(x1, y1, z1, x2, y2, z2, SIDE_UV, END_UV);
    }

    static List<ResourceLocation> sprites() {
        return List.of(AND, OR, ON, OFF);
    }

    private static ResourceLocation material(EnumGateMaterial material) {
        return switch (material) {
            case CLAY_BRICK -> new ResourceLocation("block/bricks");
            case IRON -> new ResourceLocation("block/iron_block");
            case NETHER_BRICK -> new ResourceLocation("block/nether_bricks");
            case GOLD -> new ResourceLocation("block/gold_block");
        };
    }

    @Nullable
    private static ResourceLocation modifier(EnumGateModifier modifier) {
        return switch (modifier) {
            case NO_MODIFIER -> null;
            case LAPIS -> new ResourceLocation("block/lapis_block");
            case QUARTZ -> new ResourceLocation("block/quartz_block_top");
            case DIAMOND -> new ResourceLocation("block/diamond_block");
        };
    }

    static void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        GateLogic logic = ((PluggableGate) plug).logic();
        GateVariant variant = logic.variant;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        RenderUtil.rotateFromWestAny(poseStack, plug.side);
        box(poseStack, consumer, BODY, material(variant.material()), light);
        if (variant.material() != EnumGateMaterial.CLAY_BRICK) {
            box(poseStack, consumer, LOGIC, variant.logic() == EnumGateLogic.AND ? AND : OR, light);
        }
        ResourceLocation modifier = modifier(variant.modifier());
        if (modifier != null) {
            for (PluggableRenderers.Element stud : MODIFIERS) box(poseStack, consumer, stud, modifier, light);
        }
        box(poseStack, consumer, LIGHT, logic.isOn ? ON : OFF, logic.isOn ? RenderUtil.FULL_BRIGHT : light);
        poseStack.popPose();
    }

    private static void box(PoseStack poseStack, VertexConsumer consumer, PluggableRenderers.Element element, ResourceLocation texture, int light) {
        RenderUtil.box(poseStack, consumer, RenderUtil.sprite(texture),
                (face, x1, y1, z1, x2, y2, z2) -> face.getAxis() == Direction.Axis.X ? element.endUv() : element.sideUv(),
                element.x1(), element.y1(), element.z1(), element.x2(), element.y2(), element.z2(), 0xFFFFFFFF, light);
    }
}
