package com.technovision.advancedgenetics.events;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the "Teleport" and "Dragon's Breath" key bindings.
 */
public class KeyInputEvents {

    public static final String KEY_CATEGORY_GENETICS = "key."+ AdvancedGenetics.MOD_ID+".category";
    public static final String KEY_TELEPORT = "key."+ AdvancedGenetics.MOD_ID+".teleport";
    public static final String KEY_DRAGONS_BREATH = "key."+ AdvancedGenetics.MOD_ID+".dragons_breath";

    public static final String TELEPORT = "teleport";
    public static final String DRAGONS_BREATH = "dragons_breath";

    public static void onKeyPressed(ServerPlayer player, String geneName) {
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(player);
        if (geneName.equals(TELEPORT) && component.hasGene(Genes.TELEPORT)) {
            if (!component.isOnCooldown(TELEPORT)) {
                Vec3 v3 = player.getLookAngle().scale(6).add(player.getEyePosition());
                player.randomTeleport(v3.x(), v3.y(), v3.z(), true);
                component.addCooldown(TELEPORT, 1);
            } else {
                sendCooldownMessage(player, Genes.TELEPORT);
            }
        }
        else if (geneName.equals(DRAGONS_BREATH) && component.hasGene(Genes.DRAGONS_BREATH)) {
            if (!component.isOnCooldown(DRAGONS_BREATH)) {
                Vec3 v3 = player.getViewVector(1);
                DragonFireball fireballEntity = new DragonFireball(player.getLevel(), player, v3.x(), v3.y(), v3.z());
                fireballEntity.setPos(player.getX(), player.getY() + 1.5, player.getZ());
                player.getLevel().addFreshEntity(fireballEntity);
                player.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
                component.addCooldown(DRAGONS_BREATH, 15);
            } else {
                sendCooldownMessage(player, Genes.DRAGONS_BREATH);
            }
        }
    }

    private static void sendCooldownMessage(ServerPlayer player, Genes gene) {
        player.sendSystemMessage(Component.translatable("message."+AdvancedGenetics.MOD_ID+".cooldown", "§7"+gene.getName()+"§f"));
    }
}
