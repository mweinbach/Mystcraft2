/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.fabric.FabricGameEvents;
import ports.mekanism.lib.event.level.LevelEvent;

/**
 * Forge posts {@code LevelEvent.Load} at the end of the client level constructor. That is the level
 * {@code Mekanism.onWorldLoad} stores for player-state sounds.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mekanism$levelLoad(CallbackInfo ci) {
        FabricGameEvents.post(new LevelEvent.Load((ClientLevel) (Object) this));
    }
}
