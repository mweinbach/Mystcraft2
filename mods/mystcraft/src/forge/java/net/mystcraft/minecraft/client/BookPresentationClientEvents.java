package net.mystcraft.minecraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mystcraft.minecraft.presentation.BookPresentationOptions;

@Mod.EventBusSubscriber(modid = "mystcraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BookPresentationClientEvents {
    private BookPresentationClientEvents() {}
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { BookPresentationOptions.acceptServer(false); }
}
