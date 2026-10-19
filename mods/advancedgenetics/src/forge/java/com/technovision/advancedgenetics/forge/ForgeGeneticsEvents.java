package com.technovision.advancedgenetics.forge;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.events.GeneticsEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Routes Forge gameplay events to the shared gene handlers. */
final class ForgeGeneticsEvents {

    private ForgeGeneticsEvents() { }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onPlayerDeath);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onAttackEntity);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(ForgeGeneticsEvents::onPlayerAttacked);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AdvancedGenetics.registerCommands(event.getDispatcher()));
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (GeneticsEvents.onRightClickBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (GeneticsEvents.onEntityInteract(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GeneticsEvents.onPlayerDeath(player);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (GeneticsEvents.onAttackEntity(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (GeneticsEvents.onRightClickItem(event.getEntity(), event.getLevel(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        GeneticsEvents.onEntityJoinLevel(event.getEntity(), event.getLevel());
    }

    private static void onPlayerAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && GeneticsEvents.onPlayerAttacked(player, event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }
}
