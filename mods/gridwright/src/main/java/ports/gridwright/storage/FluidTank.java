package ports.gridwright.storage;

import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * One fluid and an amount, in the loader's fluid units (millibuckets on Forge, droplets on Fabric).
 * Only source fluids are stored. Mutators never notify; callers call {@link #changed()} once the change
 * is final (immediately on Forge, at transaction commit on Fabric).
 */
public final class FluidTank {

    private static final String FLUID = "Fluid";
    private static final String TAG = "Tag";
    private static final String AMOUNT = "Amount";
    private static final String UNITS = "Units";

    /** A copy of the contents for Fabric transactions. */
    public record Snapshot(Fluid fluid, @Nullable CompoundTag tag, long amount) { }

    private final long capacity;
    private final Runnable onChange;
    private Fluid fluid = Fluids.EMPTY;
    @Nullable private CompoundTag tag;
    private long amount;

    public FluidTank(int buckets, Runnable onChange) {
        this.capacity = buckets * Platform.bucketVolume();
        this.onChange = onChange;
    }

    public long capacity() {
        return capacity;
    }

    public long amount() {
        return amount;
    }

    public Fluid fluid() {
        return fluid;
    }

    @Nullable
    public CompoundTag tag() {
        return tag;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    /** Only still (source) fluids can be stored; flowing fluid is not a resource either loader moves. */
    public static boolean storable(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }

    public boolean holds(Fluid fluid, @Nullable CompoundTag tag) {
        return !isEmpty() && this.fluid == fluid && Objects.equals(this.tag, tag);
    }

    /** Adds up to {@code max} of {@code fluid}; returns how much was (or would be) accepted. */
    public long insert(Fluid fluid, @Nullable CompoundTag tag, long max, boolean simulate) {
        if (max <= 0 || !storable(fluid) || !isEmpty() && !holds(fluid, tag)) return 0;
        long accepted = Math.min(max, capacity - amount);
        if (!simulate && accepted > 0) {
            if (isEmpty()) {
                this.fluid = fluid;
                this.tag = tag == null || tag.isEmpty() ? null : tag.copy();
            }
            amount += accepted;
        }
        return accepted;
    }

    /** Removes up to {@code max}; returns how much was (or would be) removed. */
    public long extract(long max, boolean simulate) {
        long removed = Math.min(Math.max(0, max), amount);
        if (!simulate && removed > 0) {
            amount -= removed;
            if (amount == 0) clear();
        }
        return removed;
    }

    private void clear() {
        fluid = Fluids.EMPTY;
        tag = null;
        amount = 0;
    }

    public void changed() {
        onChange.run();
    }

    public Snapshot snapshot() {
        return new Snapshot(fluid, tag, amount);
    }

    public void restore(Snapshot snapshot) {
        fluid = snapshot.fluid();
        tag = snapshot.tag();
        amount = snapshot.amount();
    }

    /** Writes the contents, or nothing when empty (so empty drums stack). */
    public void save(CompoundTag out) {
        if (isEmpty()) return;
        out.putString(FLUID, Registry.FLUID.getKey(fluid).toString());
        if (tag != null) out.put(TAG, tag.copy());
        out.putLong(AMOUNT, amount);
        out.putLong(UNITS, Platform.bucketVolume());
    }

    /** Reads what {@link #save} wrote, converting amounts saved by the other loader's units. */
    public void load(CompoundTag in) {
        clear();
        ResourceLocation id = ResourceLocation.tryParse(in.getString(FLUID));
        Fluid saved = id == null ? Fluids.EMPTY : Registry.FLUID.get(id);
        long stored = in.getLong(AMOUNT);
        long units = in.contains(UNITS, Tag.TAG_LONG) ? in.getLong(UNITS) : Platform.bucketVolume();
        if (units > 0 && units != Platform.bucketVolume()) stored = stored * Platform.bucketVolume() / units;
        if (!storable(saved) || stored <= 0) return;
        fluid = saved;
        tag = in.contains(TAG, Tag.TAG_COMPOUND) ? in.getCompound(TAG).copy() : null;
        amount = Math.min(stored, capacity);
    }

    /** {@code amount} in millibuckets, for display on either loader. */
    public static long millibuckets(long amount) {
        return amount * 1000 / Platform.bucketVolume();
    }

    /** "Water: 12,000 / 16,000 mB", or "Empty: 0 / 16,000 mB". */
    public Component describe() {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.ROOT);
        Component name = isEmpty() ? Component.translatable("message.gridwright.drum.empty") : name(fluid);
        return Component.translatable("message.gridwright.drum.contents", name,
                format.format(millibuckets(amount)), format.format(millibuckets(capacity)));
    }

    /** A fluid's display name: its block's name, or its registry ID for fluids without a block. */
    public static Component name(Fluid fluid) {
        Block block = fluid.defaultFluidState().createLegacyBlock().getBlock();
        return block != Blocks.AIR ? block.getName() : Component.literal(Registry.FLUID.getKey(fluid).toString());
    }
}
