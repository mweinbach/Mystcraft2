package ports.jurassicraft.village.client;

import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.village.VillageRegistry;

public final class VillageClient {
    private VillageClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(VillageRegistry.ITEM_HOLDER_ENTITY.get(), context -> new AncientItemHolderRenderer());
    }
}
