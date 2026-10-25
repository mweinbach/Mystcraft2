package dev.icbm.port.radio;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Per-dimension radio channel registry adapted from MIT ICBM-Classic {@code lib/radio/RadioRegistry}
 * and {@code RadioMap}.
 *
 * <p>Upstream tile radios ({@code RadioTile#getRange}) use {@code RadioRegistry.INFINITE}, so every
 * registered receiver in the sender's dimension hears every message on its channel regardless of
 * distance. This registry keeps that rule: delivery is dimension-wide, filtered by channel, the
 * receiver's disabled flag and "not the sender itself" ({@code RadioTile#canReceive}). Receivers
 * are added when their block entity loads and removed when it unloads or is removed ({@code
 * onLoad}/{@code invalidate} upstream, Fabric {@link ServerBlockEntityEvents} here), and a world's
 * map is dropped when the world unloads ({@code RadioRegistry#worldUnload}) or the server stops, so
 * nothing survives a reload. Radio never loads chunks: only loaded block entities are registered.
 *
 * <p>Port bounds: at most {@link #MAX_DELIVERIES} deliveries per message and at most {@link
 * #MAX_DEPTH} nested broadcasts (a receiver answering by broadcasting), so a misconfigured channel
 * cannot recurse without limit.
 */
public final class RadioNetwork {
  public static final int MAX_CHANNEL = 9999;
  public static final int MAX_DELIVERIES = 256;
  public static final int MAX_DEPTH = 4;

  /** Shared radio identity (upstream {@code IRadio} + {@code IRadioChannelAccess}). */
  public interface Radio {
    BlockPos radioPosition();

    int radioChannel();
  }

  /** Upstream {@code IRadioSender}: receives {@link RadioMessage.Text} callbacks. */
  public interface Sender extends Radio {
    default void onCallback(Receiver from, RadioMessage.Text text) {}
  }

  /** Upstream {@code IRadioReceiver}. */
  public interface Receiver extends Radio {
    /** False when the radio is disabled (upstream {@code Radio#isDisabled}). */
    boolean radioEnabled();

    void onMessage(Sender sender, RadioMessage message);
  }

  /** Item radios act through the holding player, like upstream {@code FakeRadioSender}. */
  public record PlayerSender(PlayerEntity player, int channel) implements Sender {
    @Override
    public BlockPos radioPosition() {
      return player.getBlockPos();
    }

    @Override
    public int radioChannel() {
      return channel;
    }

    @Override
    public void onCallback(Receiver from, RadioMessage.Text text) {
      player.sendMessage(net.minecraft.text.Text.translatable(text.key(), text.args()), true);
    }
  }

  private static final Map<ServerWorld, LinkedHashMap<BlockPos, Receiver>> RECEIVERS =
      new WeakHashMap<>();
  private static int depth;
  private static boolean lifecycleRegistered;

  private RadioNetwork() {}

  public static boolean validChannel(int channel) {
    return channel >= 1 && channel <= MAX_CHANNEL;
  }

  /** Hook the registry to block entity, world and server lifecycle (called once at init). */
  public static void registerLifecycle() {
    if (lifecycleRegistered) return;
    lifecycleRegistered = true;
    ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(
        (blockEntity, world) -> {
          if (blockEntity instanceof Receiver receiver) register(world, receiver);
        });
    ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(
        (blockEntity, world) -> {
          if (blockEntity instanceof Receiver receiver) remove(world, receiver);
        });
    ServerWorldEvents.UNLOAD.register((server, world) -> RECEIVERS.remove(world));
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> RECEIVERS.clear());
  }

  public static void register(ServerWorld world, Receiver receiver) {
    RECEIVERS
        .computeIfAbsent(world, ignored -> new LinkedHashMap<>())
        .put(receiver.radioPosition().toImmutable(), receiver);
  }

  public static void remove(ServerWorld world, BlockPos pos) {
    var receivers = RECEIVERS.get(world);
    if (receivers != null) receivers.remove(pos);
  }

  /** Remove only if {@code receiver} is still the radio registered at its position. */
  public static void remove(ServerWorld world, Receiver receiver) {
    var receivers = RECEIVERS.get(world);
    if (receivers != null) receivers.remove(receiver.radioPosition(), receiver);
  }

  public static boolean isRegistered(ServerWorld world, Receiver receiver) {
    var receivers = RECEIVERS.get(world);
    return receivers != null && receivers.get(receiver.radioPosition()) == receiver;
  }

  /** Number of live receivers registered in {@code world}, after pruning stale entries. */
  public static int registered(ServerWorld world) {
    var receivers = RECEIVERS.get(world);
    if (receivers == null) return 0;
    prune(world, receivers);
    return receivers.size();
  }

  /** Upstream {@code RadioTile#canReceive}: enabled, same channel and not the sender itself. */
  public static boolean canReceive(Receiver receiver, Sender sender, RadioMessage message) {
    return receiver != sender
        && receiver.radioEnabled()
        && validChannel(message.channel())
        && receiver.radioChannel() == message.channel();
  }

  /**
   * Deliver {@code message} to every loaded receiver on its channel in {@code world} (upstream
   * {@code RadioRegistry.popMessage}). Returns the number of receivers that were handed the
   * message.
   */
  public static int broadcast(ServerWorld world, Sender sender, RadioMessage message) {
    if (world == null
        || sender == null
        || message == null
        || !validChannel(message.channel())
        || depth >= MAX_DEPTH) return 0;
    var receivers = RECEIVERS.get(world);
    if (receivers == null) return 0;
    prune(world, receivers);
    List<Receiver> snapshot = new ArrayList<>(receivers.values());
    int delivered = 0;
    depth++;
    try {
      for (Receiver receiver : snapshot) {
        if (delivered >= MAX_DELIVERIES) break;
        if (!canReceive(receiver, sender, message)) continue;
        if (receiver instanceof BlockEntity entity && entity.isRemoved()) continue;
        delivered++;
        receiver.onMessage(sender, message);
      }
    } finally {
      depth--;
    }
    return delivered;
  }

  /** Send a translated text callback to {@code sender} (upstream {@code onMessageCallback}). */
  public static void reply(Sender sender, Receiver receiver, String key, Object... args) {
    if (sender != null)
      sender.onCallback(receiver, new RadioMessage.Text(receiver.radioChannel(), key, args));
  }

  private static void prune(ServerWorld world, Map<BlockPos, Receiver> receivers) {
    for (Iterator<Map.Entry<BlockPos, Receiver>> it = receivers.entrySet().iterator();
        it.hasNext(); ) {
      var entry = it.next();
      Receiver receiver = entry.getValue();
      if (!world.isChunkLoaded(entry.getKey())
          || (receiver instanceof BlockEntity entity
              && (entity.isRemoved() || world.getBlockEntity(entry.getKey()) != entity)))
        it.remove();
    }
  }
}
