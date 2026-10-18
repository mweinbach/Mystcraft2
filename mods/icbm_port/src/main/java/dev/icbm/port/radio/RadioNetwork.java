package dev.icbm.port.radio;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Loaded, dimension-local channel registry. Relays forward with bounded hops and loop suppression.
 */
public final class RadioNetwork {
  public record Incoming(UUID id, UUID target, BlockPos origin, int channel) {}

  public interface Receiver {
    BlockPos radioPosition();

    int radioChannel();

    boolean radioEnabled();

    /** Return true to relay this message from the receiving position. */
    boolean receive(Incoming message);
  }

  private static final Map<ServerWorld, Map<BlockPos, WeakReference<Receiver>>> RECEIVERS =
      new WeakHashMap<>();

  private RadioNetwork() {}

  public static void register(ServerWorld world, Receiver receiver) {
    RECEIVERS
        .computeIfAbsent(world, ignored -> new HashMap<>())
        .put(receiver.radioPosition().toImmutable(), new WeakReference<>(receiver));
  }

  public static void remove(ServerWorld world, BlockPos pos) {
    var receivers = RECEIVERS.get(world);
    if (receivers != null) receivers.remove(pos);
  }

  public static int broadcast(ServerWorld world, Receiver sender, UUID target) {
    if (!sender.radioEnabled() || sender.radioChannel() < 1) return 0;
    var receivers = RECEIVERS.get(world);
    if (receivers == null) return 0;
    receivers
        .entrySet()
        .removeIf(entry -> entry.getValue().get() == null || !world.isChunkLoaded(entry.getKey()));
    Incoming message =
        new Incoming(
            UUID.randomUUID(), target, sender.radioPosition().toImmutable(), sender.radioChannel());
    record Hop(BlockPos pos, int depth) {}
    ArrayDeque<Hop> queue = new ArrayDeque<>();
    queue.add(new Hop(sender.radioPosition(), 0));
    Set<BlockPos> visited = new HashSet<>();
    visited.add(sender.radioPosition());
    int delivered = 0;
    while (!queue.isEmpty() && delivered < 256) {
      Hop hop = queue.remove();
      for (var entry : List.copyOf(receivers.entrySet())) {
        Receiver receiver = entry.getValue().get();
        if (receiver == null
            || visited.contains(entry.getKey())
            || !receiver.radioEnabled()
            || receiver.radioChannel() != message.channel()
            || hop.pos.getSquaredDistance(entry.getKey()) > 512D * 512D) continue;
        visited.add(entry.getKey());
        delivered++;
        boolean relay = receiver.receive(message);
        if (relay && hop.depth < 3) queue.add(new Hop(entry.getKey(), hop.depth + 1));
        if (delivered >= 256) break;
      }
    }
    return delivered;
  }
}
