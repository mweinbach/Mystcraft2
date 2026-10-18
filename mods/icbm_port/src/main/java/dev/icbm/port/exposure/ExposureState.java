package dev.icbm.port.exposure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** Persistent overworld UUID exposure records survive entity unload and server restart. */
public final class ExposureState extends PersistentState {
  public static final int MAX_ENTITIES = 65536;

  public static final class Dose {
    public int chemicalHits, viralHits, radiationHits, infectiousTicks;

    public NbtCompound write() {
      NbtCompound n = new NbtCompound();
      n.putInt("chemical", chemicalHits);
      n.putInt("viral", viralHits);
      n.putInt("radiation", radiationHits);
      n.putInt("infectious", infectiousTicks);
      return n;
    }

    public static Dose read(NbtCompound n) {
      Dose d = new Dose();
      d.chemicalHits = Math.max(0, Math.min(100000, n.getInt("chemical")));
      d.viralHits = Math.max(0, Math.min(100000, n.getInt("viral")));
      d.radiationHits = Math.max(0, Math.min(100000, n.getInt("radiation")));
      d.infectiousTicks = Math.max(0, Math.min(12000, n.getInt("infectious")));
      return d;
    }
  }

  final Map<UUID, Dose> doses = new HashMap<>();

  public static ExposureState get(ServerWorld world) {
    return world
        .getServer()
        .getOverworld()
        .getPersistentStateManager()
        .getOrCreate(ExposureState::read, ExposureState::new, "icbm_port_exposure");
  }

  public Dose dose(UUID uuid) {
    Dose existing = doses.get(uuid);
    if (existing != null) {
      markDirty();
      return existing;
    }
    // Preserve admitted records, including unloaded living entities. At capacity,
    // immediate effects still apply using a transient dose; no UUID is evicted.
    Dose fresh = new Dose();
    if (doses.size() < MAX_ENTITIES) {
      doses.put(uuid, fresh);
      markDirty();
    }
    return fresh;
  }

  public Dose find(UUID uuid) {
    return doses.get(uuid);
  }

  public void onRemoval(UUID uuid, net.minecraft.entity.Entity.RemovalReason reason) {
    if (reason != null && reason.shouldDestroy()) clear(uuid);
  }

  public void clear(UUID uuid) {
    doses.remove(uuid);
    markDirty();
  }

  public static ExposureState read(NbtCompound nbt) {
    ExposureState s = new ExposureState();
    NbtList list = nbt.getList("entities", 10);
    for (int i = 0; i < Math.min(list.size(), MAX_ENTITIES); i++) {
      NbtCompound n = list.getCompound(i);
      if (n.containsUuid("uuid")) s.doses.put(n.getUuid("uuid"), Dose.read(n));
    }
    return s;
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt) {
    NbtList list = new NbtList();
    doses.forEach(
        (uuid, dose) -> {
          NbtCompound n = dose.write();
          n.putUuid("uuid", uuid);
          list.add(n);
        });
    nbt.put("entities", list);
    return nbt;
  }
}
