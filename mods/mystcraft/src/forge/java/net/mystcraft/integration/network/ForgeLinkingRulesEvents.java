package net.mystcraft.integration.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mystcraft.minecraft.linking.LinkingRules;

/** Native Forge lifecycle adapters; identity persistence/clone copying lives in the shared-contract mixin. */
@Mod.EventBusSubscriber(modid = "mystcraft", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeLinkingRulesEvents {
    private ForgeLinkingRulesEvents() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) onServer(player, () -> LinkingRules.onLogin(player));
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) onServer(player, () -> LinkingRules.onDimensionChanged(player));
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) onServer(player, () -> LinkingRules.onRespawn(player));
    }
    private static void onServer(ServerPlayer player, Runnable action) {
        if (player.server.isSameThread()) action.run(); else player.server.execute(action);
    }
}
