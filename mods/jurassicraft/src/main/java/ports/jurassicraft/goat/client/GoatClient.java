package ports.jurassicraft.goat.client;

import ports.jurassicraft.goat.GoatRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class GoatClient {
    private GoatClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) { renderers.entity(GoatRegistry.GOAT.get(), GoatRenderer::new); }
}
