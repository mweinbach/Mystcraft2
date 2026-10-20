package ports.buildcraft.lib;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;

/**
 * Upstream {@code AverageLong}: the average of the values pushed in each of the last {@code precise} ticks.
 * Upstream's {@code clear} emptied the window but kept the running sum, so a cleared average read its old
 * value until the window wrapped; here clearing also zeroes the sum.
 */
public final class AverageLong {
    private final long[] data;
    private final int precise;
    private int pos;
    private long averageRaw, tickValue;

    public AverageLong(int precise) {
        this.precise = precise;
        this.data = new long[precise];
    }

    public void clear() {
        Arrays.fill(data, 0);
        pos = 0;
        averageRaw = 0;
        tickValue = 0;
    }

    public double getAverage() {
        return (double) averageRaw / precise;
    }

    public void tick() {
        pos = ++pos % precise;
        long oldValue = data[pos];
        data[pos] = tickValue;
        if (pos == 0) {
            averageRaw = 0;
            for (long value : data) averageRaw += value;
        } else {
            averageRaw = averageRaw - oldValue + tickValue;
        }
        tickValue = 0;
    }

    public void push(long value) {
        tickValue += value;
    }

    public void save(CompoundTag tag, String key) {
        int[] ints = new int[precise * 2];
        for (int i = 0; i < precise; i++) {
            ints[i * 2] = (int) data[i];
            ints[i * 2 + 1] = (int) (data[i] >>> 32);
        }
        tag.putIntArray(key, ints);
    }

    public void load(CompoundTag tag, String key) {
        int[] ints = tag.getIntArray(key);
        if (ints.length < precise * 2) return;
        averageRaw = 0;
        pos = 0;
        tickValue = 0;
        for (int i = 0; i < precise; i++) {
            long value = ints[i * 2] & 0xFFFFFFFFL | (long) ints[i * 2 + 1] << 32;
            averageRaw += value;
            data[i] = value;
        }
    }
}
