package com.technovision.advancedgenetics.network;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.platform.Platform;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packets replacing Cardinal Components auto-sync and upstream's Fabric key packets. Each loader
 * carries them on its own networking API.
 */
public class ModNetwork {

    public static final ResourceLocation SYNC_GENETICS = new ResourceLocation(AdvancedGenetics.MOD_ID, "sync_genetics");
    public static final ResourceLocation KEY_PRESSED = new ResourceLocation(AdvancedGenetics.MOD_ID, "key_pressed");

    /**
     * Sends a player's genes to their own client, which needs them for climbing,
     * web walking, mob sight and key bindings.
     */
    public static void syncGenetics(ServerPlayer player) {
        if (player.connection == null || Platform.isFakePlayer(player)) return;
        Platform.sendToPlayer(player, new SyncGeneticsPacket(ComponentRegistry.getGenetics(player).serializeNBT()));
    }
}
