package com.technovision.advancedgenetics.fabric;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.events.ComponentEvents;
import com.technovision.advancedgenetics.events.GeneticsEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Routes Fabric API events to the shared handlers. Hooks Fabric API places differently from
 * Forge's events are mixins instead ({@code PlayerFabricMixin}, {@code ServerPlayerFabricMixin}).
 */
final class FabricEvents {

    private FabricEvents() { }

    static void register() {
        // Forge's RightClickBlock and UseBlockCallback both fire at the start of useItemOn.
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                GeneticsEvents.onRightClickBlock(player, level, hand, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        // UseItemCallback fires before vanilla's spectator and cooldown checks; Forge's RightClickItem fires after them.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isSpectator() || player.getCooldowns().isOnCooldown(stack.getItem())) return InteractionResultHolder.pass(stack);
            return GeneticsEvents.onRightClickItem(player, level, hand) ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
        });
        ServerEntityEvents.ENTITY_LOAD.register(GeneticsEvents::onEntityJoinLevel);

        ServerPlayerEvents.COPY_FROM.register((original, clone, alive) -> ComponentEvents.copyOnRespawn(original, clone, !alive));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ComponentEvents.sync(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, clone, alive) -> ComponentEvents.sync(clone));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> ComponentEvents.sync(player));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> AdvancedGenetics.registerCommands(dispatcher));
    }
}
