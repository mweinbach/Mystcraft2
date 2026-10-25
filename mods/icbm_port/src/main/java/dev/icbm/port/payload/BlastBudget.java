package dev.icbm.port.payload;

import net.minecraft.server.world.ServerWorld;

/**
 * Shared wall-clock budget for {@link BlastWork} in one world tick. Upstream computed large blasts
 * on worker threads; the port keeps every world access on the server thread, so cheap ray steps
 * may run past the per-job unit share only while this world tick still has time left.
 */
final class BlastBudget {
  /** Total time all staged blasts in one world may use per tick beyond their guaranteed share. */
  static long tickBudgetNanos = 8_000_000L;

  private static ServerWorld budgetWorld;
  private static long budgetTime = Long.MIN_VALUE, deadline;

  private BlastBudget() {}

  /** True while the shared time budget of this world tick is not used up. */
  static boolean hasTime(ServerWorld world) {
    if (budgetWorld != world || budgetTime != world.getTime()) {
      budgetWorld = world;
      budgetTime = world.getTime();
      deadline = System.nanoTime() + tickBudgetNanos;
    }
    return System.nanoTime() < deadline;
  }
}
