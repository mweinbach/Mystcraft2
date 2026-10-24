package dev.icbm.port.simulation;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class SimulationContent {
  public static final EntityType<FlyingBlockEntity> FLYING_BLOCK =
      Registry.register(
          Registry.ENTITY_TYPE,
          new Identifier("icbm_port", "flying_block"),
          FabricEntityTypeBuilder.<FlyingBlockEntity>create(SpawnGroup.MISC, FlyingBlockEntity::new)
              .dimensions(EntityDimensions.fixed(.9f, .9f))
              .trackRangeBlocks(96)
              .trackedUpdateRate(2)
              .build());
  public static final EntityType<RedmatterEntity> REDMATTER =
      Registry.register(
          Registry.ENTITY_TYPE,
          new Identifier("icbm_port", "redmatter_singularity"),
          FabricEntityTypeBuilder.<RedmatterEntity>create(SpawnGroup.MISC, RedmatterEntity::new)
              .dimensions(EntityDimensions.fixed(1, 1))
              .trackRangeBlocks(256)
              .trackedUpdateRate(2)
              .build());

  private SimulationContent() {}

  public static void register() {
    /* Class initialization performs registration once. */
  }
}
