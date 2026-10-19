package ports.jurassicraft.decor.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.decor.DecorRegistry;
import ports.jurassicraft.decor.ParkSigns;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DecorClient {
    private DecorClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), ParkSignRenderer::new);
        event.registerEntityRenderer(DecorRegistry.PADDOCK_SIGN_ENTITY.get(), ParkSignRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(DecorRegistry.ATTRACTION_SIGN.get(),
                new ResourceLocation(PortMod.MOD_ID, "sign_artwork"), (stack, level, entity, seed) -> {
                    String artwork = stack.hasTag() ? stack.getTag().getString("Artwork") : "";
                    return ParkSigns.ATTRACTIONS.indexOf(ParkSigns.validate(true, artwork));
                }));
    }
}
