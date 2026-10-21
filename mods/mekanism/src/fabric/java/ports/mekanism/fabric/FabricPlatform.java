/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.lib.common.capabilities.Capability;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.fluids.FluidStack;
import ports.mekanism.lib.fluids.FluidTypes;
import ports.mekanism.lib.network.NetworkDirection;
import ports.mekanism.lib.registries.IForgeRegistry;
import ports.mekanism.lib.registries.RegistryBuilder;
import ports.mekanism.lib.registries.VanillaForgeRegistry;
import ports.mekanism.platform.ModMetadata;
import ports.mekanism.platform.Platform;

/**
 * The Fabric implementation of the port's loader services.
 */
public final class FabricPlatform implements Platform.Backend {

    /**
     * What only the client can do. {@code FabricClientMod} installs it.
     */
    public interface ClientSide {

        void registerReceiver(ResourceLocation channel, Platform.PacketReceiver receiver);

        void sendToServer(ResourceLocation channel, FriendlyByteBuf payload);

        CompletableFuture<Void> execute(Runnable runnable);
    }

    static final FabricPlatform INSTANCE = new FabricPlatform();
    private static final Set<ResourceKey<? extends Registry<?>>> FORGE_TAG_DIRECTORIES = ConcurrentHashMap.newKeySet();

    private final Map<ResourceLocation, Platform.PacketReceiver> channels = new ConcurrentHashMap<>();
    @Nullable
    private volatile MinecraftServer server;
    @Nullable
    private ClientSide clientSide;

    private FabricPlatform() {
    }

    public static void installClient(ClientSide clientSide) {
        INSTANCE.clientSide = clientSide;
        INSTANCE.channels.forEach(clientSide::registerReceiver);
    }

    void setServer(@Nullable MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public Dist dist() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<ModMetadata> modMetadata(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> new ModMetadata(modId, container.getMetadata().getName(),
              container.getMetadata().getVersion().getFriendlyString(), container.getRootPaths()));
    }

    @Nullable
    @Override
    public MinecraftServer currentServer() {
        return server;
    }

    @Override
    public Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public CompletableFuture<Void> executeOnGameThread(boolean onServer, Runnable runnable) {
        if (onServer) {
            MinecraftServer current = server;
            if (current == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No server is running"));
            }
            return current.submit(runnable);
        }
        return Objects.requireNonNull(clientSide, "Not on a client").execute(runnable);
    }

    @Override
    public List<ServerPlayer> trackingPlayers(Entity entity, boolean includeSelf) {
        List<ServerPlayer> players = new ArrayList<>(PlayerLookup.tracking(entity));
        if (includeSelf && entity instanceof ServerPlayer self && !players.contains(self)) {
            players.add(self);
        }
        return players;
    }

    @Override
    public List<ServerPlayer> trackingPlayers(LevelChunk chunk) {
        return List.copyOf(PlayerLookup.tracking((ServerLevel) chunk.getLevel(), chunk.getPos()));
    }

    @Override
    public void registerChannel(ResourceLocation name, Platform.PacketReceiver receiver) {
        if (channels.putIfAbsent(name, receiver) != null) {
            throw new IllegalStateException("Channel " + name + " is already registered");
        }
        ServerPlayNetworking.registerGlobalReceiver(name, (srv, player, handler, buf, responseSender) ->
              receiver.receive(buf, NetworkDirection.PLAY_TO_SERVER, player));
        if (clientSide != null) {
            clientSide.registerReceiver(name, receiver);
        }
    }

    @Override
    public void sendToServer(ResourceLocation channel, FriendlyByteBuf payload) {
        Objects.requireNonNull(clientSide, "Not on a client").sendToServer(channel, payload);
    }

    @Override
    public void sendToPlayer(ResourceLocation channel, ServerPlayer player, FriendlyByteBuf payload) {
        ServerPlayNetworking.send(player, channel, payload);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraDataWriter) {
        FabricNetworking.openMenu(player, provider, extraDataWriter);
    }

    @Override
    public Packet<?> entitySpawnPacket(Entity entity) {
        return FabricNetworking.entitySpawnPacket(entity);
    }

    @Override
    public Component fluidName(FluidStack stack) {
        return FluidTypes.of(stack.getFluid()).getDescription(stack);
    }

    @Override
    public String fluidTranslationKey(FluidStack stack) {
        return FluidTypes.of(stack.getFluid()).getDescriptionId(stack);
    }

    /**
     * Forge registries a mod creates are real vanilla registries on Fabric, so datapack tags load into them.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <V> IForgeRegistry<V> createRegistry(RegistryBuilder<V> builder) {
        ResourceLocation name = Objects.requireNonNull(builder.getName(), "Registry name");
        FabricRegistryBuilder<V, MappedRegistry<V>> fabricBuilder = builder.getDefault() == null
              ? FabricRegistryBuilder.createSimple((Class) Object.class, name)
              : (FabricRegistryBuilder) FabricRegistryBuilder.createDefaulted((Class) Object.class, name, builder.getDefault());
        if (builder.getSync()) {
            fabricBuilder.attribute(RegistryAttribute.SYNCED);
        }
        if (builder.getSaveToDisc()) {
            fabricBuilder.attribute(RegistryAttribute.PERSISTED);
        }
        Registry<V> registry = fabricBuilder.buildAndRegister();
        if (builder.getHasTags()) {
            FORGE_TAG_DIRECTORIES.add(registry.key());
        }
        return new VanillaForgeRegistry<>(registry);
    }

    /**
     * Forge reads the tags of a modded registry from {@code tags/<namespace>/<path>}, vanilla from {@code tags/<path>}.
     * Registries Mekanism creates keep Forge's layout so the shared data's tags load.
     */
    public static boolean usesForgeTagDirectory(ResourceKey<? extends Registry<?>> registry) {
        return FORGE_TAG_DIRECTORIES.contains(registry);
    }

    @Override
    public <T> LazyOptional<T> foreignCapability(Object provider, Capability<T> capability, @Nullable Direction side) {
        return LazyOptional.empty();
    }
}
