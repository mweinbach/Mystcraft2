package ports.gridwright.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import ports.gridwright.platform.Platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The chunk-loading wards that are holding chunks, server-wide, saved with the overworld. Chunk
 * tickets are not saved by the game, so on a server's first tick every ward listed here gets its
 * tickets back; its chunks load, the ward ticks again, and from then on it decides for itself whether
 * its grid still pays. Entries whose ward has gone (or whose dimension has) are dropped.
 *
 * <p>Each ward holds one ticket per chunk keyed by its own position, so two wards sharing a chunk
 * never release each other's hold.
 */
public final class ChunkWards extends SavedData {

    public static final String NAME = "gridwright_chunk_wards";
    /** Region tickets at distance 2 make a chunk entity-ticking, exactly as {@code /forceload} does. */
    private static final int TICKET_DISTANCE = 2;
    private static final int VALIDATE_TICKS = 200;
    public static final TicketType<BlockPos> TICKET = TicketType.create("gridwright_chunk_ward", Comparator.comparingLong(BlockPos::asLong));
    /** How many wards one player may have loading chunks at once, server-wide. Operators change it with /gamerule. */
    public static final GameRules.Key<GameRules.IntegerValue> LIMIT = Platform.intGameRule("gridwrightChunkWardsPerPlayer",
            GameRules.Category.MISC, 4);

    private record Entry(ResourceKey<Level> dimension, BlockPos pos) { }

    private final Map<Entry, UUID> active = new LinkedHashMap<>();
    /** Not saved: whether this server has put the saved tickets back yet. */
    private boolean restored;

    public static ChunkWards get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ChunkWards::load, ChunkWards::new, NAME);
    }

    public static int limit(MinecraftServer server) {
        return server.getGameRules().getInt(LIMIT);
    }

    /** Called at the end of every server tick: restores tickets once, then checks the list now and then. */
    public static void tick(MinecraftServer server) {
        ChunkWards wards = get(server);
        if (!wards.restored) {
            wards.restored = true;
            wards.restore(server);
        }
        if (server.getTickCount() % VALIDATE_TICKS == 0) wards.validate(server);
    }

    public boolean isActive(ServerLevel level, BlockPos pos) {
        return active.containsKey(new Entry(level.dimension(), pos));
    }

    /** Wards {@code owner} has holding chunks, server-wide. */
    public int activeFor(UUID owner) {
        int count = 0;
        for (UUID holder : active.values()) if (holder.equals(owner)) count++;
        return count;
    }

    public void activate(ServerLevel level, BlockPos pos, UUID owner) {
        Entry entry = new Entry(level.dimension(), pos.immutable());
        if (active.put(entry, owner) == null) tickets(level, entry.pos(), true);
        setDirty();
    }

    public void deactivate(ServerLevel level, BlockPos pos) {
        if (active.remove(new Entry(level.dimension(), pos)) != null) {
            tickets(level, pos, false);
            setDirty();
        }
    }

    /** The 3x3 chunks a ward at {@code pos} loads. */
    public static List<ChunkPos> chunks(BlockPos pos) {
        ChunkPos centre = new ChunkPos(pos);
        List<ChunkPos> chunks = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
        }
        return chunks;
    }

    private static void tickets(ServerLevel level, BlockPos pos, boolean add) {
        for (ChunkPos chunk : chunks(pos)) {
            if (add) level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, pos);
            else level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_DISTANCE, pos);
        }
    }

    private void restore(MinecraftServer server) {
        List<Entry> gone = new ArrayList<>();
        for (Entry entry : active.keySet()) {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) gone.add(entry);
            else tickets(level, entry.pos(), true);
        }
        gone.forEach(active::remove);
        if (!gone.isEmpty()) setDirty();
    }

    /** Drops wards that were removed without telling us (world edits, a block swapped by another mod). */
    private void validate(MinecraftServer server) {
        List<Entry> gone = new ArrayList<>();
        for (Entry entry : active.keySet()) {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) {
                gone.add(entry);
            } else if (level.isLoaded(entry.pos()) && !(level.getBlockEntity(entry.pos()) instanceof ChunkWardBlockEntity)) {
                tickets(level, entry.pos(), false);
                gone.add(entry);
            }
        }
        gone.forEach(active::remove);
        if (!gone.isEmpty()) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        active.forEach((entry, owner) -> {
            CompoundTag saved = new CompoundTag();
            saved.putString("Dimension", entry.dimension().location().toString());
            saved.putLong("Pos", entry.pos().asLong());
            saved.putUUID("Owner", owner);
            list.add(saved);
        });
        tag.put("Wards", list);
        return tag;
    }

    public static ChunkWards load(CompoundTag tag) {
        ChunkWards wards = new ChunkWards();
        for (Tag element : tag.getList("Wards", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(saved.getString("Dimension"));
            if (dimension == null || !saved.hasUUID("Owner")) continue;
            wards.active.put(new Entry(ResourceKey.create(Registry.DIMENSION_REGISTRY, dimension), BlockPos.of(saved.getLong("Pos"))),
                    saved.getUUID("Owner"));
        }
        return wards;
    }
}
