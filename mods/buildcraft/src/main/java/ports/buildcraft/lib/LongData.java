package ports.buildcraft.lib;

import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import net.minecraft.world.inventory.ContainerData;

/**
 * Menu data carrying whole longs. Vanilla sends each data slot as a 16-bit short, so every long takes four
 * slots; the server reads its sources and the client reassembles what it was sent.
 */
public final class LongData implements ContainerData {
    private static final int SLOTS_PER_LONG = 4;
    @Nullable private final LongSupplier[] sources;
    private final int[] values;

    private LongData(@Nullable LongSupplier[] sources, int count) {
        this.sources = sources;
        this.values = new int[count * SLOTS_PER_LONG];
    }

    public static LongData server(LongSupplier... sources) {
        return new LongData(sources, sources.length);
    }

    public static LongData client(int count) {
        return new LongData(null, count);
    }

    public long getLong(int index) {
        if (sources != null) return sources[index].getAsLong();
        long value = 0;
        for (int part = 0; part < SLOTS_PER_LONG; part++) {
            value |= (long) (values[index * SLOTS_PER_LONG + part] & 0xFFFF) << (16 * part);
        }
        return value;
    }

    @Override
    public int get(int slot) {
        if (sources == null) return values[slot];
        return (int) (sources[slot / SLOTS_PER_LONG].getAsLong() >>> (16 * (slot % SLOTS_PER_LONG)) & 0xFFFF);
    }

    @Override
    public void set(int slot, int value) {
        values[slot] = value & 0xFFFF;
    }

    @Override
    public int getCount() {
        return values.length;
    }
}
