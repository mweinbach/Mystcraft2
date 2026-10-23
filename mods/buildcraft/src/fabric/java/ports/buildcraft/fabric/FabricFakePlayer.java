package ports.buildcraft.fabric;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * A server player with no client for Fabric, which has no fake player API for 1.19.2. Like Forge's
 * {@code FakePlayer} it is never added to the level or the player list, takes no damage, earns no stats and
 * drops every packet sent to it. One instance is kept per loaded level and profile, as Forge's
 * {@code FakePlayerFactory}.
 */
final class FabricFakePlayer extends ServerPlayer {
    private static final Map<ServerLevel, Map<UUID, FabricFakePlayer>> PLAYERS = new HashMap<>();

    private FabricFakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, null);
        new NullPacketListener(level.getServer(), this);
    }

    static synchronized ServerPlayer get(ServerLevel level, GameProfile profile) {
        return PLAYERS.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(profile.getId(), id -> new FabricFakePlayer(level, profile));
    }

    /** Called when {@code level} unloads, so that its players do not keep it alive. */
    static synchronized void unload(ServerLevel level) {
        PLAYERS.remove(level);
    }

    @Override public void displayClientMessage(Component message, boolean actionBar) {}
    @Override public void awardStat(Stat<?> stat, int amount) {}
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean canHarmPlayer(Player player) { return false; }
    @Override public void die(DamageSource source) {}
    @Override public void tick() {}
    @Override public void updateOptions(ServerboundClientInformationPacket packet) {}

    /** Takes the player's connection so that code sending it packets, such as opening a menu, does nothing. */
    private static final class NullPacketListener extends ServerGamePacketListenerImpl {
        NullPacketListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player);
        }

        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }
}
