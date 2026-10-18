package dev.icbm.port.delivery;

import dev.icbm.port.payload.PayloadType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Immutable, bounded payload manifest adapted from ActionDataCluster's spawn entries. */
public final class ClusterPlan {
  public static final int MAX_BOMBLETS = 64;
  public static final ClusterPlan EMPTY = new ClusterPlan(List.of());
  public static final String NBT_KEY = "ClusterEntries";
  private final List<PayloadType> payloads;

  public ClusterPlan(List<PayloadType> payloads) {
    this.payloads =
        List.copyOf(
            payloads.stream()
                .filter(p -> p.hasContent() && p.tier() == 1)
                .limit(MAX_BOMBLETS)
                .toList());
  }

  public List<PayloadType> payloads() {
    return payloads;
  }

  public int size() {
    return payloads.size();
  }

  public boolean isEmpty() {
    return payloads.isEmpty();
  }

  public static ClusterPlan fromStack(ItemStack stack) {
    return stack.isOf(DeliveryContent.CLUSTER_MISSILE) && stack.hasNbt()
        ? read(stack.getNbt())
        : EMPTY;
  }

  public static ClusterPlan read(NbtCompound nbt) {
    List<PayloadType> payloads = new ArrayList<>();
    NbtList entries = nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE);
    for (int i = 0;
        i < Math.min(entries.size(), MAX_BOMBLETS) && payloads.size() < MAX_BOMBLETS;
        i++) {
      NbtCompound entry = entries.getCompound(i);
      String id = entry.getString("Payload");
      PayloadType payload = null;
      for (PayloadType candidate : PayloadType.values())
        if (candidate.id().equals(id) && candidate.hasContent() && candidate.tier() == 1) {
          payload = candidate;
          break;
        }
      if (payload == null) continue;
      int count = Math.min(MAX_BOMBLETS - payloads.size(), Math.max(0, entry.getInt("Count")));
      for (int j = 0; j < count; j++) payloads.add(payload);
    }
    return payloads.isEmpty() ? EMPTY : new ClusterPlan(payloads);
  }

  public void writeTo(NbtCompound nbt) {
    NbtList entries = new NbtList();
    // One entry per droplet preserves mixed loading order, as upstream does.
    for (PayloadType payload : payloads) {
      NbtCompound entry = new NbtCompound();
      entry.putString("Payload", payload.id());
      entry.putInt("Count", 1);
      entries.add(entry);
    }
    nbt.put(NBT_KEY, entries);
  }

  public void writeTo(ItemStack stack) {
    writeTo(stack.getOrCreateNbt());
  }
}
