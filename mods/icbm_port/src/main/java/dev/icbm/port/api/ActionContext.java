package dev.icbm.port.api;

import java.util.Objects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Server-owned action request. Size is an absolute radius/area-size override, not a multiplier. */
public record ActionContext(
    ServerWorld world, Vec3d position, Identifier action, double size, ActionSource source) {
  public ActionContext {
    Objects.requireNonNull(world);
    Objects.requireNonNull(position);
    Objects.requireNonNull(action);
    Objects.requireNonNull(source);
    if (!Double.isFinite(position.x)
        || !Double.isFinite(position.y)
        || !Double.isFinite(position.z)
        || !Double.isFinite(size)
        || size < 0
        || size > 80) throw new IllegalArgumentException("Invalid action position or size [0,80]");
  }

  public boolean isLoadedAndInBounds() {
    BlockPos pos = new BlockPos(position);
    return world.isChunkLoaded(pos)
        && !world.isOutOfHeightLimit(pos)
        && world.getWorldBorder().contains(pos);
  }
}
