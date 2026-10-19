package com.technovision.advancedgenetics.events;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.player.Player;

/**
 * Copies, ticks and syncs player genetics, matching the Cardinal Components ALWAYS_COPY,
 * server-ticking and auto-sync behavior upstream relied on. Loader adapters call these hooks.
 */
public class ComponentEvents {

    /** Called when a player respawns or returns from the End, after the clone is created. */
    public static void copyOnRespawn(Player original, Player clone, boolean wasDeath) {
        PlayerGeneticsComponent genetics = ComponentRegistry.getGenetics(clone);
        genetics.copyFrom(ComponentRegistry.getGenetics(original));

        // Handles "Keep Inventory" gene; PlayerMixin stops the death drops
        if (wasDeath && genetics.hasGene(Genes.KEEP_INVENTORY)) {
            clone.getInventory().replaceWith(original.getInventory());
        }
    }

    /** Called at the end of each server-side player tick. */
    public static void serverTick(Player player) {
        ComponentRegistry.getGenetics(player).serverTick();
    }

    /** Called on login, respawn and dimension change. */
    public static void sync(Player player) {
        ComponentRegistry.getGenetics(player).sync();
    }
}
