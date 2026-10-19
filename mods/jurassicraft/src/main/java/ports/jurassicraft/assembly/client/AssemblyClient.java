package ports.jurassicraft.assembly.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class AssemblyClient {
    private AssemblyClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(AssemblyRegistry.DISPLAY_ENTITY.get(), context -> new SkeletonDisplayRenderer());
    }
    public static void setup() {
        MenuScreens.register(AssemblyRegistry.MENU.get(), SkeletonAssemblyScreen::new);
        ItemProperties.register(AssemblyRegistry.DISPLAY_ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "species"),
                (stack, level, entity, seed) -> SkeletonDisplayItem.species(stack).ordinal());
        ItemProperties.register(AssemblyRegistry.DISPLAY_ITEM.get(), new ResourceLocation(PortMod.MOD_ID, "fresh"),
                (stack, level, entity, seed) -> SkeletonDisplayItem.fresh(stack) ? 1.0F : 0.0F);
    }
}
