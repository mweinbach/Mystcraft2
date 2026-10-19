package ports.jurassicraft.hatching;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class HatcheryClient {
    private HatcheryClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.blockEntity(HatcheryRegistry.blockEntity(HatcheryRegistry.Machine.INCUBATOR), IncubatorRenderer::new);
    }
    public static void setup() {
        for (var machine : HatcheryRegistry.Machine.values()) {
            MenuScreens.register(HatcheryRegistry.menu(machine), HatcheryScreen::new);
            ClientPlatform.renderLayer(RenderType.cutout(), HatcheryRegistry.block(machine));
        }
    }
}
