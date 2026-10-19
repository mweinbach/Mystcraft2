package ports.jurassicraft.skull.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import ports.jurassicraft.skull.SkullRegistry;

public final class SkullClient {
    private SkullClient() {}
    public static void init() { BlockEntityRenderers.register(SkullRegistry.ENTITY.get(), context -> new SkullRenderer()); }
}
