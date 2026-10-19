package ports.jurassicraft.mural.client;

import ports.jurassicraft.mural.MuralRegistry;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class MuralClient {
    private MuralClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) { renderers.entity(MuralRegistry.ENTITY.get(), MuralRenderer::new); }
}
