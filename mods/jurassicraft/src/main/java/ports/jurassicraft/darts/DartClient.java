package ports.jurassicraft.darts;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.alchemy.PotionUtils;
import ports.jurassicraft.platform.client.ClientPlatform;

public final class DartClient {
    private DartClient() {}
    public static void renderers(ClientPlatform.Renderers renderers) {
        renderers.entity(DartRegistry.PROJECTILE.get(), ThrownItemRenderer::new);
    }
    public static void colors(ClientPlatform.Colors colors) {
        colors.item((stack, layer) -> layer != 1 ? 0xffffff : switch (((DartItem) stack.getItem()).kind()) {
            case TRANQUILIZER -> 0xffffff;
            case POISON -> 0xe2e1b8;
            case LETHAL -> 0x111111;
            case POTION -> PotionUtils.getColor(stack);
        }, DartRegistry.TRANQUILIZER.get(), DartRegistry.POISON.get(), DartRegistry.LETHAL.get(), DartRegistry.POTION.get());
    }
}
