package dev.icbm.port.exposure;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** Fallout replacement cursor survives server restart; chunk processing remains loaded-only. */
public final class FalloutState extends PersistentState {
  public record Job(BlockPos origin, int radius, long cursor) {
    int diameter() {
      return radius * 2 + 1;
    }

    long volume() {
      long d = diameter();
      return d * d * d;
    }
  }

  final Deque<Job> jobs = new ArrayDeque<>();

  public static FalloutState get(ServerWorld world) {
    return world
        .getPersistentStateManager()
        .getOrCreate(FalloutState::read, FalloutState::new, "icbm_port_fallout");
  }

  static FalloutState read(NbtCompound nbt) {
    FalloutState state = new FalloutState();
    NbtList list = nbt.getList("jobs", 10);
    for (int i = 0; i < Math.min(64, list.size()); i++) {
      NbtCompound n = list.getCompound(i);
      int r = Math.max(1, Math.min(128, n.getInt("radius")));
      Job job =
          new Job(BlockPos.fromLong(n.getLong("origin")), r, Math.max(0, n.getLong("cursor")));
      if (job.cursor() < job.volume()) state.jobs.add(job);
    }
    return state;
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt) {
    NbtList list = new NbtList();
    for (Job job : jobs) {
      NbtCompound n = new NbtCompound();
      n.putLong("origin", job.origin().asLong());
      n.putInt("radius", job.radius());
      n.putLong("cursor", job.cursor());
      list.add(n);
    }
    nbt.put("jobs", list);
    return nbt;
  }
}
