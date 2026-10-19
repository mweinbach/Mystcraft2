package dev.icbm.port.simulation;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Every entity's latest physical step, recorded around {@link Entity#move} by a mixin. */
public interface MovementTrace {
  /**
   * Where the entity's latest {@code move} started, or null when it has not moved during this or
   * the previous world tick, or was repositioned by anything else afterwards (a teleport, spawn,
   * {@code setPosition} or saved-data load).
   */
  @Nullable
  Vec3d icbm$stepStart();

  static @Nullable Vec3d stepStart(Entity entity) {
    return ((MovementTrace) entity).icbm$stepStart();
  }
}
