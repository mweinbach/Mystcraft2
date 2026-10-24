package com.agricraft.agricraft.client.forge;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.client.AgriCraftClient;
import com.agricraft.agricraft.client.IrrigationClient;
import com.agricraft.agricraft.client.ber.CropBlockEntityRenderer;
import com.agricraft.agricraft.client.ber.SeedAnalyzerEntityRenderer;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.client.gui.MagnifyingGlassOverlay;
import com.agricraft.agricraft.client.gui.SeedAnalyzerScreen;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModMenus;
import com.agricraft.agricraft.common.util.PlatformClient;
import com.agricraft.agricraft.common.util.forge.ForgePlatformClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;


/**
 * Forge client event handler in the mod event bus
 */
@Mod.EventBusSubscriber(modid = AgriApi.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AgriCraftForgeClient {

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		PlatformClient.setup(new ForgePlatformClient());
		AgriCraftForgeClient.init();
		MenuScreens.register(ModMenus.SEED_ANALYZER_MENU.get(), SeedAnalyzerScreen::new);
		ItemBlockRenderTypes.setRenderLayer(ModBlocks.SEED_ANALYZER.get(), RenderType.cutout());
		IrrigationClient.cutoutBlocks().forEach(block -> ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout()));
	}

	@SubscribeEvent
	public static void loadModels(ModelEvent.RegisterAdditional event) {
		// crop, seed and weed models are not referenced by any block state, so load them explicitly
		ResourceManager manager = Minecraft.getInstance().getResourceManager();
		for (String folder : new String[]{"seed", "crop", "weed"}) {
			for (ResourceLocation file : manager.listResources("models/" + folder, path -> path.getPath().endsWith(".json")).keySet()) {
				String path = file.getPath();
				event.register(new ResourceLocation(file.getNamespace(), path.substring("models/".length(), path.length() - ".json".length())));
			}
		}
		// add the crop sticks models else they're not loaded
		event.register(new ResourceLocation("agricraft:block/wooden_crop_sticks"));
		event.register(new ResourceLocation("agricraft:block/iron_crop_sticks"));
		event.register(new ResourceLocation("agricraft:block/obsidian_crop_sticks"));
		event.register(new ResourceLocation("agricraft:block/wooden_cross_crop_sticks"));
		event.register(new ResourceLocation("agricraft:block/iron_cross_crop_sticks"));
		event.register(new ResourceLocation("agricraft:block/obsidian_cross_crop_sticks"));
		IrrigationClient.EXTRA_MODELS.forEach(event::register);
	}

	@SubscribeEvent
	public static void registerBer(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(ModBlockEntityTypes.CROP.get(), CropBlockEntityRenderer::new);
		event.registerBlockEntityRenderer(ModBlockEntityTypes.SEED_ANALYZER.get(), SeedAnalyzerEntityRenderer::new);
		IrrigationClient.registerRenderers(event::registerBlockEntityRenderer);
	}

	@SubscribeEvent
	public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "magnifying_glass_info", (forgeGui, poseStack, partialTicks, width, height) -> MagnifyingGlassOverlay.renderOverlay(new AgriGuiGraphics(poseStack), partialTicks));
	}

	public static void init() {
		AgriCraftClient.init();
	}

}
