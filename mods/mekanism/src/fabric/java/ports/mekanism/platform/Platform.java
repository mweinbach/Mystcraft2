/*
 * Mekanism 1.19.2 port - loader services.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.fluids.FluidStack;
import ports.mekanism.lib.network.NetworkDirection;
import ports.mekanism.lib.registries.IForgeRegistry;
import ports.mekanism.lib.registries.RegistryBuilder;

/**
 * The loader services the shared code needs. Each loader entrypoint installs a {@link Backend} before any
 * Mekanism class is initialized.
 */
public final class Platform {

    @FunctionalInterface
    public interface PacketReceiver {

        void receive(FriendlyByteBuf buffer, NetworkDirection direction, @Nullable ServerPlayer sender);
    }

    public interface Backend {

        String loaderName();

        Dist dist();

        boolean isDevelopmentEnvironment();

        boolean isModLoaded(String modId);

        Optional<ModMetadata> modMetadata(String modId);

        @Nullable
        MinecraftServer currentServer();

        Path gameDirectory();

        Path configDirectory();

        CompletableFuture<Void> executeOnGameThread(boolean server, Runnable runnable);

        List<ServerPlayer> trackingPlayers(Entity entity, boolean includeSelf);

        List<ServerPlayer> trackingPlayers(LevelChunk chunk);

        void registerChannel(ResourceLocation name, PacketReceiver receiver);

        void sendToServer(ResourceLocation channel, FriendlyByteBuf payload);

        void sendToPlayer(ResourceLocation channel, ServerPlayer player, FriendlyByteBuf payload);

        void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraDataWriter);

        Packet<?> entitySpawnPacket(Entity entity);

        Component fluidName(FluidStack stack);

        String fluidTranslationKey(FluidStack stack);

        <V> IForgeRegistry<V> createRegistry(RegistryBuilder<V> builder);

        /**
         * A capability of an object that is not one of the port's own providers: another mod's block entity, item or
         * entity, through the loader's API.
         */
        <T> LazyOptional<T> foreignCapability(Object provider, Capability<T> capability, @Nullable Direction side);
    }

    private static Backend backend;

    private Platform() {
    }

    public static void install(Backend backend) {
        if (Platform.backend != null) {
            throw new IllegalStateException("The platform backend is already installed");
        }
        Platform.backend = Objects.requireNonNull(backend);
    }

    public static Backend backend() {
        return Objects.requireNonNull(backend, "The platform backend is not installed yet");
    }

    public static Dist dist() {
        return backend().dist();
    }

    public static boolean isModLoaded(String modId) {
        return backend().isModLoaded(modId);
    }

    @Nullable
    public static MinecraftServer currentServer() {
        return backend().currentServer();
    }

    public static CompletableFuture<Void> executeOnGameThread(boolean server, Runnable runnable) {
        return backend().executeOnGameThread(server, runnable);
    }

    public static Iterable<ServerPlayer> players(Function<MinecraftServer, Iterable<ServerPlayer>> selector) {
        MinecraftServer server = currentServer();
        return server == null ? List.of() : selector.apply(server);
    }

    public static List<ServerPlayer> trackingPlayers(Entity entity, boolean includeSelf) {
        return backend().trackingPlayers(entity, includeSelf);
    }

    public static List<ServerPlayer> trackingPlayers(LevelChunk chunk) {
        return backend().trackingPlayers(chunk);
    }

    public static void registerChannel(ResourceLocation name, PacketReceiver receiver) {
        backend().registerChannel(name, receiver);
    }

    public static void sendToServer(ResourceLocation channel, FriendlyByteBuf payload) {
        backend().sendToServer(channel, payload);
    }

    public static void sendToPlayer(ResourceLocation channel, ServerPlayer player, FriendlyByteBuf payload) {
        backend().sendToPlayer(channel, player, payload);
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraDataWriter) {
        backend().openMenu(player, provider, extraDataWriter);
    }

    public static Packet<?> entitySpawnPacket(Entity entity) {
        return backend().entitySpawnPacket(entity);
    }

    public static Component fluidName(FluidStack stack) {
        return backend().fluidName(stack);
    }

    public static String fluidTranslationKey(FluidStack stack) {
        return backend().fluidTranslationKey(stack);
    }

    @NotNull
    public static <T> LazyOptional<T> foreignCapability(Object provider, Capability<T> capability, @Nullable Direction side) {
        return backend().foreignCapability(provider, capability, side);
    }
}
