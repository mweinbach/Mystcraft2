package ports.jurassicraft.goat.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ports.jurassicraft.goat.GoatRegistry;

public final class GoatClient {
    private GoatClient() {}
    public static void init() { EntityRendererRegistry.register(GoatRegistry.GOAT.get(), GoatRenderer::new); }
}
