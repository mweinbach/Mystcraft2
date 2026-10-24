package ports.gridwright.fabric;

import com.mojang.authlib.GameProfile;
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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gridwright's stand-in for Forge's {@code FakePlayer} (Fabric 1.19.2 has none): a server player that is
 * never added to a level or the player list, cannot be hurt, earns nothing and drops every packet sent
 * to it. Spikes credit kills to the shared {@code [Gridwright]} player; machines act through one carrying
 * their owner's profile, so vanilla interaction code fires Fabric's events ({@code UseBlockCallback},
 * {@code PlayerBlockBreakEvents}) as for that player and claim mods see the owner. One per profile per
 * loaded level, forgotten when the level unloads.
 */
final class FabricFakePlayer extends ServerPlayer {

    private static final GameProfile PROFILE =
            new GameProfile(UUID.fromString("5f0c3e1a-8d2b-4c6e-9a47-6b1d2e3f4a5b"), "[Gridwright]");
    private static final Map<ServerLevel, Map<GameProfile, FabricFakePlayer>> PLAYERS = new HashMap<>();

    private FabricFakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, null);
        new SilentListener(level.getServer(), this);
    }

    static ServerPlayer get(ServerLevel level) {
        return get(level, PROFILE);
    }

    static synchronized ServerPlayer get(ServerLevel level, GameProfile profile) {
        return PLAYERS.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(profile, key -> create(level, key));
    }

    static synchronized void unload(ServerLevel level) {
        PLAYERS.remove(level);
    }

    private static FabricFakePlayer create(ServerLevel level, GameProfile profile) {
        FabricFakePlayer player = new FabricFakePlayer(level, profile);
        // Constructing any ServerPlayer points the owner's advancement tracker at it; give it back to
        // the real player if they are online (Forge's PlayerList skips fake players the same way).
        ServerPlayer real = level.getServer().getPlayerList().getPlayer(profile.getId());
        if (real != null) level.getServer().getPlayerList().getPlayerAdvancements(real);
        return player;
    }

    @Override public void displayClientMessage(Component message, boolean actionBar) { }
    @Override public void awardStat(Stat<?> stat, int amount) { }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean canHarmPlayer(Player player) { return false; }
    @Override public void die(DamageSource source) { }
    @Override public void tick() { }
    @Override public void updateOptions(ServerboundClientInformationPacket packet) { }

    /** Becomes the player's connection, so anything sent to it goes nowhere. */
    private static final class SilentListener extends ServerGamePacketListenerImpl {
        SilentListener(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player);
        }

        @Override public void tick() { }
        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, @Nullable PacketSendListener listener) { }
        @Override public void disconnect(Component reason) { }
    }
}
