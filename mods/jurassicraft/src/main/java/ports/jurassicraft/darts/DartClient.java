package ports.jurassicraft.darts;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ports.jurassicraft.PortMod;

@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DartClient {
    private DartClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DartRegistry.PROJECTILE.get(), ThrownItemRenderer::new);
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer != 1 ? 0xffffff : switch (((DartItem) stack.getItem()).kind()) {
            case TRANQUILIZER -> 0xffffff;
            case POISON -> 0xe2e1b8;
            case LETHAL -> 0x111111;
            case POTION -> PotionUtils.getColor(stack);
        }, DartRegistry.TRANQUILIZER.get(), DartRegistry.POISON.get(), DartRegistry.LETHAL.get(), DartRegistry.POTION.get());
    }
}
