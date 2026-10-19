package net.mystcraft.fabric.fluid;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.integration.registry.InkRegistry;
public final class FabricInkClient {
    public static void initialize(){
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack,layer)->layer==1?0xFF191919:-1,InkRegistry.BLACK_INK_BUCKET.get());
        FluidRenderHandlerRegistry.INSTANCE.register(InkRegistry.BLACK_INK.get(),InkRegistry.FLOWING_BLACK_INK.get(),new SimpleFluidRenderHandler(new ResourceLocation("mystcraft:block/fluid"),new ResourceLocation("mystcraft:block/fluid_flow"),0xFF191919));
        BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(),InkRegistry.BLACK_INK.get(),InkRegistry.FLOWING_BLACK_INK.get());
    }
    private FabricInkClient(){}
}
