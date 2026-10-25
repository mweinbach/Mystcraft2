package dev.icbm.port.exposure;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Lets blast GameTests confirm and then cancel fallout so it cannot reach other tests. */
public final class FalloutTestHooks {
  private FalloutTestHooks() {}

  /** Removes queued fallout centred on {@code origin}; returns how many jobs were removed. */
  public static int removeAt(ServerWorld world, BlockPos origin, int radius) {
    FalloutState state = FalloutState.get(world);
    int before = state.jobs.size();
    state.jobs.removeIf(job -> job.origin().equals(origin) && job.radius() == radius);
    state.markDirty();
    return before - state.jobs.size();
  }
}
