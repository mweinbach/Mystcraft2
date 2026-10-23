package dev.icbm.port.client;

import dev.icbm.port.IcbmPort;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class IcbmPortClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    BlastEffectsClient.register();
    net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
        (stack, context, lines) -> {
          var type = IcbmPort.payloadForMissile(stack);
          if (stack.getItem() instanceof net.minecraft.item.BlockItem item
              && item.getBlock() instanceof dev.icbm.port.block.CondensedExplosiveBlock block)
            type = block.getPayload();
          if (type == null) {
            for (var entries :
                java.util.List.of(
                    dev.icbm.port.delivery.DeliveryContent.GRENADES,
                    dev.icbm.port.delivery.DeliveryContent.BOMBLETS,
                    dev.icbm.port.delivery.DeliveryContent.BOMB_CARTS)) {
              for (var entry : entries.entrySet())
                if (stack.isOf(entry.getValue())) type = entry.getKey();
            }
          }
          if (type == null) return;
          String key = "tooltip.icbm_port.payload." + type.id();
          lines.add(
              net.minecraft.text.Text.translatable(key)
                  .formatted(net.minecraft.util.Formatting.GRAY));
          if (type == dev.icbm.port.payload.PayloadType.REDMATTER)
            lines.add(
                net.minecraft.text.Text.translatable("tooltip.icbm_port.redmatter_counter")
                    .formatted(net.minecraft.util.Formatting.DARK_RED));
        });
    dev.icbm.port.exposure.ExposureContent.registerClient();
    dev.icbm.port.simulation.client.SimulationClient.register();
    dev.icbm.port.launcher.LauncherContent.registerClient();
    dev.icbm.port.delivery.DeliveryClient.registerClient();
    dev.icbm.port.defense.DefenseContent.registerClient();
    EntityRendererRegistry.register(IcbmPort.MISSILE_ENTITY, ProjectileItemRenderer::new);
    EntityRendererRegistry.register(IcbmPort.PRIMED_EXPLOSIVE, PrimedExplosiveRenderer::new);
  }
}
