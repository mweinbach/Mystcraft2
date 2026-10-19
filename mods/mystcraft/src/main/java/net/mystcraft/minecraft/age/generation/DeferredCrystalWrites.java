package net.mystcraft.minecraft.age.generation;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.mystcraft.minecraft.age.AgeServerLevel;

/** Source geometry is generated once. Worker threads only enqueue immutable writes; the server owns replay/storage. */
public final class DeferredCrystalWrites {
    private static final int MAX_PENDING = 131072, CHECKS_PER_TICK = 1024, WRITES_PER_TICK = 512;
    private static final String FILE = "mystcraft_deferred_crystals";
    private record Write(long position, BlockState state, long source, long sequence) {}
    private final PendingData data;
    private final ConcurrentLinkedQueue<Write> ingress = new ConcurrentLinkedQueue<>();
    private final AtomicInteger reserved;
    private final AtomicLong sequence;
    private Long cursor;

    /** Called during AgeServerLevel construction on the server thread, before chunk requests. */
    public DeferredCrystalWrites(ServerLevel level) {
        this(level, loadData(level));
    }
    private static PendingData loadData(ServerLevel level) {
        requireServer(level); return level.getDataStorage().computeIfAbsent(PendingData::load, PendingData::new, FILE);
    }
    private DeferredCrystalWrites(ServerLevel level, PendingData loaded) {
        requireServer(level);
        data = loaded;
        reserved = new AtomicInteger(data.pending.size());
        sequence = new AtomicLong(data.pending.values().stream().mapToLong(Write::sequence).max().orElse(0));
    }
    /** Pure boundary check avoids ensureCanWrite's far-chunk ERROR logging. */
    public static boolean canWriteImmediately(WorldGenLevel level, BlockPos position) {
        if (!(level instanceof WorldGenRegion region)) return true;
        int radius = region instanceof RegionWriteBounds bounds ? bounds.mystcraft$getWriteRadiusCutoff() : 1;
        return withinRadius(region.getCenter(), position, radius);
    }
    public static boolean withinRadius(ChunkPos center, BlockPos position, int radius) {
        return radius >= 0 && Math.abs((long)(position.getX() >> 4) - center.x) <= radius
                && Math.abs((long)(position.getZ() >> 4) - center.z) <= radius;
    }
    public static boolean place(WorldGenLevel level, BlockPos position, BlockState state, int flags) {
        if (level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
        if (level.getBlockState(position).is(Blocks.BEDROCK) || level.getBlockEntity(position) != null) return false;
        if (canWriteImmediately(level, position)) return level.ensureCanWrite(position) && level.setBlock(position, state, flags);
        if (!(level.getLevel() instanceof AgeServerLevel age))
            throw new IllegalStateException("Deferred classic crystal write outside an Age: " + position);
        long source = level instanceof WorldGenRegion region ? region.getCenter().toLong() : new ChunkPos(position).toLong();
        age.deferredCrystalWrites().enqueue(position, state, source);
        return true;
    }
    /** May run on a generation worker: no SavedData/world access or registry mutation here. */
    private void enqueue(BlockPos position, BlockState state, long source) {
        int count = reserved.incrementAndGet();
        if (count > MAX_PENDING) {
            reserved.decrementAndGet();
            org.slf4j.LoggerFactory.getLogger(DeferredCrystalWrites.class).error("Classic crystal deferred-write budget exhausted at {}", position);
            throw new IllegalStateException("Classic crystal deferred-write budget exceeded; refusing silent geometry loss");
        }
        ingress.add(new Write(position.asLong(), state, source, sequence.incrementAndGet()));
    }
    /** Serialization/reload adapter used by the controlled runtime fixture; does not register a second storage object. */
    public static DeferredCrystalWrites fromSnapshot(ServerLevel level, CompoundTag snapshot) {
        requireServer(level); return new DeferredCrystalWrites(level, PendingData.load(snapshot.copy()));
    }
    public int pendingCount() { return reserved.get(); }
    private void drain(ServerLevel level) {
        requireServer(level);
        Write write;
        while ((write = ingress.poll()) != null) {
            Write previous = data.pending.get(write.position());
            if (previous == null) data.pending.put(write.position(), write);
            else {
                // Stable cross-chunk precedence; retain source callback order within a source chunk.
                if (write.source() > previous.source() || write.source() == previous.source() && write.sequence() > previous.sequence())
                    data.pending.put(write.position(), write);
                reserved.decrementAndGet();
            }
            data.setDirty();
        }
    }
    /** Never requests a chunk. Only already-loaded FULL chunks returned by getChunkNow are eligible. */
    public void tick(ServerLevel level) {
        drain(level);
        int checked = 0, written = 0;
        while (!data.pending.isEmpty() && checked < CHECKS_PER_TICK && written < WRITES_PER_TICK) {
            Map.Entry<Long, Write> entry = cursor == null ? data.pending.firstEntry() : data.pending.higherEntry(cursor);
            if (entry == null) { cursor = null; break; }
            cursor = entry.getKey(); checked++;
            BlockPos position = BlockPos.of(entry.getKey());
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) continue;
            boolean preserve = level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)
                    || chunk.getBlockEntity(position) != null || chunk.getBlockState(position).is(Blocks.BEDROCK);
            if (!preserve && !chunk.getBlockState(position).equals(entry.getValue().state())) {
                // KNOWN_SHAPE suppresses recursive neighbor walks into chunks that are not loaded.
                if (!level.setBlock(position, entry.getValue().state(), 18)) {
                    org.slf4j.LoggerFactory.getLogger(DeferredCrystalWrites.class).error("Deferred crystal placement failed at {}", position);
                    throw new IllegalStateException("Deferred crystal block placement failed");
                }
                written++;
            }
            data.pending.remove(entry.getKey()); reserved.decrementAndGet(); data.setDirty();
        }
    }
    /** Called after chunk save has joined generation work, while still on the server thread. */
    public void flushForSave(ServerLevel level) {
        drain(level);
        if (((SavedData) data).isDirty()) level.getDataStorage().save();
    }
    public CompoundTag snapshot(ServerLevel level) { drain(level); return ((SavedData) data).save(new CompoundTag()); }
    private static void requireServer(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Deferred crystal storage/replay requires the server thread");
    }
    private static final class PendingData extends SavedData {
        private final TreeMap<Long, Write> pending = new TreeMap<>();
        @Override public CompoundTag save(CompoundTag tag) {
            tag.putInt("Version", 1); ListTag writes = new ListTag();
            for (Write write : pending.values()) {
                CompoundTag encoded = new CompoundTag(); encoded.putLong("Pos", write.position());
                encoded.put("State", NbtUtils.writeBlockState(write.state())); encoded.putLong("Source", write.source());
                encoded.putLong("Sequence", write.sequence()); writes.add(encoded);
            }
            tag.put("Writes", writes); return tag;
        }
        private static PendingData load(CompoundTag tag) {
            if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported deferred crystal version");
            ListTag writes = tag.getList("Writes", Tag.TAG_COMPOUND);
            if (writes.size() > MAX_PENDING) throw new IllegalArgumentException("Deferred crystal storage exceeds budget");
            PendingData data = new PendingData();
            for (int i = 0; i < writes.size(); i++) {
                CompoundTag entry = writes.getCompound(i);
                CompoundTag encodedState = entry.getCompound("State");
                if (!encodedState.contains("Name", Tag.TAG_STRING)) throw new IllegalArgumentException("Missing deferred crystal block identity");
                CompoundTag material = new CompoundTag(); material.put("BlockState", encodedState);
                BlockState state = ClassicMaterials.state(material);
                Write write = new Write(entry.getLong("Pos"), state, entry.getLong("Source"), entry.getLong("Sequence"));
                if (data.pending.putIfAbsent(write.position(), write) != null) throw new IllegalArgumentException("Duplicate deferred crystal position");
            }
            return data;
        }
    }
}
