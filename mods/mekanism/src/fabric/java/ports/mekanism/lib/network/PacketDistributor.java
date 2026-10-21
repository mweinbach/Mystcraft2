/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import ports.mekanism.platform.Platform;

/**
 * Selects the players a server-to-client packet goes to.
 */
public final class PacketDistributor<T> {

    public static final PacketDistributor<ServerPlayer> PLAYER = new PacketDistributor<>(player -> Platform.players(server -> java.util.List.of(player)));
    public static final PacketDistributor<Void> ALL = new PacketDistributor<>(ignored -> Platform.players(server -> server.getPlayerList().getPlayers()));
    public static final PacketDistributor<ResourceKey<Level>> DIMENSION = new PacketDistributor<>(dimension -> Platform.players(
          server -> server.getPlayerList().getPlayers().stream().filter(player -> player.getLevel().dimension() == dimension).toList()));
    public static final PacketDistributor<Entity> TRACKING_ENTITY = new PacketDistributor<>(entity -> Platform.players(server -> Platform.trackingPlayers(entity, false)));
    public static final PacketDistributor<Entity> TRACKING_ENTITY_AND_SELF = new PacketDistributor<>(entity -> Platform.players(server -> Platform.trackingPlayers(entity, true)));
    public static final PacketDistributor<LevelChunk> TRACKING_CHUNK = new PacketDistributor<>(chunk -> Platform.players(server -> Platform.trackingPlayers(chunk)));

    private final Function<T, PacketTarget> targets;

    private PacketDistributor(Function<T, PacketTarget> targets) {
        this.targets = targets;
    }

    public PacketTarget with(Supplier<T> input) {
        return targets.apply(input.get());
    }

    public PacketTarget noArg() {
        return targets.apply(null);
    }

    /**
     * The players a packet is sent to, computed when it is sent.
     */
    @FunctionalInterface
    public interface PacketTarget {

        Iterable<ServerPlayer> players();
    }
}
