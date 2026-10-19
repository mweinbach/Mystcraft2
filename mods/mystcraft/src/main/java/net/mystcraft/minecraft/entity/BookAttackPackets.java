package net.mystcraft.minecraft.entity;

import java.util.function.BiPredicate;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Narrow exception to vanilla's blanket rejection of attacks on dropped item entities. */
public final class BookAttackPackets {
    private BookAttackPackets() {}
    public static boolean handle(ServerPlayer player, ServerboundInteractPacket packet,
                                 BiPredicate<ServerPlayer, Entity> inReach) {
        Entity target = packet.getTarget(player.getLevel());
        if (!(target instanceof LinkBookEntity)) return false;
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) {}
            @Override public void onInteraction(InteractionHand hand, Vec3 location) {}
            @Override public void onAttack() { attack[0] = true; }
        });
        if (!attack[0]) return false;
        player.resetLastActionTime();
        player.setShiftKeyDown(packet.isUsingSecondaryAction());
        if (!player.isSpectator() && player.isAlive() && target.isAlive()
                && player.getLevel().getWorldBorder().isWithinBounds(target.blockPosition())
                && inReach.test(player, target)) player.attack(target);
        // Even denied book attacks must not fall through to vanilla's invalid-item kick.
        return true;
    }
}
