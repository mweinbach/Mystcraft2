package net.mystcraft.minecraft.client;

import net.minecraft.client.Minecraft;
import net.mystcraft.integration.network.LinkingNetwork;

/** Referenced only behind a physical-client distribution gate. */
public final class LinkingBookClient {
    private LinkingBookClient() {}
    public static void open(LinkingNetwork.OpenBook packet) {
        Minecraft.getInstance().setScreen(new LinkingBookScreen(packet, Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof net.mystcraft.minecraft.exploration.BookDisplayMenu menu ? menu : null));
    }
}
