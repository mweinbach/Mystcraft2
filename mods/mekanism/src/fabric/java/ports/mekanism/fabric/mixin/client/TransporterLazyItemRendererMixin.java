/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.client.TransporterItems;

/**
 * Fabric draws the ground plane on a depth-tested cutout layer and flushes it before the colored glass. Forge keeps
 * the upstream item renderer.
 */
@Mixin(targets = "mekanism.client.render.transmitter.RenderLogisticalTransporter$LazyItemRenderer")
public abstract class TransporterLazyItemRendererMixin {

    @Shadow
    @Nullable
    private ItemEntity entityItem;

    @Inject(method = "init", at = @At("RETURN"))
    private void mekanism$faceCamera(Level world, BlockPos pos, CallbackInfo ci) {
        if (this.entityItem != null) {
            TransporterItems.faceCamera(this.entityItem);
        }
    }

    @Redirect(method = "renderAsStack", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void mekanism$cutoutItem(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partialTick, PoseStack pose,
          MultiBufferSource buffer, int light) {
        renderer.render(entity, yaw, partialTick, pose, TransporterItems.cutout(buffer), TransporterItems.fullBright());
        TransporterItems.flush(buffer);
    }
}
