/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import ports.projecte.lib.client.extensions.IForgeVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VertexConsumer.class)
public interface VertexConsumerMixin extends IForgeVertexConsumer {
}
