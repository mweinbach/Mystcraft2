package ports.jurassicraft.darts;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.alchemy.PotionUtils;

public final class DartClient {
    private DartClient() {}
    public static void init() {
        EntityRendererRegistry.register(DartRegistry.PROJECTILE.get(), ThrownItemRenderer::new);
        ColorProviderRegistry.ITEM.register((stack, layer) -> layer != 1 ? 0xffffff : switch (((DartItem) stack.getItem()).kind()) {
            case TRANQUILIZER -> 0xffffff;
            case POISON -> 0xe2e1b8;
            case LETHAL -> 0x111111;
            case POTION -> PotionUtils.getColor(stack);
        }, DartRegistry.TRANQUILIZER.get(), DartRegistry.POISON.get(), DartRegistry.LETHAL.get(), DartRegistry.POTION.get());
    }
}
