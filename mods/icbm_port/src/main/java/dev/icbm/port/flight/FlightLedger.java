package dev.icbm.port.flight;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/** Durable, bounded virtual flight; never force-loads destination chunks or detonates offline. */
public final class FlightLedger extends PersistentState {
  private static final int MAX_PENDING = 256, MAX_IDENTITIES = 8192;
  private static final net.minecraft.server.world.ChunkTicketType<UUID> REENTRY_TICKET =
      net.minecraft.server.world.ChunkTicketType.create("icbm_reentry", UUID::compareTo, 1200);

  private record Ticket(net.minecraft.util.math.ChunkPos chunk, long expires) {}

  private final Map<UUID, Ticket> tickets = new LinkedHashMap<>();
  private final Map<UUID, Integer> generations = new LinkedHashMap<>();
  private final Map<UUID, Entry> pending = new LinkedHashMap<>();

  private static final class Entry {
    NbtCompound entity;
    BlockPos target;
    int ticks, age, generation, attempts;

    Entry(NbtCompound entity, BlockPos target, int ticks, int generation) {
      this.entity = entity;
      this.target = target;
      this.ticks = ticks;
      this.generation = generation;
    }
  }

  public static FlightLedger get(ServerWorld world) {
    return world
        .getPersistentStateManager()
        .getOrCreate(FlightLedger::read, FlightLedger::new, "icbm_flights");
  }

  public int pendingCount() {
    return pending.size();
  }

  public int removeNear(ServerWorld world, Vec3d center, double radius) {
    double squared = Math.max(0, radius) * Math.max(0, radius);
    int count = 0;
    var iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      var record = iterator.next();
      Entry entry = record.getValue();
      NbtList position = entry.entity.getList("Pos", NbtElement.DOUBLE_TYPE);
      boolean near = Vec3d.ofCenter(entry.target).squaredDistanceTo(center) <= squared;
      if (position.size() == 3)
        near |=
            new Vec3d(position.getDouble(0), position.getDouble(1), position.getDouble(2))
                    .squaredDistanceTo(center)
                <= squared;
      if (near) {
        iterator.remove();
        Ticket ticket = tickets.remove(record.getKey());
        if (ticket != null)
          world.getChunkManager().removeTicket(REENTRY_TICKET, ticket.chunk(), 2, record.getKey());
        count++;
      }
    }
    if (count > 0) markDirty();
    return count;
  }

  public int generation(UUID uuid) {
    return generations.getOrDefault(uuid, 0);
  }

  public static boolean suppressDuplicate(Entity entity, StagedFlight plan) {
    if (!(entity.world instanceof ServerWorld world)) return false;
    FlightLedger ledger = get(world);
    int newest = ledger.generation(entity.getUuid());
    return newest > plan.generation() || ledger.pending.containsKey(entity.getUuid());
  }

  public static boolean tryCapture(Entity entity, StagedFlight plan) {
    if (!(entity.world instanceof ServerWorld world)
        || !plan.targeted()
        || entity.hasPassengers()
        || entity.hasVehicle()) return false;
    return get(world).capture(entity, plan);
  }

  private boolean capture(Entity entity, StagedFlight plan) {
    UUID uuid = entity.getUuid();
    if (pending.size() >= MAX_PENDING
        || (!generations.containsKey(uuid) && generations.size() >= MAX_IDENTITIES)
        || pending.containsKey(uuid)) return false;
    int old = plan.generation(), next = Math.max(old, generation(uuid)) + 1;
    if (next <= 0) return false;
    plan.setGeneration(next);
    NbtCompound snapshot = new NbtCompound();
    if (!entity.saveNbt(snapshot)) {
      plan.setGeneration(old);
      return false;
    }
    int ticks =
        Math.max(
            1,
            (int)
                Math.ceil(
                    Math.hypot(
                            plan.target().getX() + .5 - entity.getX(),
                            plan.target().getZ() + .5 - entity.getZ())
                        / FlightProfile.SIMULATION_SPEED));
    generations.put(uuid, next);
    pending.put(uuid, new Entry(snapshot, plan.target(), ticks, next));
    markDirty();
    entity.discard();
    return true;
  }

  public void tick(ServerWorld world) {
    var ticketIterator = tickets.entrySet().iterator();
    while (ticketIterator.hasNext()) {
      var ticket = ticketIterator.next();
      if (world.getTime() >= ticket.getValue().expires()
          || (!pending.containsKey(ticket.getKey()) && world.getEntity(ticket.getKey()) == null)) {
        world
            .getChunkManager()
            .removeTicket(REENTRY_TICKET, ticket.getValue().chunk(), 2, ticket.getKey());
        ticketIterator.remove();
      }
    }
    int spawned = 0;
    var iterator = pending.entrySet().iterator();
    while (iterator.hasNext()) {
      var record = iterator.next();
      Entry entry = record.getValue();
      entry.age++;
      if (entry.age % 20 == 0) markDirty();
      if (entry.ticks > 0) {
        entry.ticks--;
        markDirty();
        continue;
      }
      // Pending unloaded destinations are inert, bounded and durable; retain generation tombstone
      // on expiry.
      if (entry.age > FlightSettings.current.pendingLifetimeTicks
          || world.isOutOfHeightLimit(entry.target)
          || !world.getWorldBorder().contains(entry.target)) {
        iterator.remove();
        markDirty();
        continue;
      }
      if (FlightSettings.current.temporaryReentryTickets
          && !tickets.containsKey(record.getKey())
          && tickets.size() < FlightSettings.current.maxActiveTickets
          && entry.attempts < 3) {
        var chunk = new net.minecraft.util.math.ChunkPos(entry.target);
        world.getChunkManager().addTicket(REENTRY_TICKET, chunk, 2, record.getKey());
        tickets.put(record.getKey(), new Ticket(chunk, world.getTime() + 1200));
        entry.attempts++;
        markDirty();
      }
      if (spawned >= 4 || !world.isChunkLoaded(entry.target)) continue;
      Entity existing = world.getEntity(record.getKey());
      if (existing != null && existing.isRemoved()) existing = null;
      if (existing != null) {
        if (existing instanceof FlightTracked tracked
            && tracked.flightPlan().generation() >= entry.generation) {
          iterator.remove();
          markDirty();
        } else existing.discard();
        continue;
      }
      Entity entity =
          EntityType.loadEntityWithPassengers(entry.entity.copy(), world, value -> value);
      if (!(entity instanceof FlightTracked tracked)) {
        iterator.remove();
        markDirty();
        continue;
      }
      if (!tracked.flightPlan().targeted()
          || !entry.target.equals(tracked.flightPlan().target())
          || tracked.flightPlan().generation() != entry.generation) {
        iterator.remove();
        markDirty();
        continue;
      }
      Vec3d position =
          new Vec3d(
              entry.target.getX() + .5,
              Math.min(
                  world.getTopY() - 2,
                  Math.max(entry.target.getY() + 1, FlightProfile.enterHeight(world))),
              entry.target.getZ() + .5);
      BlockPos reentry = new BlockPos(position);
      if (world.isOutOfHeightLimit(reentry)
          || !world.isChunkLoaded(reentry)
          || !world.getBlockState(reentry).getCollisionShape(world, reentry).isEmpty()) continue;
      tracked.flightPlan().prepareReentry(position);
      entity.setPosition(position);
      entity.setVelocity(0, -FlightProfile.REENTRY_SPEED, 0);
      if (world.spawnEntity(entity)) {
        spawned++;
        iterator.remove();
        markDirty();
      }
    }
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt) {
    NbtList identities = new NbtList();
    generations.forEach(
        (uuid, generation) -> {
          NbtCompound value = new NbtCompound();
          value.putUuid("UUID", uuid);
          value.putInt("Generation", generation);
          identities.add(value);
        });
    nbt.put("Identities", identities);
    NbtList flights = new NbtList();
    pending.forEach(
        (uuid, entry) -> {
          NbtCompound value = new NbtCompound();
          value.putUuid("UUID", uuid);
          value.put("Entity", entry.entity.copy());
          value.putLong("Target", entry.target.asLong());
          value.putInt("Ticks", entry.ticks);
          value.putInt("Age", entry.age);
          value.putInt("Generation", entry.generation);
          value.putInt("Attempts", entry.attempts);
          flights.add(value);
        });
    nbt.put("Pending", flights);
    return nbt;
  }

  public static FlightLedger read(NbtCompound nbt) {
    FlightLedger ledger = new FlightLedger();
    NbtList identities = nbt.getList("Identities", NbtElement.COMPOUND_TYPE);
    for (int i = 0; i < Math.min(MAX_IDENTITIES, identities.size()); i++) {
      NbtCompound value = identities.getCompound(i);
      if (value.containsUuid("UUID"))
        ledger.generations.put(value.getUuid("UUID"), Math.max(1, value.getInt("Generation")));
    }
    NbtList flights = nbt.getList("Pending", NbtElement.COMPOUND_TYPE);
    for (int i = 0; i < Math.min(MAX_PENDING, flights.size()); i++) {
      NbtCompound value = flights.getCompound(i);
      if (!value.containsUuid("UUID")) continue;
      UUID uuid = value.getUuid("UUID");
      int generation = Math.max(1, value.getInt("Generation"));
      if (!ledger.generations.containsKey(uuid)
          || ledger.generation(uuid) != generation
          || !value.contains("Entity", NbtElement.COMPOUND_TYPE)) continue;
      NbtCompound entity = value.getCompound("Entity");
      if (!entity.containsUuid("UUID") || !entity.getUuid("UUID").equals(uuid)) continue;
      Entry entry =
          new Entry(
              entity.copy(),
              BlockPos.fromLong(value.getLong("Target")),
              Math.max(0, Math.min(12000, value.getInt("Ticks"))),
              generation);
      entry.age = Math.max(0, Math.min(72001, value.getInt("Age")));
      entry.attempts = Math.max(0, Math.min(3, value.getInt("Attempts")));
      ledger.pending.put(uuid, entry);
    }
    return ledger;
  }
}
