package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.api.IcbmApi;
import dev.icbm.port.delivery.DeliveryContent;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.registry.Registry;

/**
 * Registration for upstream {@code EntityReg} entries that the port previously lacked: the
 * launcher seat ({@code holder.seat}, tracked 50 blocks every 2 ticks), the light beam ({@code
 * beam}, 80 blocks every 5 ticks) and the actionable missile. Upstream tracks missiles at 500
 * blocks; the actionable missile keeps the port's existing 128-block missile tracking.
 */
public final class EntityContent {
  public static final EntityType<PlayerSeatEntity> PLAYER_SEAT =
      FabricEntityTypeBuilder.<PlayerSeatEntity>create(SpawnGroup.MISC, PlayerSeatEntity::new)
          .dimensions(EntityDimensions.fixed(0.5F, 2.5F))
          .trackRangeBlocks(50)
          .trackedUpdateRate(2)
          .disableSummon()
          .build();
  public static final EntityType<LightBeamEntity> LIGHT_BEAM =
      FabricEntityTypeBuilder.<LightBeamEntity>create(SpawnGroup.MISC, LightBeamEntity::new)
          .dimensions(EntityDimensions.fixed(1F, 1F))
          .trackRangeBlocks(80)
          .trackedUpdateRate(5)
          .fireImmune()
          .build();
  public static final EntityType<ActionableMissileEntity> ACTIONABLE_MISSILE =
      FabricEntityTypeBuilder.<ActionableMissileEntity>create(
              SpawnGroup.MISC, ActionableMissileEntity::new)
          .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
          .trackRangeBlocks(128)
          .trackedUpdateRate(1)
          .build();

  private EntityContent() {}

  public static void register() {
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("player_seat"), PLAYER_SEAT);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("light_beam"), LIGHT_BEAM);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("actionable_missile"), ACTIONABLE_MISSILE);
    IcbmApi.registerProjectile(
        IcbmPort.id("actionable_missile"),
        context -> {
          if (!context.payload().isOf(DeliveryContent.CLUSTER_MISSILE)) return null;
          return ActionableMissileEntity.forCluster(context.world(), context.payload());
        });
  }
}
