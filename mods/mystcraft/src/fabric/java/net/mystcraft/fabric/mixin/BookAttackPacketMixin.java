package net.mystcraft.fabric.mixin;

import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.mystcraft.minecraft.entity.BookAttackPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BookAttackPacketMixin {
    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void mystcraft$attackBook(ServerboundInteractPacket packet, CallbackInfo callback) {
        ServerGamePacketListenerImpl listener = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = listener.player;
        PacketUtils.ensureRunningOnSameThread(packet, listener, player.getLevel());
        if (BookAttackPackets.handle(player, packet, (attacker, target) -> attacker.distanceToSqr(target) < 36)) callback.cancel();
    }
}
