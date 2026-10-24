/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.ForgeHooksClient;

/**
 * Recipe sync posts {@code RecipesUpdatedEvent} after the
 * client recipe book is rebuilt, which is where Forge posts it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void projecte$recipesUpdated(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        ForgeHooksClient.onRecipesUpdated(((ClientPacketListener) (Object) this).getRecipeManager());
    }
}
