/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.event.RenderArmEvent;
import ports.projecte.lib.common.MinecraftForge;

/**
 * Posts the first-person arm event before each hand. Canceling it skips vanilla's arm, which is where the MekaSuit
 * draws its own.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void projecte$rightArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new RenderArmEvent(poseStack, buffer, packedLight, player, HumanoidArm.RIGHT))) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void projecte$leftArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new RenderArmEvent(poseStack, buffer, packedLight, player, HumanoidArm.LEFT))) {
            ci.cancel();
        }
    }
}
