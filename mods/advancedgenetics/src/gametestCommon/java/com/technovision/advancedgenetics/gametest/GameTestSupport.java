package com.technovision.advancedgenetics.gametest;

import com.mojang.authlib.GameProfile;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/** Helpers shared by the Forge and Fabric GameTest runs. Both dev runtimes use official names. */
public final class GameTestSupport {

    public static final String TEMPLATE = "advancedgenetics:empty";

    private GameTestSupport() { }

    /**
     * Creates a server player with an unconnected network handler. It is never added to the level
     * or player list, so tests do not share genes or state, but vanilla entry points such as
     * {@code hurt}, {@code doTick} and {@code interactOn} run the loader's real gene hooks.
     */
    static ServerPlayer player(GameTestHelper helper, String name, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), null);
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        setField(ServerPlayer.class, "spawnInvulnerableTime", player, 0);
        BlockPos pos = helper.absolutePos(relativePos);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    static ServerPlayer player(GameTestHelper helper, String name, Genes... genes) {
        ServerPlayer player = player(helper, name, new BlockPos(1, 2, 1));
        PlayerGeneticsComponent genetics = ComponentRegistry.getGenetics(player);
        for (Genes gene : genes) genetics.addGene(gene);
        return player;
    }

    static PlayerGeneticsComponent genetics(ServerPlayer player) {
        return ComponentRegistry.getGenetics(player);
    }

    static TestPlatform platform() {
        return TestPlatform.Holder.get();
    }

    static TestPlatform.EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        return platform().energy(blockEntity, side);
    }

    static TestPlatform.ItemAccess items(BlockEntity blockEntity, Direction side) {
        return platform().items(blockEntity, side);
    }

    /**
     * Removes every packet queued for the player's client and returns the gene syncs among them,
     * oldest first. Test players never connect, so their {@link Connection} queues all it is sent.
     */
    static List<SyncGeneticsPacket> sentGeneticsSyncs(ServerPlayer player) {
        Queue<?> queue = (Queue<?>) getField(Connection.class, "queue", player.connection.connection);
        List<SyncGeneticsPacket> syncs = new ArrayList<>();
        for (Object held = queue.poll(); held != null; held = queue.poll()) {
            Packet<?> packet = (Packet<?>) getField(held.getClass(), "packet", held);
            platform().decodeGeneticsSync(packet).ifPresent(syncs::add);
        }
        return syncs;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static Object getField(Class<?> owner, String name, Object target) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + owner.getSimpleName() + "." + name, e);
        }
    }

    static void setField(Class<?> owner, String name, Object target, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write " + owner.getSimpleName() + "." + name, e);
        }
    }
}
