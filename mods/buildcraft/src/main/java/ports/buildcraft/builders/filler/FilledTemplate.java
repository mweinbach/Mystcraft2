package ports.buildcraft.builders.filler;

import java.util.BitSet;
import net.minecraft.core.BlockPos;

/**
 * Upstream {@code Template} with its {@code FilledTemplate} view and {@code IFilledTemplate}'s helpers:
 * one bit per block of a box, set where the filler should have a block. Positions are relative to the
 * box's minimum corner, and ones outside the box throw, as upstream.
 */
public final class FilledTemplate {
    private final BlockPos size;
    private final BlockPos max;
    private final BitSet data;

    public FilledTemplate(BlockPos size) {
        this.size = size;
        this.max = size.offset(-1, -1, -1);
        this.data = new BitSet(dataSize());
    }

    /** A recorded template's blocks, which use the same index order. */
    public FilledTemplate(BlockPos size, BitSet blocks) {
        this(size);
        data.or(blocks);
    }

    public BlockPos getSize() {
        return size;
    }

    public BlockPos getMax() {
        return max;
    }

    public int dataSize() {
        return size.getX() * size.getY() * size.getZ();
    }

    public int posToIndex(int x, int y, int z) {
        return ((z * size.getY()) + y) * size.getX() + x;
    }

    public int posToIndex(BlockPos pos) {
        return posToIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos indexToPos(int i) {
        return new BlockPos(i % size.getX(), (i / size.getX()) % size.getY(), i / (size.getY() * size.getX()));
    }

    private void checkPos(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) {
            throw new IllegalArgumentException("Size: " + size + ", pos: " + new BlockPos(x, y, z));
        }
    }

    public boolean get(int x, int y, int z) {
        checkPos(x, y, z);
        return data.get(posToIndex(x, y, z));
    }

    public boolean get(int index) {
        return data.get(index);
    }

    public void set(int x, int y, int z, boolean value) {
        checkPos(x, y, z);
        data.set(posToIndex(x, y, z), value);
    }

    public void setLineX(int fromX, int toX, int y, int z, boolean value) {
        checkPos(fromX, y, z);
        checkPos(toX, y, z);
        for (int x = fromX; x <= toX; x++) data.set(posToIndex(x, y, z), value);
    }

    public void setLineY(int x, int fromY, int toY, int z, boolean value) {
        checkPos(x, fromY, z);
        checkPos(x, toY, z);
        for (int y = fromY; y <= toY; y++) set(x, y, z, value);
    }

    public void setLineZ(int x, int y, int fromZ, int toZ, boolean value) {
        checkPos(x, y, fromZ);
        checkPos(x, y, toZ);
        for (int z = fromZ; z <= toZ; z++) set(x, y, z, value);
    }

    public void setAreaYZ(int x, int fromY, int toY, int fromZ, int toZ, boolean value) {
        checkPos(x, fromY, fromZ);
        checkPos(x, toY, toZ);
        for (int z = fromZ; z <= toZ; z++) setLineY(x, fromY, toY, z, value);
    }

    public void setAreaXZ(int fromX, int toX, int y, int fromZ, int toZ, boolean value) {
        checkPos(fromX, y, fromZ);
        checkPos(toX, y, toZ);
        for (int z = fromZ; z <= toZ; z++) setLineX(fromX, toX, y, z, value);
    }

    public void setAreaXY(int fromX, int toX, int fromY, int toY, int z, boolean value) {
        checkPos(fromX, fromY, z);
        checkPos(toX, toY, z);
        for (int y = fromY; y <= toY; y++) setLineX(fromX, toX, y, z, value);
    }

    public void setPlaneYZ(int x, boolean value) {
        setAreaYZ(x, 0, max.getY(), 0, max.getZ(), value);
    }

    public void setPlaneXZ(int y, boolean value) {
        setAreaXZ(0, max.getX(), y, 0, max.getZ(), value);
    }

    public void setPlaneXY(int z, boolean value) {
        setAreaXY(0, max.getX(), 0, max.getY(), z, value);
    }

    public void setAll(boolean value) {
        data.set(0, dataSize(), value);
    }

    public void invert() {
        data.flip(0, dataSize());
    }

    public int count() {
        return data.cardinality();
    }
}
