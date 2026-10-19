package ports.jurassicraft.skull.client;

import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.skull.SkullRegistry;

public final class SkullClient {
    private SkullClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) { renderers.blockEntity(SkullRegistry.ENTITY.get(), context -> new SkullRenderer()); }
}
