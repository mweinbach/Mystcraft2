package ports.buildcraft.robotics.zone;

import java.util.BitSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Upstream {@code ZoneChunk}: the 16×16 columns of one chunk in a zone plan. A completely filled chunk is stored
 * as a flag rather than 256 bits. Upstream's {@code set(false)} from that flag flips {@code [0, 255)}, so column
 * (15, 15) is cleared as well as the one asked for.
 */
public final class ZoneChunk {
    private BitSet columns;
    private boolean full;

    public ZoneChunk() {}

    /** Copies the bits and, unlike upstream's constructor, the full-chunk flag, so a filled area survives a drag. */
    public ZoneChunk(ZoneChunk old) {
        full = old.full;
        if (old.columns != null) columns = BitSet.valueOf(old.columns.toLongArray());
    }

    public boolean get(int x, int z) {
        return full || columns != null && columns.get(x + z * 16);
    }

    public void set(int x, int z, boolean value) {
        if (value) {
            if (full) return;
            if (columns == null) columns = new BitSet(16 * 16);
            columns.set(x + z * 16, true);
            if (columns.cardinality() >= 16 * 16) {
                columns = null;
                full = true;
            }
            return;
        }
        if (full) {
            columns = new BitSet(16 * 16);
            columns.flip(0, 16 * 16 - 1);
            full = false;
        } else if (columns == null) {
            columns = new BitSet(16 * 16);
        }
        columns.set(x + z * 16, false);
    }

    public boolean isEmpty() {
        return !full && (columns == null || columns.isEmpty());
    }

    public void write(CompoundTag tag) {
        tag.putBoolean("fullSet", full);
        if (columns != null) tag.putByteArray("bits", columns.toByteArray());
    }

    public void read(CompoundTag tag) {
        full = tag.getBoolean("fullSet");
        columns = tag.contains("bits") ? BitSet.valueOf(tag.getByteArray("bits")) : null;
    }

    public void write(FriendlyByteBuf buffer) {
        int flags = (full ? 2 : 0) | (columns != null ? 1 : 0);
        buffer.writeByte(flags);
        if (columns != null) buffer.writeByteArray(columns.toByteArray());
    }

    public void read(FriendlyByteBuf buffer) {
        int flags = buffer.readUnsignedByte();
        columns = (flags & 1) != 0 ? BitSet.valueOf(buffer.readByteArray()) : null;
        full = (flags & 2) != 0;
    }

    /** A random filled column, with a random height, as upstream's {@code getRandomBlockPos}. */
    public BlockPos randomColumn(Random random) {
        int x;
        int z;
        if (full) {
            x = random.nextInt(16);
            z = random.nextInt(16);
        } else if (columns == null || columns.isEmpty()) {
            return null;
        } else {
            int bitId = random.nextInt(columns.cardinality());
            int bit = columns.nextSetBit(0);
            while (bitId > 0) {
                bitId--;
                bit = columns.nextSetBit(bit + 1);
            }
            z = bit / 16;
            x = bit - 16 * z;
        }
        return new BlockPos(x, random.nextInt(255), z);
    }
}
