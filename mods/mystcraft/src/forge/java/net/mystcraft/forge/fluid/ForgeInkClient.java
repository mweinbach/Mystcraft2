package net.mystcraft.forge.fluid;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.mystcraft.integration.registry.InkRegistry;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="mystcraft",bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ForgeInkClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event){event.enqueueWork(ForgeInkClient::initialize);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void colors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event){event.register((stack,layer)->layer==1?0xFF191919:-1,InkRegistry.BLACK_INK_BUCKET.get());}
    public static void initialize(){ItemBlockRenderTypes.setRenderLayer(InkRegistry.BLACK_INK.get(),RenderType.translucent());ItemBlockRenderTypes.setRenderLayer(InkRegistry.FLOWING_BLACK_INK.get(),RenderType.translucent());}
    private ForgeInkClient(){}
}
