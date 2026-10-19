package ports.jurassicraft.tourrail.client;

import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.tourrail.TourRailRegistry;

public final class TourRailClient {
    private TourRailClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(TourRailRegistry.RENDER_ENTITY.get(), context -> new TourRailRenderer());
    }
}
