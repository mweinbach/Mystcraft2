package ports.jurassicraft.assembly.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AssemblyClient {
    private AssemblyClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(AssemblyRegistry.MENU.get(), SkeletonAssemblyScreen::new);
            BlockEntityRenderers.register(AssemblyRegistry.DISPLAY_ENTITY.get(), context -> new SkeletonDisplayRenderer());
            ItemProperties.register(AssemblyRegistry.DISPLAY_ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "species"),
                    (stack, level, entity, seed) -> SkeletonDisplayItem.species(stack).ordinal());
            ItemProperties.register(AssemblyRegistry.DISPLAY_ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "fresh"),
                    (stack, level, entity, seed) -> SkeletonDisplayItem.fresh(stack) ? 1.0F : 0.0F);
        });
    }
}
