package dev.icbm.port.payload;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Staged, saved and cancellable work for one queued blast. Implementations replace upstream's
 * ticking blasts and worker threads: every world read and edit happens on the server thread
 * inside {@link #tick}, under a per-call unit budget plus the shared per-world-tick time budget of
 * {@link BlastBudget}, and all progress needed to resume is written by {@link #write}.
 *
 * <p>The owning {@link Payloads.Job} keeps the centre, radius, attribution and elapsed lifetime;
 * removing the job from the queue (for example with {@code /icbm remove}) cancels the work.
 */
interface BlastWork {
  /**
   * Advances the blast.
   *
   * @param budget guaranteed minimum work units for this call; implementations may continue while
   *     {@link BlastBudget#hasTime} allows
   * @return true when the blast has finished
   */
  boolean tick(ServerWorld world, Payloads.Job job, int budget);

  /** Saved progress, read back by {@link #read}. */
  NbtCompound write();

  /** Creates the work for a newly accepted detonation, or null when the family has none. */
  static @Nullable BlastWork create(
      ServerWorld world, Vec3d center, PayloadType type, int size, @Nullable Entity source) {
    return switch (type) {
      case CONDENSED, REPULSIVE, ATTRACTIVE -> new TntBlast(type, size);
      case INCENDIARY -> new FireBlast(size);
      case SONIC -> new SonicBlast(size);
      case BREACHING -> BreachBlast.create(world, center, size, source);
      case THERMOBARIC, NUCLEAR -> new ThermalBlast(type, size);
      case EXOTHERMIC, ENDOTHERMIC -> new BeamBlast(type, size);
      case ANTI_GRAVITATIONAL -> new AntiGravityBlast(world, center, size);
      case ENDER -> EnderBlast.create(world, center, size, source);
      default -> null;
    };
  }

  /** Restores saved work; returns null for unknown or malformed data so the job is dropped. */
  static @Nullable BlastWork read(PayloadType type, NbtCompound nbt, int radius) {
    try {
      return switch (type) {
        case CONDENSED, REPULSIVE, ATTRACTIVE -> TntBlast.read(type, nbt, radius);
        case INCENDIARY -> FireBlast.read(nbt, radius);
        case SONIC -> SonicBlast.read(nbt, radius);
        case BREACHING -> BreachBlast.read(nbt, radius);
        case THERMOBARIC, NUCLEAR -> ThermalBlast.read(type, nbt, radius);
        case EXOTHERMIC, ENDOTHERMIC -> BeamBlast.read(type, nbt, radius);
        case ANTI_GRAVITATIONAL -> AntiGravityBlast.read(nbt, radius);
        case ENDER -> EnderBlast.read(nbt, radius);
        default -> null;
      };
    } catch (RuntimeException malformed) {
      return null;
    }
  }
}
