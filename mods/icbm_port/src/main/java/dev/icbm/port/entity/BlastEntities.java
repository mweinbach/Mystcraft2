package dev.icbm.port.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * Blast-spawned entities. Tracking ranges and update rates follow upstream {@code
 * content/reg/EntityReg.java}: fragments 40 blocks every tick, smoke 100 blocks every 15 ticks.
 */
public final class BlastEntities {
  public static final EntityType<FragmentEntity> FRAGMENT =
      Registry.register(
          Registry.ENTITY_TYPE,
          new Identifier("icbm_port", "fragment"),
          FabricEntityTypeBuilder.<FragmentEntity>create(SpawnGroup.MISC, FragmentEntity::new)
              .dimensions(EntityDimensions.fixed(.5f, .5f))
              .trackRangeBlocks(40)
              .trackedUpdateRate(1)
              .forceTrackedVelocityUpdates(true)
              .build());
  public static final EntityType<SmokeEntity> SMOKE =
      Registry.register(
          Registry.ENTITY_TYPE,
          new Identifier("icbm_port", "smoke"),
          FabricEntityTypeBuilder.<SmokeEntity>create(SpawnGroup.MISC, SmokeEntity::new)
              .dimensions(EntityDimensions.fixed(.1f, .1f))
              .trackRangeBlocks(100)
              .trackedUpdateRate(15)
              .build());

  private BlastEntities() {}

  public static void register() {
    /* Class initialization performs registration once. */
  }
}
