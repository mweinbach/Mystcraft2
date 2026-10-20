package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code DeltaInt}: a value that changes linearly over scheduled spans of ticks, so a GUI can animate
 * progress without an update every tick. Spans are kept in level game time, which both sides share, instead
 * of upstream's per-delta tick counters and messages.
 */
public final class DeltaInt {
    private final List<Entry> entries = new ArrayList<>();
    private int staticValue;

    private record Entry(long startTime, long endTime, int delta) {}

    /** Upstream {@code addDelta}: {@code delta} is added over the ticks from {@code start} to {@code end}, counted from now. */
    public void addDelta(long now, long start, long end, int delta) {
        entries.add(new Entry(now + start, now + end, delta));
    }

    /** Upstream {@code setValue}: drops every scheduled span. */
    public void setValue(int value) {
        entries.clear();
        staticValue = value;
    }

    /** Folds finished spans into the static value. */
    public void tick(long now) {
        for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext(); ) {
            Entry entry = iterator.next();
            if (now >= entry.endTime) {
                staticValue += entry.delta;
                iterator.remove();
            }
        }
    }

    public double getDynamic(long now, float partialTicks) {
        double time = now + partialTicks;
        double value = staticValue;
        for (Entry entry : entries) {
            if (time < entry.startTime) continue;
            long duration = entry.endTime - entry.startTime;
            value += duration <= 0 || time >= entry.endTime ? entry.delta : entry.delta * (time - entry.startTime) / duration;
        }
        return value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("static", staticValue);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("start", entry.startTime);
            entryTag.putLong("end", entry.endTime);
            entryTag.putInt("delta", entry.delta);
            list.add(entryTag);
        }
        tag.put("changing", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        staticValue = tag.getInt("static");
        entries.clear();
        for (Tag element : tag.getList("changing", Tag.TAG_COMPOUND)) {
            CompoundTag entryTag = (CompoundTag) element;
            entries.add(new Entry(entryTag.getLong("start"), entryTag.getLong("end"), entryTag.getInt("delta")));
        }
    }
}
