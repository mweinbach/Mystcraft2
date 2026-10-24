package com.agricraft.agricraft.client.forge;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.client.SeedBagSorterInput;
import com.agricraft.agricraft.forge.AgriCraftForgeNetwork;
import com.agricraft.agricraft.common.registry.AgriDataRegistries;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge client event handler in the forge event bus
 */
@Mod.EventBusSubscriber(modid = AgriApi.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ForgeBusClientEvents {

	@SubscribeEvent
	public static void onTooltipRender(RenderTooltipEvent.GatherComponents event) {
		if (event.getItemStack().hasTag() && event.getItemStack().getTag().getBoolean("magnifying")) {
			event.getTooltipElements().add(1, Either.left(Component.translatable("agricraft.tooltip.magnifying").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC)));
		}
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		// registries received from a remote server only apply to that connection
		AgriDataRegistries.clear(true);
	}

	@SubscribeEvent
	public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
		// shift-scrolling a seed bag cycles its sorter instead of the hotbar slot, as on Fabric
		if (Minecraft.getInstance().screen == null
				&& SeedBagSorterInput.onScroll((int) Math.signum(event.getScrollDelta()), AgriCraftForgeNetwork::sendSorter)) {
			event.setCanceled(true);
		}
	}

}
