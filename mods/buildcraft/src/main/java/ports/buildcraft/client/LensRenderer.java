package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.plug.LensPluggable;
import ports.buildcraft.silicon.plug.LensVariant;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.transport.pluggable.PipePluggable;

/**
 * Upstream {@code PlugBakerLens}: the lens's item model, which is upstream's {@code plugs/lens} model built facing
 * west, turned to the lens's face. The frame is cutout and the tinted pane translucent.
 */
public final class LensRenderer {
    /**
     * Upstream's clear lens showed 1.12's water texture untinted, which was already blue; 1.19's is grey and tinted
     * by biome, so it takes the default water colour.
     */
    private static final int CLEAR_TINT = 0x3F76E4;
    private static final RandomSource RANDOM = RandomSource.create();

    private LensRenderer() {}

    /** Upstream {@code convertColourToArgb}: the pane's colour. */
    public static int tint(LensVariant variant) {
        return variant.colour() == null ? CLEAR_TINT : ColourText.lightHex(variant.colour());
    }

    static void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(plug instanceof LensPluggable lens)) return;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(BCSilicon.lensItem(lens.variant()));
        List<BakedQuad> quads = model.getQuads(null, null, RANDOM);
        int tint = tint(lens.variant());
        float red = (tint >> 16 & 0xFF) / 255F, green = (tint >> 8 & 0xFF) / 255F, blue = (tint & 0xFF) / 255F;
        poseStack.pushPose();
        RenderUtil.rotateFromWestAny(poseStack, plug.side);
        PoseStack.Pose pose = poseStack.last();
        // Buffers are filled one after the other: fetching another render type can end the previous one's batch.
        VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        for (BakedQuad quad : quads) {
            if (!quad.isTinted()) cutout.putBulkData(pose, quad, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        }
        VertexConsumer translucent = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        for (BakedQuad quad : quads) {
            if (quad.isTinted()) translucent.putBulkData(pose, quad, red, green, blue, light, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }
}
