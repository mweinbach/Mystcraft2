/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import mekanism.common.Mekanism;
import mekanism.common.item.ItemRobit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.extensions.IForgeVertexConsumer;
import ports.mekanism.lib.client.extensions.common.IClientItemExtensions;

/**
 * Renders built-in item models with the item's own renderer, as Forge does. An item registered with Fabric's
 * {@link BuiltinItemRendererRegistry} is left on the vanilla renderer: Fabric's mixin on that renderer dispatches the
 * registry. The wind generator item and the Mekanism Tools shields use that registry.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Shadow
    public abstract void renderModelLists(BakedModel model, ItemStack stack, int light, int overlay, PoseStack poseStack, VertexConsumer consumer);

    /**
     * Upstream 10.3.9 leaves {@code #robit} unmapped on purpose ({@code RobitModel} drops that missing-texture error)
     * and stitches the skin only into {@code mekanism:textures/atlas/robit.png}. The item quads and {@code RenderRobit}
     * share that atlas, and {@code getRenderTypes} is {@code entityCutoutNoCull} of it. This is not a flat texture that
     * failed to join the block atlas: stitching it there would give the item different UVs from the entity and the
     * skin swap. Vanilla's item pass, and Fabric's renderer, bind the block atlas only. Forge binds each render type.
     * This is that bind, and only for this item.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mekanism$robitAtlas(ItemStack stack, ItemTransforms.TransformType transformType, boolean leftHand, PoseStack poseStack,
          MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (!(stack.getItem() instanceof ItemRobit)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity holder = minecraft.player;
        BakedModel resolved = model.getOverrides().resolve(model, stack, minecraft.level, holder, 0);
        if (resolved == null) {
            resolved = model;
        }
        poseStack.pushPose();
        resolved.getTransforms().getTransform(transformType).apply(leftHand, poseStack);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        boolean foil = stack.hasFoil();
        // A Robit is not a block item, so vanilla always uses the direct buffer for it.
        for (BakedModel pass : resolved.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffers, renderType, true, foil);
                renderModelLists(pass, stack, light, overlay, poseStack, consumer);
            }
        }
        poseStack.popPose();
        ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void mekanism$customRenderer(BlockEntityWithoutLevelRenderer vanilla, ItemStack stack, ItemTransforms.TransformType transformType, PoseStack poseStack,
          MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        if (BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem()) != null) {
            original.call(vanilla, stack, transformType, poseStack, buffers, light, overlay);
            return;
        }
        original.call(IClientItemExtensions.of(stack).getCustomRenderer(), stack, transformType, poseStack, buffers, light, overlay);
    }

    @Shadow
    @Final
    private ItemColors itemColors;

    /**
     * Forge's item quad pass keeps the light and colour baked into the quad. Vanilla's pass replaces both, so an
     * emissive face on an item (an energy cube's corners, a charged machine face) stays dark. This runs at the head of
     * the pass, for Mekanism's own items only, and draws their quads the Forge way. It does not wrap the
     * {@code putBulkData} call inside the method: Sodium (in Tekxit 4) overwrites {@code renderQuadList}, so that call
     * is gone and a required wrap crashes the game at the first item render. Other items keep whatever renderer the
     * pack uses.
     */
    @Inject(method = "renderQuadList", at = @At("HEAD"), cancellable = true)
    private void mekanism$emissiveItemQuads(PoseStack poseStack, VertexConsumer consumer, List<BakedQuad> quads, ItemStack stack, int light,
          int overlay, CallbackInfo ci) {
        if (stack.isEmpty() || !Registry.ITEM.getKey(stack.getItem()).getNamespace().startsWith(Mekanism.MODID)) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        for (BakedQuad quad : quads) {
            int color = quad.isTinted() ? itemColors.getColor(stack, quad.getTintIndex()) : -1;
            float red = (color >> 16 & 255) / 255.0F;
            float green = (color >> 8 & 255) / 255.0F;
            float blue = (color & 255) / 255.0F;
            ((IForgeVertexConsumer) consumer).mekanism$putBulkData(pose, quad, red, green, blue, 1.0F, light, overlay, true);
        }
        ci.cancel();
    }
}
