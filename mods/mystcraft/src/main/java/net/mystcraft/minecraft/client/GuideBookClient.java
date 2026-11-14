package net.mystcraft.minecraft.client;

import net.minecraft.client.Minecraft;

/** Client-only entry point; the guide remembers the reader's place for this session. */
public final class GuideBookClient {
    private static int lastPage;

    private GuideBookClient() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideBookScreen(lastPage, page -> lastPage = page));
    }
}
