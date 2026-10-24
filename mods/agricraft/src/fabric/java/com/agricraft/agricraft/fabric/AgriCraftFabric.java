package com.agricraft.agricraft.fabric;

import com.agricraft.agricraft.AgriCraft;
import com.agricraft.agricraft.api.config.CoreConfig;
import com.agricraft.agricraft.common.commands.DumpRegistriesCommand;
import com.agricraft.agricraft.common.commands.GiveSeedCommand;
import com.agricraft.agricraft.common.handler.DenyBonemeal;
import com.agricraft.agricraft.common.handler.VanillaSeedConversion;
import com.agricraft.agricraft.common.registry.AgriDataRegistries;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.fabric.FabricPlatform;
import com.agricraft.agricraft.plugin.minecraft.MinecraftPlugin;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AgriCraftFabric implements ModInitializer {

	public static MinecraftServer cachedServer;

	@Override
	public void onInitialize() {
		Platform.setup(new FabricPlatform());
		AgriCraft.init();
		registerDataRegistries();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> cachedServer = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			cachedServer = null;
			AgriDataRegistries.clear(false);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			GiveSeedCommand.register(dispatcher, registryAccess);
			DumpRegistriesCommand.register(dispatcher, registryAccess);
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> VanillaSeedConversion.onRightClick(player, hand, hitResult.getBlockPos(), hitResult));
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (DenyBonemeal.denyBonemeal(player, hand, hitResult.getBlockPos(), world)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		MinecraftPlugin.init();

		FabricLoader.getInstance().getModContainer("agricraft").ifPresent(agricraft -> {
			// Fabric API 0.77 only accepts a custom sub-path with the boolean overload.
			boolean activation = CoreConfig.enablePacksByDefault;
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String modid = mod.getMetadata().getId();
				if (!modid.equals("agricraft") && !modid.equals("minecraft")) {
					if (agricraft.findPath("datapacks/" + modid).isPresent()) {
						ResourceManagerHelper.registerBuiltinResourcePack(new ResourceLocation("agricraft", "agricraft_datapacks_" + modid), "datapacks/" + modid, agricraft, activation);
					}
					if (agricraft.findPath("resourcepacks/" + modid).isPresent()) {
						ResourceManagerHelper.registerBuiltinResourcePack(new ResourceLocation("agricraft", "agricraft_resourcepacks_" + modid), "resourcepacks/" + modid, agricraft, activation);
					}
				}
			}
		});
	}

	/** Loads plants, weeds, soils, mutations and fertilizers with the server's data and sends them to players. */
	private static void registerDataRegistries() {
		PreparableReloadListener listener = AgriDataRegistries.reloadListener();
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return AgriDataRegistries.RELOAD_LISTENER_ID;
			}

			@Override
			public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller prepareProfiler,
			                                      ProfilerFiller applyProfiler, Executor backgroundExecutor, Executor gameExecutor) {
				return listener.reload(barrier, manager, prepareProfiler, applyProfiler, backgroundExecutor, gameExecutor);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendDataRegistries(handler.player));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				server.getPlayerList().getPlayers().forEach(AgriCraftFabric::sendDataRegistries);
			}
		});
	}

	public static void sendDataRegistries(ServerPlayer player) {
		// The integrated server's own client already reads the server's registries.
		if (player.connection.connection.isMemoryConnection()) {
			return;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		AgriDataRegistries.write(buf);
		ServerPlayNetworking.send(player, AgriDataRegistries.SYNC_CHANNEL, buf);
	}

}
