/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import ports.mekanism.lib.common.ForgeHooks;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.event.RegisterCommandsEvent;
import ports.mekanism.lib.event.TagsUpdatedEvent;
import ports.mekanism.lib.event.TickEvent;
import ports.mekanism.lib.event.entity.EntityLeaveLevelEvent;
import ports.mekanism.lib.event.entity.player.PlayerEvent;
import ports.mekanism.lib.event.entity.player.PlayerInteractEvent;
import ports.mekanism.lib.event.level.BlockEvent;
import ports.mekanism.lib.event.level.ChunkEvent;
import ports.mekanism.lib.event.level.LevelEvent;
import ports.mekanism.lib.event.server.ServerStoppedEvent;
import ports.mekanism.lib.eventbus.api.Event;
import ports.mekanism.lib.fml.LogicalSide;
import ports.mekanism.lib.fml.config.ConfigTracker;
import ports.mekanism.lib.fml.config.ModConfig;
import ports.mekanism.lib.fml.loading.FMLPaths;
import ports.mekanism.lib.server.permission.PermissionAPI;

/**
 * Fires the port's Forge game events from Fabric API callbacks. Events without a Fabric callback are fired by the
 * mixins in {@code ports.mekanism.fabric.mixin}.
 */
public final class FabricGameEvents {

    public static final ResourceLocation CONFIG_SYNC = new ResourceLocation("mekanism", "fabric_config_sync");

    private FabricGameEvents() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(FabricGameEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricGameEvents::serverStopped);
        ServerTickEvents.START_SERVER_TICK.register(server -> post(new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, server)));
        ServerTickEvents.END_SERVER_TICK.register(server -> post(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server)));
        ServerTickEvents.START_WORLD_TICK.register(level -> post(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.START, level, () -> true)));
        ServerTickEvents.END_WORLD_TICK.register(level -> post(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true)));
        ServerWorldEvents.LOAD.register((server, level) -> post(new LevelEvent.Load(level)));
        ServerWorldEvents.UNLOAD.register((server, level) -> post(new LevelEvent.Unload(level)));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> post(new ChunkEvent.Load(chunk, level)));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> post(new ChunkEvent.Unload(chunk, level)));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> post(new RegisterCommandsEvent(dispatcher, environment, context)));
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) ->
              post(new TagsUpdatedEvent(registries, client, client && FabricPlatform.INSTANCE.currentServer() != null)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncServerConfigs(server, handler.getPlayer());
            post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> post(new PlayerEvent.Clone(newPlayer, oldPlayer, !alive)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> post(new PlayerEvent.PlayerRespawnEvent(newPlayer, alive)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
              post(new PlayerEvent.PlayerChangedDimensionEvent(player, origin.dimension(), destination.dimension())));
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> post(new PlayerEvent.StartTracking(player, entity)));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> post(new EntityLeaveLevelEvent(entity, world)));

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
            boolean canceled = MinecraftForge.postLoaderBreakEvent(event);
            // Listeners may call setExpToDrop while the event is posted. The break mixin reads the value afterwards.
            ForgeHooks.captureBreakExp(event.getExpToDrop());
            return !canceled;
        });
        UseBlockCallback.EVENT.register(FabricGameEvents::rightClickBlock);
    }

    public static boolean post(Event event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    private static void serverStarting(MinecraftServer server) {
        FabricPlatform.INSTANCE.setServer(server);
        Path serverConfigs = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        copyDefaultServerConfigs(serverConfigs);
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, serverConfigs);
        PermissionAPI.initializePermissionAPI();
    }

    private static void serverStopped(MinecraftServer server) {
        post(new ServerStoppedEvent(server));
        ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER, server.getWorldPath(LevelResource.ROOT).resolve("serverconfig"));
        FabricPlatform.INSTANCE.setServer(null);
    }

    /**
     * Forge seeds a world's server configs from {@code defaultconfigs/} the first time the world loads.
     */
    private static void copyDefaultServerConfigs(Path serverConfigs) {
        Path defaults = FMLPaths.GAMEDIR.get().resolve("defaultconfigs");
        for (ModConfig config : ConfigTracker.INSTANCE.configs(ModConfig.Type.SERVER)) {
            Path source = defaults.resolve(config.getFileName());
            Path target = serverConfigs.resolve(config.getFileName());
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                try {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to copy the default config " + source, e);
                }
            }
        }
    }

    /**
     * Sends the server configs to a client on another machine; an integrated server's owner shares the same values.
     */
    private static void syncServerConfigs(MinecraftServer server, ServerPlayer player) {
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            return;
        }
        for (Map.Entry<String, byte[]> entry : ConfigTracker.INSTANCE.syncConfigs().entrySet()) {
            FriendlyByteBuf payload = PacketByteBufs.create();
            payload.writeUtf(entry.getKey());
            payload.writeByteArray(entry.getValue());
            ServerPlayNetworking.send(player, CONFIG_SYNC, payload);
        }
    }

    /**
     * Mekanism uses {@link PlayerInteractEvent.RightClickBlock} to let a sneaking player use a block while holding an
     * item, and to stop the item being used.
     */
    private static InteractionResult rightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos(), hit);
        if (post(event)) {
            return event.getCancellationResult() == InteractionResult.PASS ? InteractionResult.FAIL : event.getCancellationResult();
        }
        if (event.getUseBlock() == Event.Result.ALLOW) {
            InteractionResult result = level.getBlockState(hit.getBlockPos()).use(level, player, hand, hit);
            if (result.consumesAction()) {
                return result;
            }
        }
        if (event.getUseItem() == Event.Result.DENY && event.getUseBlock() != Event.Result.DEFAULT) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
