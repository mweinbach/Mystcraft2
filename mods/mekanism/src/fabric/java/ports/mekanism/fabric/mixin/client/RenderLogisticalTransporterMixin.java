/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.client.render.transmitter.RenderLogisticalTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ports.mekanism.fabric.client.TransporterItems;

/**
 * The first translate in the transporter renderer is the moving stack. Forge keeps that call. Fabric moves the
 * sprite to just in front of the cutout shell so the shell's depth does not hide it, without leaving the block.
 */
@Mixin(RenderLogisticalTransporter.class)
public abstract class RenderLogisticalTransporterMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
          target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void mekanism$clearOfShell(PoseStack pose, double x, double y, double z) {
        TransporterItems.translateClearOfShell(pose, x, y, z);
    }
}
