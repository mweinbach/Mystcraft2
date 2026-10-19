package com.technovision.advancedgenetics.events;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Attaches, persists, ticks and syncs the player genetics capability, matching the
 * Cardinal Components ALWAYS_COPY, server-ticking and auto-sync behavior upstream relied on.
 */
public class ComponentEvents {

    public static void registerEvents() {
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, ComponentEvents::onAttachCapabilities);
        MinecraftForge.EVENT_BUS.addListener(ComponentEvents::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(ComponentEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> sync(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> sync(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> sync(event.getEntity()));
    }

    private static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            ComponentRegistry.Provider provider = new ComponentRegistry.Provider(player);
            event.addCapability(ComponentRegistry.PLAYER_GENETICS_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player clone = event.getEntity();
        original.reviveCaps();
        try {
            PlayerGeneticsComponent genetics = ComponentRegistry.getGenetics(clone);
            genetics.copyFrom(ComponentRegistry.getGenetics(original));

            // Handles "Keep Inventory" gene; PlayerMixin stops the death drops
            if (event.isWasDeath() && genetics.hasGene(Genes.KEEP_INVENTORY)) {
                clone.getInventory().replaceWith(original.getInventory());
            }
        } finally {
            original.invalidateCaps();
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            ComponentRegistry.getGenetics(event.player).serverTick();
        }
    }

    private static void sync(Player player) {
        ComponentRegistry.getGenetics(player).sync();
    }
}
