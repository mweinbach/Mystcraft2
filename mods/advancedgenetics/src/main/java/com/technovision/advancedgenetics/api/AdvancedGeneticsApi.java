package com.technovision.advancedgenetics.api;

import com.technovision.advancedgenetics.api.component.EntityGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.player.Player;

/**
 * Entry points for other mods, the same on Forge and Fabric. Upstream exposed player genetics as the
 * Cardinal Components key {@code ComponentRegistry.PLAYER_GENETICS}; this port does not use Cardinal
 * Components, so that key no longer exists. Look genetics up here instead.
 */
public final class AdvancedGeneticsApi {

    private AdvancedGeneticsApi() { }

    /**
     * The player's genetics. Changes made on the server are saved with the player and synced to
     * their client; on the client this is the copy the server last synced.
     */
    public static EntityGeneticsComponent genetics(Player player) {
        return ComponentRegistry.getGenetics(player);
    }
}
