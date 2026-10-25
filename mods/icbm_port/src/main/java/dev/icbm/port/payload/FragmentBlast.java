package dev.icbm.port.payload;

import dev.icbm.port.api.ActionSource;
import dev.icbm.port.entity.BlastEntities;
import dev.icbm.port.entity.FragmentEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Fragment burst for the shrapnel, anvil and fragmentation payloads. Adapted from MIT
 * ICBM-Classic {@code content/blast/BlastShrapnel.java} and the projectile factories in {@code
 * content/reg/ExplosiveInit.java}.
 *
 * <p>Upstream: the blast size {@code n} is the configured fragment count; a yaw-by-pitch grid of
 * {@code n * n} fragments steps {@code 360 / n} degrees in both angles, spawns at {@code
 * floor(y) + 1.4} offset 0.16 against the yaw, and is launched along the grid direction with each
 * axis scaled by an independent random factor, at speed {@code 0.5 + 0.7 * random}. Shrapnel
 * fragments are critical and burn for 100 seconds; anvil fragments use anvil mode; fragmentation
 * fragments are explosive with the configured explosion energy.
 *
 * <p>Port bounds: {@code n} is capped at {@link #MAX_GRID} (4,096 fragments per burst) and each
 * world keeps at most {@link #MAX_LIVE_FRAGMENTS} fragments ({@link #MAX_LIVE_EXPLOSIVE}
 * explosive); when a burst would exceed that, an evenly spread subset of the grid is spawned.
 */
public final class FragmentBlast {
  public static final int MAX_GRID = 64;
  public static final int MAX_LIVE_FRAGMENTS = 4096;
  public static final int MAX_LIVE_EXPLOSIVE = 1024;

  private FragmentBlast() {}

  /**
   * @param size explicit blast size from the caller; 0 uses the configured fragment count
   * @return fragments spawned
   */
  public static int spawn(
      ServerWorld world,
      Vec3d center,
      PayloadType type,
      PayloadConfig.Settings settings,
      int size,
      ActionSource source) {
    int n = Math.min(MAX_GRID, size > 0 ? size : settings.fragments());
    if (n <= 0) return 0;
    boolean explosive = type == PayloadType.FRAGMENTATION;
    int total = n * n;
    int live =
        world
            .getEntitiesByType(
                BlastEntities.FRAGMENT, fragment -> !explosive || fragment.isExplosive())
            .size();
    int allowed =
        Math.max(0, Math.min(total, (explosive ? MAX_LIVE_EXPLOSIVE : MAX_LIVE_FRAGMENTS) - live));
    if (allowed == 0) return 0;
    Entity owner = source.owner() == null ? null : world.getEntity(source.owner());
    float step = 360f / n;
    double y = Math.floor(center.y) + 1.5 - .1;
    int index = 0, spawned = 0;
    for (int yawIndex = 0; yawIndex < n; yawIndex++) {
      float yaw = step * yawIndex;
      float yawRad = yaw / 180f * (float) Math.PI;
      for (int pitchIndex = 0; pitchIndex < n; pitchIndex++, index++) {
        // Evenly thin the grid when the world cap admits only part of it.
        if ((long) (index + 1) * allowed / total == (long) index * allowed / total) continue;
        float pitch = step * pitchIndex;
        float pitchRad = pitch / 180f * (float) Math.PI;
        double x = center.x - MathHelper.cos(yawRad) * .16f;
        double z = center.z - MathHelper.sin(yawRad) * .16f;
        if (!world.isChunkLoaded(new BlockPos(x, y, z))) continue;
        FragmentEntity fragment = create(world, type, settings);
        fragment.refreshPositionAndAngles(x, y, z, yaw, 0);
        double mx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double mz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        double my = -MathHelper.sin(pitchRad);
        double rx = mx * world.random.nextFloat(),
            ry = my * world.random.nextFloat(),
            rz = mz * world.random.nextFloat();
        if (rx * rx + ry * ry + rz * rz < 1e-12) {
          rx = mx;
          ry = my;
          rz = mz;
        }
        fragment.setHeading(rx, ry, rz, .5f + .7f * world.random.nextFloat(), 1);
        fragment.setOwner(owner);
        if (world.spawnEntity(fragment)) spawned++;
      }
    }
    return spawned;
  }

  /** Upstream {@code ExplosiveInit} projectile factories for each fragment payload. */
  static FragmentEntity create(ServerWorld world, PayloadType type, PayloadConfig.Settings s) {
    FragmentEntity fragment = new FragmentEntity(world);
    fragment.setDamage(s.entityDamage());
    switch (type) {
      case SHRAPNEL -> {
        fragment.setCritical(true);
        fragment.setOnFireFor(100);
      }
      case ANVIL -> fragment.setAnvil(true);
      case FRAGMENTATION -> {
        fragment.setExplosive(true);
        fragment.setExplosionSize((float) s.energy());
      }
      default -> {}
    }
    return fragment;
  }
}
