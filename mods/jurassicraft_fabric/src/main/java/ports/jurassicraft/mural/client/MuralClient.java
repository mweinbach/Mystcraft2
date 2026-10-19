package ports.jurassicraft.mural.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ports.jurassicraft.mural.MuralRegistry;

public final class MuralClient {
    private MuralClient() {}
    public static void init() { EntityRendererRegistry.register(MuralRegistry.ENTITY.get(), MuralRenderer::new); }
}
