/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.event.RenderLivingEvent;
import ports.mekanism.lib.common.MinecraftForge;

/**
 * Posts living-entity render events around the same method Forge patches. A canceled pre event skips the entity and
 * does not post the matching post event. The stack keeps a nested render's cancel from swallowing the outer post.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Unique
    private final Deque<Boolean> mekanism$renderCanceled = new ArrayDeque<>();

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
          at = @At("HEAD"), cancellable = true)
    private void mekanism$renderLivingPre(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
          CallbackInfo ci) {
        boolean canceled = MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Pre<>(entity, renderer(), partialTick, poseStack, buffer, packedLight));
        this.mekanism$renderCanceled.push(canceled);
        if (canceled) {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
          at = @At("RETURN"))
    private void mekanism$renderLivingPost(LivingEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
          CallbackInfo ci) {
        if (this.mekanism$renderCanceled.isEmpty() || this.mekanism$renderCanceled.pop()) {
            return;
        }
        MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Post<>(entity, renderer(), partialTick, poseStack, buffer, packedLight));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <T extends LivingEntity, M extends EntityModel<T>> LivingEntityRenderer<T, M> renderer() {
        return (LivingEntityRenderer<T, M>) (Object) this;
    }
}
