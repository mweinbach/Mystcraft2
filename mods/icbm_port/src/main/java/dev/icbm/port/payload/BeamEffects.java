package dev.icbm.port.payload;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Hook for the light-beam visual of the exothermic and endothermic blasts (upstream {@code
 * EntityLightBeam}, driven by {@code content/blast/BlastBeam}). The blast reports its beam colour
 * and target progress at each stage; an entity/renderer module installs a listener to draw it.
 * Without a listener the beam is not shown.
 */
public final class BeamEffects {
  /** Receives beam updates on the server thread. */
  @FunctionalInterface
  public interface Listener {
    /**
     * @param beamId stable id of one blast's beam, kept across saves
     * @param progress upstream target beam progress (0.1, 0.2, 0.5, 0.8, 1, then 0 when dying)
     * @param dying true once the blast completed (upstream {@code startDeathCycle})
     */
    void update(
        ServerWorld world,
        Vec3d center,
        long beamId,
        float red,
        float green,
        float blue,
        float progress,
        boolean dying);
  }

  private static volatile Listener listener = (w, c, id, r, g, b, p, d) -> {};

  private BeamEffects() {}

  public static void setListener(Listener next) {
    listener = next == null ? (w, c, id, r, g, b, p, d) -> {} : next;
  }

  /** The installed listener (never null), so a test can restore it. */
  public static Listener listener() {
    return listener;
  }

  static void update(
      ServerWorld world,
      Vec3d center,
      long beamId,
      float red,
      float green,
      float blue,
      float progress,
      boolean dying) {
    listener.update(world, center, beamId, red, green, blue, progress, dying);
  }
}
