package com.agricraft.agricraft.forge;

import com.agricraft.agricraft.common.forge.irrigation.ForgeTankFluidHandler;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.agricraft.agricraft.AgriCraft;
import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.common.commands.DumpRegistriesCommand;
import com.agricraft.agricraft.common.commands.GiveSeedCommand;
import com.agricraft.agricraft.common.handler.DenyBonemeal;
import com.agricraft.agricraft.common.handler.VanillaSeedConversion;
import com.agricraft.agricraft.common.registry.AgriDataRegistries;
import com.agricraft.agricraft.common.util.Platform;
import com.agricraft.agricraft.common.util.forge.ForgePlatform;
import com.agricraft.agricraft.plugin.minecraft.MinecraftPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.resource.PathPackResources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

@Mod(AgriApi.MOD_ID)
public class AgriCraftForge {

	public AgriCraftForge() {
		Platform.setup(new ForgePlatform());
		AgriCraft.init();
		AgriCraftForgeNetwork.init();
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener(AgriCraftForge::onCommonSetup);
		bus.addListener(AgriCraftForge::onAddPackFinders);
		MinecraftForge.EVENT_BUS.addListener(AgriCraftForge::onRegisterCommands);
		MinecraftForge.EVENT_BUS.addListener(AgriCraftForge::onRightClick);
		MinecraftForge.EVENT_BUS.addListener(AgriCraftForge::onRightClickBonemeal);
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeTankFluidHandler::attach);
		// 1.19.2 has no datapack registries for mods: plants, weeds, soils, mutations and fertilizers
		// load with the server's data and are sent to clients on join and after /reload.
		MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(AgriDataRegistries.reloadListener()));
		MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
			if (event.getPlayer() != null) {
				AgriCraftForgeNetwork.send(event.getPlayer());
			} else {
				event.getPlayerList().getPlayers().forEach(AgriCraftForgeNetwork::send);
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> AgriDataRegistries.clear(false));
		if (net.minecraftforge.fml.ModList.get().isLoaded("botania") && com.agricraft.agricraft.api.config.CompatConfig.enableBotania) {
			com.agricraft.agricraft.forge.compat.BotaniaForgePlugin.init();
		}
	}

	public static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(MinecraftPlugin::init);
	}

	public static void onRegisterCommands(RegisterCommandsEvent event) {
		GiveSeedCommand.register(event.getDispatcher(), event.getBuildContext());
		DumpRegistriesCommand.register(event.getDispatcher(), event.getBuildContext());
	}

	public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
		InteractionResult result = VanillaSeedConversion.onRightClick(event.getEntity(), event.getHand(), event.getPos(), event.getHitVec());
		if (result != InteractionResult.PASS) {
			event.setUseItem(Event.Result.DENY);
			event.setCanceled(true);
		}
	}

	public static void onRightClickBonemeal(PlayerInteractEvent.RightClickBlock event) {
		if (DenyBonemeal.denyBonemeal(event.getEntity(), event.getHand(), event.getPos(), event.getLevel())) {
			event.setUseItem(Event.Result.DENY);
			event.setCanceled(true);
		}
	}

	public static void onAddPackFinders(AddPackFindersEvent event) {
		if (event.getPackType() == PackType.SERVER_DATA) {
			for (IModInfo mod : ModList.get().getMods()) {
				String modId = mod.getModId();
				if (!modId.equals("minecraft") && !modId.equals("agricraft")) {
					addPack("datapacks", modId, PackType.SERVER_DATA, event);
				}
			}
		}
		if (event.getPackType() == PackType.CLIENT_RESOURCES) {
			for (IModInfo mod : ModList.get().getMods()) {
				String modId = mod.getModId();
				if (!modId.equals("minecraft") && !modId.equals("agricraft")) {
					addPack("resourcepacks", modId, PackType.CLIENT_RESOURCES, event);
				}
			}
		}
	}

	public static void addPack(String type, String modid, PackType packType, AddPackFindersEvent event) {
		Path resourcePath = ModList.get().getModFileById(AgriApi.MOD_ID).getFile().findResource(type, modid);
		if (!Files.exists(resourcePath)) return;
		String id = "builtin/agricraft_" + type + "_" + modid;
		Supplier<PackResources> resources = () -> new PathPackResources(id, resourcePath);
		PackMetadataSection metadata;
		try (PackResources packresources = resources.get()) {
			metadata = packresources.getMetadataSection(PackMetadataSection.SERIALIZER);
		} catch (IOException ignored) {
			return;
		}
		if (metadata == null) {
			return;
		}
		// 1.19.2 enables newly found data packs automatically, so CoreConfig.enablePacksByDefault cannot
		// keep these packs off on Forge; players can still disable them in the pack screen.
		Pack pack = new Pack(id, Component.translatable("agricraft." + type + "." + modid), false, resources, metadata, packType, Pack.Position.TOP, PackSource.BUILT_IN);
		event.addRepositorySource((packConsumer, constructor) -> packConsumer.accept(pack));
	}

}
