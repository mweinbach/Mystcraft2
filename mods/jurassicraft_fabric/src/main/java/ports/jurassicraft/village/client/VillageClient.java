package ports.jurassicraft.village.client;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import ports.jurassicraft.village.VillageRegistry;
public final class VillageClient {
    private VillageClient() {}
    public static void init() { BlockEntityRenderers.register(VillageRegistry.ITEM_HOLDER_ENTITY.get(), context -> new AncientItemHolderRenderer()); }
}
