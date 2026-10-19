package net.mystcraft.smoke;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Real forced-chunk tickets and subsequent server ticks prepare otherwise playerless test pads. */
public final class SmokeRegionPreparation {
    private static final Map<MinecraftServer, Pending> PENDING = new HashMap<>();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("mystcraft_smoke",
            Comparator.comparingLong(ChunkPos::toLong));
    private SmokeRegionPreparation() {}

    public static void begin(MinecraftServer server, ServerLevel origin, Runnable ready, Consumer<Throwable> failed) {
        beginPhased(server, origin, () -> { ready.run(); return true; }, failed);
    }

    public static void beginPhased(MinecraftServer server, ServerLevel origin,
                                   java.util.function.BooleanSupplier ready, Consumer<Throwable> failed) {
        beginPhased(server, origin, ready, failed, () -> {});
    }

    public static void beginPhased(MinecraftServer server, ServerLevel origin,
                                   java.util.function.BooleanSupplier ready, Consumer<Throwable> failed, Runnable cleanup) {
        if (PENDING.containsKey(server)) throw new IllegalStateException("A Mystcraft smoke run is already preparing or running");
        Pending pending = new Pending(server.getTickCount(), ready, failed, cleanup);
        try {
            // Linking, descriptive-book departures, travel modifiers and the Forge veto fixture.
            for (int[] range : new int[][]{{29, 29, 36, 36}, {48, 48, 48, 48}, {204, 204, 212, 212}, {110, 110, 114, 114}, {287, 287, 289, 289}, {350, 350, 358, 354}, {3373, 3373, 3395, 3395}, {3431, 3431, 3474, 3474}, {3486, 3486, 3490, 3490},
                    {100010, 100010, 100010, 100010}, {100020, 100010, 100020, 100010},
                    {100030, 100030, 100030, 100030}, {100040, 100040, 100040, 100040},
                    {100050, 100049, 100054, 100050}, {100080, 100079, 100146, 100080},
                    {100402, 100402, 100412, 100412}, {100437, 100445, 100463, 100455}}) {
                add(pending, origin, range[0], range[1], range[2], range[3]);
            }
            ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
            if (target == null) throw new IllegalStateException("Smoke fixtures require a real second dimension");
            add(pending, target, 77, 77, 84, 84);
            add(pending, target, 220, 220, 228, 228);
            add(pending, target, 126, 126, 130, 130);
            add(pending, target, 366, 366, 370, 370);
            BlockPos from = origin.getSharedSpawnPos(), to = target.getSharedSpawnPos();
            add(pending, origin, from.getX() - 7, from.getZ() - 1, from.getX() + 2, from.getZ() + 8);
            add(pending, target, to.getX() - 6, to.getZ() - 1, to.getX() + 2, to.getZ() + 7);
            PENDING.put(server, pending);
        } catch (Throwable failure) {
            pending.close(); throw new IllegalStateException("Could not prepare smoke fixture chunks", failure);
        }
    }

    /** Called by the loader's END_SERVER_TICK hook, never by a blocking wait loop. */
    public static void tick(MinecraftServer server) {
        StaticSymbolSmoke.tick(server);
        Pending pending = PENDING.get(server);
        if (pending == null || server.getTickCount() - pending.startTick < 2) return;
        try {
            boolean ready = true;
            for (Lease lease : pending.running ? java.util.List.<Lease>of() : pending.leases.values()) {
                PersistentEntitySectionManager<Entity> manager = pending.managers.get(lease.level);
                if (!manager.canPositionTick(lease.chunk) || !manager.areEntitiesLoaded(lease.chunk.toLong())) {
                    ready = false; break;
                }
            }
            if (!ready) {
                if (server.getTickCount() - pending.startTick > 2400) throw new AssertionError("Smoke fixture chunks did not become entity-ticking after 2400 real ticks");
                return;
            }
            pending.running = true;
            pending.finished = pending.ready.getAsBoolean();
        } catch (Throwable failure) {
            pending.finished = true;
            pending.failed.accept(failure);
        } finally {
            if (pending.finished) {
                PENDING.remove(server);
                pending.close();
            }
        }
    }

    public static void stop(MinecraftServer server) {
        StaticSymbolSmoke.stop(server);
        Pending pending = PENDING.remove(server);
        if (pending != null) pending.close();
    }

    private static void add(Pending pending, ServerLevel level, int minX, int minZ, int maxX, int maxZ) throws ReflectiveOperationException {
        if (!pending.managers.containsKey(level)) pending.managers.put(level, manager(level));
        for (int x = minX >> 4; x <= maxX >> 4; x++) for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
            ChunkPos chunk = new ChunkPos(x, z);
            Key key = new Key(level, chunk.toLong());
            if (pending.leases.containsKey(key)) continue;
            pending.leases.put(key, new Lease(level, chunk));
            // Radius two produces ticket level 31 (ENTITY_TICKING), just like /forceload,
            // without its synchronous getChunk call or changes to persistent forced chunks.
            level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
        }
    }

    @SuppressWarnings("unchecked")
    private static PersistentEntitySectionManager<Entity> manager(ServerLevel level) throws ReflectiveOperationException {
        for (Field field : ServerLevel.class.getDeclaredFields()) {
            if (PersistentEntitySectionManager.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true); return (PersistentEntitySectionManager<Entity>) field.get(level);
            }
        }
        throw new IllegalStateException("Smoke could not locate vanilla entity section manager");
    }

    private record Key(ServerLevel level, long chunk) {}
    private record Lease(ServerLevel level, ChunkPos chunk) {}
    private static final class Pending {
        private final int startTick;
        private final java.util.function.BooleanSupplier ready;
        private final Consumer<Throwable> failed;
        private final Runnable cleanup;
        private final Map<Key, Lease> leases = new LinkedHashMap<>();
        private final Map<ServerLevel, PersistentEntitySectionManager<Entity>> managers = new HashMap<>();
        private boolean running, finished;
        Pending(int tick, java.util.function.BooleanSupplier ready, Consumer<Throwable> failed, Runnable cleanup) { startTick = tick; this.ready = ready; this.failed = failed; this.cleanup=cleanup; }
        void close() {
            try { cleanup.run(); }
            finally { for (Lease lease : leases.values()) lease.level.getChunkSource().removeRegionTicket(TICKET, lease.chunk, 2, lease.chunk); }
        }
    }
}
