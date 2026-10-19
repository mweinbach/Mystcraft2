package ports.jurassicraft.tourrail.client;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import ports.jurassicraft.tourrail.TourRailRegistry;

public final class TourRailClient {
    private TourRailClient() {}
    public static void init() {
        BlockEntityRendererRegistry.register(TourRailRegistry.RENDER_ENTITY.get(), context -> new TourRailRenderer());
    }
}
