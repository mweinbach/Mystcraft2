package ports.buildcraft.robotics.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream {@code ZonePlan}: a set of columns, stored per chunk. Coordinates are block coordinates, and a zone
 * planner stores them relative to itself. {@code distanceToSquared} keeps upstream's {@code x << 4 + 8}, where
 * {@code +} binds first, so the chunk centre is shifted by 12 bits.
 */
public final class ZonePlan {
    private final Map<ChunkPos, ZoneChunk> chunks = new HashMap<>();

    public ZonePlan() {}

    public ZonePlan(ZonePlan old) {
        for (Map.Entry<ChunkPos, ZoneChunk> entry : old.chunks.entrySet()) {
            chunks.put(entry.getKey(), new ZoneChunk(entry.getValue()));
        }
    }

    public boolean get(int x, int z) {
        ZoneChunk chunk = chunks.get(new ChunkPos(x >> 4, z >> 4));
        return chunk != null && chunk.get(x & 0xF, z & 0xF);
    }

    public void set(int x, int z, boolean value) {
        ChunkPos key = new ChunkPos(x >> 4, z >> 4);
        ZoneChunk chunk = chunks.get(key);
        if (chunk == null) {
            if (!value) return;
            chunk = new ZoneChunk();
            chunks.put(key, chunk);
        }
        chunk.set(x & 0xF, z & 0xF, value);
        if (chunk.isEmpty()) chunks.remove(key);
    }

    /**
     * Upstream's drag loop. The rectangle is half-open, so a press that has not moved to another column paints
     * nothing.
     */
    public void paintRectangle(int x0, int z0, int x1, int z1, boolean value) {
        for (int x = Math.min(x0, x1); x < Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z < Math.max(z0, z1); z++) set(x, z, value);
        }
    }

    public List<BlockPos> columns() {
        List<BlockPos> columns = new ArrayList<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (get(x, z)) columns.add(new BlockPos(x, 0, z));
            }
        }
        for (Map.Entry<ChunkPos, ZoneChunk> entry : chunks.entrySet()) {
            int originX = entry.getKey().getMinBlockX();
            int originZ = entry.getKey().getMinBlockZ();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (entry.getValue().get(x, z)) columns.add(new BlockPos(originX + x, 0, originZ + z));
                }
            }
        }
        return columns;
    }

    public ZonePlan offset(int dx, int dz) {
        ZonePlan moved = new ZonePlan();
        for (BlockPos column : columns()) moved.set(column.getX() + dx, column.getZ() + dz, true);
        return moved;
    }

    public Set<ChunkPos> chunks() {
        return chunks.keySet();
    }

    public boolean contains(Vec3 point) {
        return get((int) Math.floor(point.x), (int) Math.floor(point.z));
    }

    public double distanceToSquared(BlockPos pos) {
        double nearest = Double.MAX_VALUE;
        for (ChunkPos chunk : chunks.keySet()) {
            double dx = (chunk.x << 4 + 8) - pos.getX();
            double dz = (chunk.z << 4 + 8) - pos.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < nearest) nearest = distance;
        }
        return nearest;
    }

    public BlockPos randomBlockPos(Random random) {
        if (chunks.isEmpty()) return null;
        int index = random.nextInt(chunks.size());
        for (Map.Entry<ChunkPos, ZoneChunk> entry : chunks.entrySet()) {
            if (index == 0) {
                BlockPos column = entry.getValue().randomColumn(random);
                if (column == null) return null;
                return new BlockPos((entry.getKey().x << 4) + column.getX(), column.getY(), (entry.getKey().z << 4) + column.getZ());
            }
            index--;
        }
        return null;
    }

    public void write(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<ChunkPos, ZoneChunk> entry : chunks.entrySet()) {
            CompoundTag chunkTag = new CompoundTag();
            entry.getValue().write(chunkTag);
            chunkTag.putInt("chunkX", entry.getKey().x);
            chunkTag.putInt("chunkZ", entry.getKey().z);
            list.add(chunkTag);
        }
        tag.put("chunkMapping", list);
    }

    /** Replaces the chunks. Upstream appended, which duplicated a layer the second time a tile was read. */
    public void read(CompoundTag tag) {
        chunks.clear();
        Tag stored = tag.get("chunkMapping");
        if (!(stored instanceof ListTag list)) return;
        for (Tag element : list) {
            if (!(element instanceof CompoundTag chunkTag)) continue;
            ZoneChunk chunk = new ZoneChunk();
            chunk.read(chunkTag);
            chunks.put(new ChunkPos(chunkTag.getInt("chunkX"), chunkTag.getInt("chunkZ")), chunk);
        }
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(chunks.size());
        for (Map.Entry<ChunkPos, ZoneChunk> entry : chunks.entrySet()) {
            buffer.writeInt(entry.getKey().x);
            buffer.writeInt(entry.getKey().z);
            entry.getValue().write(buffer);
        }
    }

    public void read(FriendlyByteBuf buffer) {
        chunks.clear();
        int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            ChunkPos key = new ChunkPos(buffer.readInt(), buffer.readInt());
            ZoneChunk chunk = new ZoneChunk();
            chunk.read(buffer);
            chunks.put(key, chunk);
        }
    }
}
