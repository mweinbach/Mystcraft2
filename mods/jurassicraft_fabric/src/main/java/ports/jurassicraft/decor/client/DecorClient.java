package ports.jurassicraft.decor.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.decor.DecorRegistry;
import ports.jurassicraft.decor.ParkSigns;

public final class DecorClient {
    private DecorClient() {}
    public static void init() {
        EntityRendererRegistry.register(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), ParkSignRenderer::new);
        EntityRendererRegistry.register(DecorRegistry.PADDOCK_SIGN_ENTITY.get(), ParkSignRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), DecorRegistry.CLEAR_GLASS.get(), DecorRegistry.CLEAR_GLASS_PANE.get(),
                DecorRegistry.REINFORCED_GLASS.get(), DecorRegistry.REINFORCED_GLASS_PANE.get());
        ItemProperties.register(DecorRegistry.ATTRACTION_SIGN.get(), new ResourceLocation(PortMod.MOD_ID, "sign_artwork"),
                (stack, level, entity, seed) -> ParkSigns.ATTRACTIONS.indexOf(ParkSigns.validate(true, stack.hasTag() ? stack.getTag().getString("Artwork") : "")));
        ParkSignClientNetwork.register();
    }
}
