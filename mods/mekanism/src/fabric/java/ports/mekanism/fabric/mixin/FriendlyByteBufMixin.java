/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import ports.mekanism.lib.common.extensions.IForgeFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin implements IForgeFriendlyByteBuf {
}
