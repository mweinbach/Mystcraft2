package dev.icbm.port.defense;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;

/** Per-dimension bounded EMP fields. Absolute game time preserves remaining duration on reload. */
public final class EmpPersistentState extends PersistentState {
  private record Zone(Vec3d center, double radiusSquared, long expiry) {}

  private final List<Zone> zones = new ArrayList<>();

  public static EmpPersistentState get(ServerWorld world) {
    return world
        .getPersistentStateManager()
        .getOrCreate(EmpPersistentState::fromNbt, EmpPersistentState::new, "icbm_emp_fields");
  }

  public void add(Vec3d center, double radius, long time) {
    if (!Double.isFinite(center.x)
        || !Double.isFinite(center.y)
        || !Double.isFinite(center.z)
        || !Double.isFinite(radius)
        || radius <= 0) return;
    prune(time);
    if (zones.size() >= 256) zones.remove(0);
    double bounded = Math.min(512, radius);
    zones.add(new Zone(center, bounded * bounded, time + 200));
    markDirty();
  }

  private void prune(long time) {
    if (zones.removeIf(zone -> zone.expiry <= time)) markDirty();
  }

  public boolean contains(Vec3d position, long time) {
    prune(time);
    return zones.stream()
        .anyMatch(zone -> zone.center.squaredDistanceTo(position) <= zone.radiusSquared);
  }

  public int size() {
    return zones.size();
  }

  public static EmpPersistentState fromNbt(NbtCompound nbt) {
    EmpPersistentState state = new EmpPersistentState();
    NbtList saved = nbt.getList("Zones", NbtElement.COMPOUND_TYPE);
    for (int index = 0; index < Math.min(256, saved.size()); index++) {
      NbtCompound zone = saved.getCompound(index);
      double x = zone.getDouble("X"),
          y = zone.getDouble("Y"),
          z = zone.getDouble("Z"),
          radius = zone.getDouble("RadiusSquared");
      long expiry = zone.getLong("Expiry");
      if (Double.isFinite(x)
          && Double.isFinite(y)
          && Double.isFinite(z)
          && Double.isFinite(radius)
          && radius > 0
          && radius <= 512 * 512
          && expiry >= 0) state.zones.add(new Zone(new Vec3d(x, y, z), radius, expiry));
    }
    return state;
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt) {
    NbtList saved = new NbtList();
    for (Zone zone : zones) {
      NbtCompound entry = new NbtCompound();
      entry.putDouble("X", zone.center.x);
      entry.putDouble("Y", zone.center.y);
      entry.putDouble("Z", zone.center.z);
      entry.putDouble("RadiusSquared", zone.radiusSquared);
      entry.putLong("Expiry", zone.expiry);
      saved.add(entry);
    }
    nbt.put("Zones", saved);
    return nbt;
  }
}
