package com.agricraft.agricraft.client.fabric;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.client.AgriCraftClient;
import com.agricraft.agricraft.client.IrrigationClient;
import com.agricraft.agricraft.client.ClientUtil;
import com.agricraft.agricraft.client.ber.CropBlockEntityRenderer;
import com.agricraft.agricraft.client.ber.SeedAnalyzerEntityRenderer;
import com.agricraft.agricraft.client.bewlr.AgriSeedBEWLR;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import com.agricraft.agricraft.client.gui.MagnifyingGlassOverlay;
import com.agricraft.agricraft.client.gui.SeedAnalyzerScreen;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.registry.ModMenus;
import com.agricraft.agricraft.common.util.LangUtils;
import com.agricraft.agricraft.common.util.PlatformClient;
import com.agricraft.agricraft.common.util.fabric.FabricPlatformClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.agricraft.agricraft.common.registry.AgriDataRegistries;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public class AgriCraftFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		PlatformClient.setup(new FabricPlatformClient());
		AgriCraftClient.init();
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.SEED.get(), AgriSeedBEWLR.INSTANCE::renderByItem);
		ModelLoadingRegistry.INSTANCE.registerModelProvider((manager, out) -> {
			// crop, seed and weed models are not referenced by any block state, so load them explicitly
			for (String folder : new String[]{"seed", "crop", "weed"}) {
				for (ResourceLocation file : manager.listResources("models/" + folder, path -> path.getPath().endsWith(".json")).keySet()) {
					String path = file.getPath();
					out.accept(new ResourceLocation(file.getNamespace(), path.substring("models/".length(), path.length() - ".json".length())));
				}
			}
			// add the crop sticks models else they're not loaded
			for (String model : new String[]{"wooden_crop_sticks", "iron_crop_sticks", "obsidian_crop_sticks", "wooden_cross_crop_sticks", "iron_cross_crop_sticks", "obsidian_cross_crop_sticks"}) {
				out.accept(new ResourceLocation(AgriApi.MOD_ID, "block/" + model));
			}
			IrrigationClient.EXTRA_MODELS.forEach(out);
		});
		ClientPlayNetworking.registerGlobalReceiver(AgriDataRegistries.SYNC_CHANNEL, (client, handler, buf, responseSender) -> {
			var registries = AgriDataRegistries.read(buf);
			client.execute(() -> {
				AgriDataRegistries.apply(registries);
				ClientUtil.refreshCreativeSearch();
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AgriDataRegistries.clear(true));

		BlockEntityRenderers.register(ModBlockEntityTypes.CROP.get(), CropBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntityTypes.SEED_ANALYZER.get(), SeedAnalyzerEntityRenderer::new);
		IrrigationClient.registerRenderers(BlockEntityRenderers::register);
		MenuScreens.register(ModMenus.SEED_ANALYZER_MENU.get(), SeedAnalyzerScreen::new);

		HudRenderCallback.EVENT.register((poseStack, partialTicks) -> MagnifyingGlassOverlay.renderOverlay(new AgriGuiGraphics(poseStack), partialTicks));
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (stack.hasTag() && stack.getTag().getBoolean("magnifying")) {
				lines.add(1, Component.translatable("agricraft.tooltip.magnifying").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
			}
			AgriApi.getSoilRegistry().ifPresent(registry -> registry.forEach(soil -> {
						if (soil.isVariant(stack.getItem())) {
							lines.add(1, Component.translatable("agricraft.tooltip.magnifying.soil.soil").withStyle(ChatFormatting.DARK_GRAY));
							lines.add(2, Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY)
									.append(Component.translatable("agricraft.tooltip.magnifying.soil.humidity"))
									.append(LangUtils.soilPropertyName("humidity", soil.humidity())));
							lines.add(3, Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY)
									.append(Component.translatable("agricraft.tooltip.magnifying.soil.acidity"))
									.append(LangUtils.soilPropertyName("acidity", soil.acidity())));
							lines.add(4, Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY)
									.append(Component.translatable("agricraft.tooltip.magnifying.soil.nutrients"))
									.append(LangUtils.soilPropertyName("nutrients", soil.nutrients())));
						}
					}
			));
		});
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SEED_ANALYZER.get(), RenderType.cutout());
		IrrigationClient.cutoutBlocks().forEach(block -> BlockRenderLayerMap.INSTANCE.putBlock(block, RenderType.cutout()));
	}

}
