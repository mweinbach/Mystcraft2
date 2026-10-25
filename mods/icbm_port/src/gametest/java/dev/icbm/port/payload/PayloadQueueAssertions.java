package dev.icbm.port.payload;

import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;

/** Exercises the real persisted queue while keeping test jobs out of the world's live queue. */
public final class PayloadQueueAssertions {
  private PayloadQueueAssertions() {}

  private static void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  public static void removeTestDebilitation(TestContext context, Vec3d center) {
    var queue = Payloads.state(context.getWorld());
    queue.jobs.removeIf(
        j -> j.type == PayloadType.DEBILITATION && j.center.squaredDistanceTo(center) < .001);
    queue.pending.removeIf(
        j -> j.type == PayloadType.DEBILITATION && j.center.squaredDistanceTo(center) < .001);
    queue.markDirty();
  }

  public static void savedTerrainWorkResumes(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
    context.getWorld().setBlockState(pos.up(), Blocks.BEDROCK.getDefaultState(), 2);
    var original = new Payloads.QueueState();
    var job = new Payloads.Job(PayloadType.ENDOTHERMIC, Vec3d.ofCenter(pos), 2);
    // The center is cube cursor62. Stop immediately before it would be processed.
    check(context, !job.tick(context.getWorld(), 62), "Partial terrain work must remain pending");
    check(context, job.cursor == 62, "Job must retain its exact next candidate cursor");
    check(
        context,
        context.getWorld().getBlockState(pos).isOf(Blocks.WATER),
        "Unvisited center must remain water");
    original.enqueue(job, false);
    NbtCompound saved = original.writeNbt(new NbtCompound());
    var restored = Payloads.QueueState.read(saved);
    check(
        context,
        restored.jobs.size() == 1
            && restored.jobs.get(0).cursor == 62
            && restored.jobs.get(0).age == 1,
        "Queue save/load must retain position and age");
    restored.tick(context.getWorld());
    check(
        context,
        context.getWorld().getBlockState(pos).isOf(Blocks.ICE),
        "Resumed endothermic work must freeze the unvisited water");
    check(
        context,
        context.getWorld().getBlockState(pos.up()).isOf(Blocks.BEDROCK),
        "Terrain work must preserve unbreakable blocks");
    check(context, restored.jobs.isEmpty(), "Completed restored work must leave the queue");
    context.complete();
  }

  public static void worldBudgetBoundsWork(TestContext context) {
    var state = new Payloads.QueueState();
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    for (int i = 0; i < 9; i++) state.enqueue(new Payloads.Job(PayloadType.ROT, pos, 8), false);
    state.tick(context.getWorld());
    int visited = state.jobs.stream().mapToInt(j -> j.cursor).sum();
    check(
        context,
        visited == 4096,
        "A queue tick must visit exactly4096 candidates when work exceeds budget");
    for (int i = 0; i < 8; i++)
      check(
          context,
          state.jobs.get(i).cursor == 512,
          "Each served job must respect512-candidate share");
    check(
        context,
        state.jobs.get(8).cursor == 0 && state.jobs.get(8).age == 0,
        "Work beyond the world tick budget must wait");
    state.tick(context.getWorld());
    check(
        context,
        state.jobs.get(8).cursor == 512 && state.jobs.get(8).age == 1,
        "Round-robin scheduling must serve previously waiting work on the next tick");
    context.complete();
  }

  public static void malformedSaveAndDeferredAdmissionAreBounded(TestContext context) {
    var state = new Payloads.QueueState();
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    for (int i = 0; i < 255; i++)
      check(
          context,
          state.enqueue(new Payloads.Job(PayloadType.ROT, pos, 2), false),
          "Expected queue slot");
    check(
        context,
        state.enqueue(new Payloads.Job(PayloadType.ROT, pos, 2), true),
        "Last deferred slot must be accepted");
    check(
        context,
        !state.enqueue(new Payloads.Job(PayloadType.ROT, pos, 2), true),
        "Deferred jobs must count toward total cap");
    check(
        context,
        !state.enqueue(new Payloads.Job(PayloadType.ROT, pos, 2), false),
        "Immediate jobs must share deferred admission cap");
    var saved = state.writeNbt(new NbtCompound());
    check(
        context, saved.getList("jobs", 10).size() == 256, "Deferred work must also be serialized");
    NbtList malformed = new NbtList();
    NbtCompound valid = saved.getList("jobs", 10).getCompound(0).copy();
    valid.putInt("cursor", Integer.MAX_VALUE);
    valid.putInt("age", Integer.MAX_VALUE);
    valid.putInt("elapsedTicks", Integer.MAX_VALUE);
    malformed.add(valid);
    for (double coordinate :
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 30000001}) {
      NbtCompound invalid = valid.copy();
      invalid.putDouble("x", coordinate);
      malformed.add(invalid);
    }
    NbtCompound unknown = valid.copy();
    unknown.putString("type", "UNKNOWN_PAYLOAD");
    malformed.add(unknown);
    NbtCompound root = new NbtCompound();
    root.put("jobs", malformed);
    var restored = Payloads.QueueState.read(root);
    check(
        context,
        restored.jobs.size() == 1,
        "Malformed coordinates and unknown types must not enter queue");
    check(
        context,
        restored.jobs.get(0).cursor == 125
            && restored.jobs.get(0).age == 72000
            && restored.jobs.get(0).elapsedTicks == 72000,
        "Persisted progress must clamp to valid cube/lifetime limits");
    context.complete();
  }

  public static void loadedQueuesKeepRealTimeFieldLifetimes(TestContext context) {
    var state = new Payloads.QueueState();
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    // Radius-0 gas clouds: timed by world ticks like upstream BlastGasBase, with no block or
    // entity work, so a full queue of them isolates lifetime bookkeeping.
    int duration = PayloadConfig.forType(PayloadType.DEBILITATION).durationTicks();
    for (int i = 0; i < 256; i++)
      state.enqueue(new Payloads.Job(PayloadType.DEBILITATION, pos, 0), false);
    for (int tick = 0; tick < duration - 1; tick++) state.tick(context.getWorld());
    check(
        context,
        state.jobs.size() == 256,
        "Fields must remain before configured world-tick expiry");
    check(
        context,
        state.jobs.stream().allMatch(j -> j.elapsedTicks == duration - 1 && j.age < j.elapsedTicks),
        "Unserved fields must also age every server tick");
    NbtCompound saved = state.writeNbt(new NbtCompound());
    var restored = Payloads.QueueState.read(saved);
    check(
        context,
        restored.jobs.stream().allMatch(j -> j.elapsedTicks == duration - 1),
        "Save/load must preserve elapsed lifetime independently of service age");
    // A cloud ends on its first run past the duration (t + 1 > duration). Eight jobs are served per
    // tick, so the first eight (served at t = duration) come round again on tick 33.
    for (int tick = 0; tick < 33; tick++) restored.tick(context.getWorld());
    check(
        context,
        restored.jobs.isEmpty(),
        "All expired fields must retire within 33 ticks under full queue load");
    NbtCompound legacy = saved.getList("jobs", 10).getCompound(0).copy();
    legacy.remove("elapsedTicks");
    NbtList entries = new NbtList();
    entries.add(legacy);
    NbtCompound legacyRoot = new NbtCompound();
    legacyRoot.put("jobs", entries);
    var legacyRestored = Payloads.QueueState.read(legacyRoot).jobs.get(0);
    check(
        context,
        legacyRestored.elapsedTicks == legacyRestored.age,
        "Legacy saved work must retain its prior age as lifetime fallback");
    context.complete();
  }
}
